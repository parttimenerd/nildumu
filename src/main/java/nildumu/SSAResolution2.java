package nildumu;

import swp.parser.lr.BaseAST;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static nildumu.Lattices.vl;
import static nildumu.Parser.*;

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

    public SSAResolution2(MethodNode method) {
        reverseMapping = new HashMap<>();
        versionCount = new HashMap<>();
        directPredecessor = new HashMap<>();
        newVariables = new Stack<>();
        this.currentMethod = Optional.ofNullable(method);
        this.introducedVariables = new ArrayList<>();
    }

    public List<String> resolve(MJNode node){
        node.accept(this);
        return introducedVariables;
    }

    void pushNewVariablesScope(){
        newVariables.push(new HashMap<>());
    }

    void popNewVariablesScope(){
        newVariables.pop();
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
    public VisRet visit(VariableDeclarationNode declaration) {
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

    public static void process(MethodNode method) {
        SSAResolution2 resolution = new SSAResolution2(method);
        resolution.pushNewVariablesScope();
        resolution.resolve(method.parameters);
        resolution.resolve(method.body).forEach(method.body::prependVariableDeclaration);
    }

    public static void process(ProgramNode program){
        SSAResolution2 resolution = new SSAResolution2(null);
        resolution.pushNewVariablesScope();
        resolution.resolve(program.globalBlock)
                .forEach(program.globalBlock::prependVariableDeclaration);
        program.methods().forEach(SSAResolution2::process);
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
        visitChildrenDiscardReturn(methodInvocation);
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

    /**
     * Create a new variable and add a declaration to beginning
     */
    private String create(String variable){
        String origin = resolveOrigin(variable);
        String newVariable = origin + (numberOfVersions(origin) + 1);
        String pred = resolve(variable);
        directPredecessor.put(newVariable, pred);
        versionCount.put(origin, numberOfVersions(origin) + 1);
        reverseMapping.put(newVariable, origin);
        newVariables.get(newVariables.size() - 1).put(origin, newVariable);
        introducedVariables.add(newVariable);
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
