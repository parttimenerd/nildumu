package nildumu;

import swp.util.Pair;

import java.util.*;
import java.util.stream.Collectors;

import static nildumu.Parser.*;
import static nildumu.util.Util.p;

/**
 * Does the conversion of a non SSA to a SSA AST,
 * introduces new phi-nodes and variables
 *
 * Version 2: comes in before (!) the name resolution and transforms the program in a program with
 * Phi-Nodes. This program should then be pretty-printed and processed again (without SSA resolution)
 */
public class SSAResolution2 implements NodeVisitor<SSAResolution2.VisRet> {

    /**
     * Result of visiting a statement
     */
    static class VisRet {

        static final VisRet DEFAULT = new VisRet(false, Collections.emptyList(), Collections.emptyList());

        final boolean removeCurrentStatement;

        final List<StatementNode> statementsToAdd;

        final List<StatementNode> statementsToPrepend;

        VisRet(boolean removeCurrentStatement, List<StatementNode> statementsToAdd, List<StatementNode> statementsToPrepend) {
            this.removeCurrentStatement = removeCurrentStatement;
            this.statementsToAdd = statementsToAdd;
            this.statementsToPrepend = statementsToPrepend;
        }

        VisRet(boolean removeCurrentStatement, StatementNode... statementsToAdd) {
            this(removeCurrentStatement, Arrays.asList(statementsToAdd), Collections.emptyList());
        }

        boolean isDefault(){
            return !removeCurrentStatement && statementsToAdd.isEmpty() && statementsToPrepend.isEmpty();
        }
    }

    /**
     * Newly introduced variables for an old one
     */
    private Stack<Map<String, String>> newVariables;

    /**
     * Defined variables
     */
    private Stack<Set<String>> definedVariables;

    /**
     * Maps newly introduced variables to their origins
     */
    private Map<String, String> reverseMapping;

    /**
     * Variable → Variable it overrides
     */
    private Map<String, String> directPredecessor;

    private Map<String, Integer> versionCount;

    /**
     * The current method if resolving in a
     */
    private final Optional<MethodNode> currentMethod;

    private final List<String> introducedVariables;

    private final Set<String> appendOnlyVariables;

    private final Set<String> appendValueVariables;

    public SSAResolution2(MethodNode method) {
        reverseMapping = new HashMap<>();
        versionCount = new HashMap<>();
        directPredecessor = new HashMap<>();
        newVariables = new Stack<>();
        definedVariables = new Stack<>();
        this.currentMethod = Optional.ofNullable(method);
        this.introducedVariables = new ArrayList<>();
        appendOnlyVariables = new HashSet<>();
        appendValueVariables = new HashSet<>();
    }

    public List<String> resolve(MJNode node){
        node.accept(this);
        return introducedVariables;
    }

    public List<String> resolveGlobalBlock(BlockNode node){
        visit(node, true);
        return introducedVariables;
    }

    void pushNewVariablesScope(){
        newVariables.push(new HashMap<>());
        definedVariables.push(new HashSet<>());
    }

    void popNewVariablesScope(){
        newVariables.pop();
        definedVariables.pop();
    }

    @Override
    public VisRet visit(MJNode node) {
        visitChildrenDiscardReturn(node);
        return VisRet.DEFAULT;
    }

    @Override
    public VisRet visit(ProgramNode program) {
        pushNewVariablesScope();
        visitChildrenDiscardReturn(program);
        popNewVariablesScope();
        return VisRet.DEFAULT;
    }

    @Override
    public VisRet visit(AppendOnlyVariableDeclarationNode appendDecl) {
        appendOnlyVariables.add(appendDecl.variable);
        appendValueVariables.add(appendDecl.variable);
        definedVariables.peek().add(appendDecl.variable);
        return new VisRet(true, appendDecl, new VariableAssignmentNode(appendDecl.location, create(appendDecl.variable, true), new IntegerLiteralNode(appendDecl.location, new Lattices.AppendOnlyValue().createEmpty())));
    }

    @Override
    public VisRet visit(VariableDeclarationNode declaration) {
        definedVariables.peek().add(declaration.variable);
        return visit((MJNode)declaration);
    }

    @Override
    public VisRet visit(VariableAssignmentNode assignment) {
        if (assignment.expression instanceof VariableAccessNode){
            VariableAccessNode variableAccess = (VariableAccessNode)assignment.expression;
            assignment.expression = new VariableAccessNode(assignment.expression.location, resolve(variableAccess.ident));
        } else {
            visitChildrenDiscardReturn(assignment.expression);
        }
        String newVariable = create(assignment.variable);
        return new VisRet(true,
                new VariableAssignmentNode(assignment.location, newVariable, assignment.expression));
    }

    @Override
    public VisRet visit(BlockNode block) {
        return visit(block, false);
    }

    public VisRet visit(BlockNode block, boolean assignGlobalVariables) {
        List<StatementNode> blockPartNodes = new ArrayList<>();
        for (StatementNode child : block.statementNodes){
            VisRet ret = child.accept(this);
            blockPartNodes.addAll(ret.statementsToPrepend);
            if (!ret.removeCurrentStatement) {
                blockPartNodes.add(child);
            }
            blockPartNodes.addAll(ret.statementsToAdd);
        }
        block.statementNodes.clear();
        block.addAll(blockPartNodes);
        if (assignGlobalVariables){
            this.assignAppendOnlyVariables(block);
        }
        Map<String, String> filtered = new HashMap<>();
        newVariables.peek().forEach((o, n) -> {
            if (definedVariables.peek().contains(o)){
                block.prependVariableDeclaration(n, appendValueVariables.contains(o));
                reverseMapping.remove(n);
                directPredecessor.remove(n);
                introducedVariables.remove(n);
            } else {
                filtered.put(o, n);
            }
        });
        newVariables.pop();
        newVariables.push(filtered);
        return VisRet.DEFAULT;
    }

    @Override
    public VisRet visit(IfStatementNode ifStatement) {
        return visit(ifStatement, ifStatement.ifBlock, ifStatement.elseBlock);
    }

    public VisRet visit(ConditionalStatementNode statement, BlockNode ifBlock, BlockNode elseBlock) {
        visitChildrenDiscardReturn(statement.conditionalExpression);

        pushNewVariablesScope();

        VisRet toAppend = ifBlock.accept(this);
        ifBlock.addAll(0, toAppend.statementsToPrepend);
        ifBlock.addAll(toAppend.statementsToAdd);
        Map<String, String> ifRedefines = newVariables.pop();

        pushNewVariablesScope();
        toAppend = elseBlock.accept(this);
        elseBlock.addAll(0, toAppend.statementsToPrepend);
        elseBlock.addAll(toAppend.statementsToAdd);
        Map<String, String> elseRedefines = newVariables.pop();

        Set<String> redefinedVariables = new HashSet<>();
        redefinedVariables.addAll(ifRedefines.keySet());
        redefinedVariables.addAll(elseRedefines.keySet());

        List<StatementNode> phiStatements = new ArrayList<>();
        for (String var : redefinedVariables){
            List<String> varsToJoin = new ArrayList<>();
            varsToJoin.add(ifRedefines.getOrDefault(var, resolve(var)));
            varsToJoin.add(elseRedefines.getOrDefault(var, resolve(var)));
            String created = create(var);
            PhiNode phi = new PhiNode(statement.location, varsToJoin);
            phi.controlDepStatement = statement;
            VariableAssignmentNode localVarDecl =
                    new VariableAssignmentNode(statement.location, created, phi);
            phiStatements.add(localVarDecl);
        }
        return new VisRet(false, phiStatements, Collections.emptyList());
    }

    @Override
    public VisRet visit(WhileStatementNode whileStatement) {

        VisRet ret = visit(whileStatement, whileStatement.body, new BlockNode(whileStatement.location, new ArrayList<>()));
        List<StatementNode> prepend = new ArrayList<>(ret.statementsToPrepend);
        List<StatementNode> stmtsToAdd = new ArrayList<>();
        for (StatementNode statementNode : ret.statementsToAdd) {
            PhiNode phi = (PhiNode)((VariableAssignmentNode)statementNode).expression;
            String whileEnd = phi.joinedVariables.get(0).ident;
            String beforeWhile = phi.joinedVariables.get(1).ident;
            String newWhilePhiPreVar = create(whileEnd);

            replaceVariable(beforeWhile, newWhilePhiPreVar, whileStatement.body);
            //prepend.add(new VariableDeclarationNode(whileStatement.location, newWhilePhiVar, phi));
            whileStatement.addPreCondVarAss(Arrays.asList((VariableAssignmentNode)new VariableAssignmentNode(whileStatement.location, newWhilePhiPreVar, phi)));
            //System.err.println(newWhilePhiPreVar + "  " + newWhilePhiVar);
            replaceVariable(beforeWhile, newWhilePhiPreVar, whileStatement.conditionalExpression);

          //  stmtsToAdd.add(new VariableAssignmentNode(statementNode.location, newWhilePhiPreVar, phi));
        }
        return new VisRet(ret.removeCurrentStatement,
                stmtsToAdd,
                prepend);
    }

    public static void process(SSAResolution2 parent, MethodNode method) {
        SSAResolution2 resolution = new SSAResolution2(method);
        resolution.pushNewVariablesScope();
        Map<String, String> pre = parent.appendOnlyVariables.stream().collect(Collectors.toMap(v -> v, v -> {
            resolution.appendValueVariables.add(v);
            resolution.appendOnlyVariables.add(v);
            return resolution.create(v, true);
        }));
        resolution.resolve(method.parameters);
        resolution.resolve(method.body).stream().filter(v -> !pre.values().contains(v))
                .forEach(v -> method.body.prependVariableDeclaration(v, resolution.appendValueVariables.contains(resolution.resolveOrigin(v))));
        Map<String, String> post = parent.appendOnlyVariables.stream().collect(Collectors.toMap(v -> v, v -> {
            return resolution.resolve(v);
        }));
        parent.appendOnlyVariables.forEach(v -> method.globals.globalVarSSAVars.put(v, p(pre.get(v), post.get(v))));
    }

    public static void process(ProgramNode program){
        SSAResolution2 resolution = new SSAResolution2(null);
        resolution.pushNewVariablesScope();
        resolution.resolveGlobalBlock(program.globalBlock)
                .forEach(v -> program.globalBlock.prependVariableDeclaration(v, resolution.appendValueVariables.contains(v)));
        program.methods().forEach(m -> SSAResolution2.process(resolution, m));
    }

    void assignAppendOnlyVariables(BlockNode block){
        this.appendOnlyVariables.forEach(v -> {
            block.add(new VariableAssignmentNode(block.location, v, new VariableAccessNode(block.location, resolve(v))));
        });
    }

    @Override
    public VisRet visit(ParameterNode parameter) {
        return VisRet.DEFAULT;
    }

    /**
     * Replaces all controlDeps that are exactly the passed variable
     */
    private void replaceVariableInPhiConds(Variable var, ExpressionNode expr, MJNode node){
        node.accept(new NodeVisitor<Object>() {

            @Override
            public Object visit(MJNode node) {
                visitChildrenDiscardReturn(node);
                return null;
            }

            @Override
            public Object visit(PhiNode phi) {
                phi.alterCondDeps(e -> e instanceof VariableAccessNode && ((VariableAccessNode) e).definition == var ? expr : e);
                return null;
            }
        });
    }

    @Override
    public VisRet visit(VariableAccessNode variableAccess) {
        variableAccess.ident = resolve(variableAccess.ident);
        variableAccess.definition = null;
        return VisRet.DEFAULT;
    }

    @Override
    public VisRet visit(MethodInvocationNode methodInvocation) {
        Map<String, String> appToBeforeSSA = appendOnlyVariables.stream().collect(Collectors.toMap(s -> s, this::resolve));
        visitChildrenDiscardReturn(methodInvocation);
        Map<String, String> afterSSA = appendOnlyVariables.stream()
                .map(v -> new Pair<>(v, create(v))).collect(Collectors.toMap(p -> p.first, p -> p.second));
        Map<String, Pair<String, String>> combined = appendOnlyVariables.stream()
                .collect(Collectors.toMap(v -> v, v -> new Pair<>(appToBeforeSSA.get(v), afterSSA.get(v))));
        methodInvocation.globals.globalVarSSAVars.putAll(combined);
        return VisRet.DEFAULT;
    }

    /**
     * Resolve the current version of the variable
     */
    private String resolve(String variable){
        for (int i = newVariables.size() - 1; i >= 0; i--){
            if (newVariables.get(i).containsKey(variable)){
                return newVariables.get(i).get(variable);
            }
        }
        return variable;
    }

    private String resolveOrigin(String variable){
        return reverseMapping.getOrDefault(variable, variable);
    }

    private int numberOfVersions(String variable){
        return versionCount.getOrDefault(variable, 0);
    }

    private String create(String variable){
        return create(variable, appendOnlyVariables.contains(resolveOrigin(variable)) || appendValueVariables.contains(resolveOrigin(variable)));
    }

    /**
     * Create a new variable and add a declaration to beginning
     */
    private String create(String variable, boolean hasAppendValue){
        String origin = resolveOrigin(variable);
        String newVariable = origin + (numberOfVersions(origin) + 1);
        String pred = resolve(variable);
        directPredecessor.put(newVariable, pred);
        versionCount.put(origin, numberOfVersions(origin) + 1);
        reverseMapping.put(newVariable, origin);
        newVariables.get(newVariables.size() - 1).put(origin, newVariable);
        introducedVariables.add(newVariable);
        if (hasAppendValue) {
            appendValueVariables.add(newVariable);
        }
        return newVariable;
    }

    static void replaceVariable(String search, String replacement, MJNode node){
        node.accept(new NodeVisitor<Object>() {
            @Override
            public Object visit(MJNode node) {
                visitChildrenDiscardReturn(node);
                return null;
            }

            @Override
            public Object visit(VariableAccessNode variableAccess) {
                if (variableAccess.ident.equals(search)){
                    variableAccess.ident = replacement;
                }
                return null;
            }

            @Override
            public Object visit(PhiNode phi) {
                List<VariableAccessNode> vars = phi.joinedVariables.stream()
                        .map(v -> v.ident.equals(search) ? new VariableAccessNode(v.location, new Variable(replacement)) : v).collect(Collectors.toList());
                phi.joinedVariables.clear();
                phi.joinedVariables.addAll(vars);
                return null;
            }
        });
    }
}
