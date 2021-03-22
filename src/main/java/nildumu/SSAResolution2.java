package nildumu;

import nildumu.typing.Types;
import swp.util.Pair;

import java.util.*;
import java.util.stream.Collectors;

import static nildumu.Parser.*;
import static nildumu.util.Util.p;

/**
 * Does the conversion of a non SSA to a SSA AST,
 * introduces new phi-nodes and variables
 * <p>
 * Version 2: comes in before (!) the name resolution and transforms the program in a program with,
 * but comes after the TypeTransformation (and therefore only deals with ints)
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
    }

    private static class Scope {
        /**
         * Newly introduced variables for an old one
         */
        final Map<String, String> newVariables = new HashMap<>();

        /**
         * Defined variables
         */
        final Set<String> definedVariables = new HashSet<>();
        /** contains variables from the defined variables field, that are defined without a value */
        final Set<String> definedVariablesWOAssigment = new HashSet<>();
        final Map<String, String> newVariablesLocated = new HashMap<>();

        Map<String, String> getNewWithoutDefined(){
            return newVariables.entrySet().stream()
                    .filter(e -> !definedVariables.contains(e.getKey()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        }
    }

    /**
     * Newly introduced variables for an old one
     */
    private final Stack<Scope> scopes;

    private final Stack<Scope> conditionalScopes;

    /**
     * Maps newly introduced variables to their origins
     */
    private Map<String, String> reverseMapping;

    private Map<String, Integer> versionCount;

    private final List<String> introducedVariables;

    private final Set<String> appendOnlyVariables;

    private final Set<String> appendValueVariables;

    private final Types types;

    private final VariableSet collectedVariableNames;

    private static class VariableSet extends AbstractSet<String> {

        private final Set<String> variables;

        private VariableSet(Set<String> variables) {
            this.variables = variables;
        }

        @Override
        public Iterator<String> iterator() {
            return variables.iterator();
        }

        @Override
        public int size() {
            return variables.size();
        }

        @Override
        public boolean contains(Object o) {
            return variables.contains(o);
        }

        /** Create a version of the variable name that is not yet present in this set and add it */
        public String create(String variable) {
            while (variables.contains(variable)) {
                variable = variable + "_" + System.nanoTime() % 10000;
            }
            variables.add(variable);
            return variable;
        }
    }

    public static class VariableDefinitionVisitor implements NodeVisitor<Object> {
        final Set<String> variables = new HashSet<>();

        @Override
        public Object visit(MJNode node) {
            visitChildrenDiscardReturn(node);
            return null;
        }

        @Override
        public Object visit(VariableDeclarationNode assignment) {
            variables.add(assignment.variable);
            return null;
        }

        @Override
        public Object visit(StatementNode statementNode) {
            return null;
        }

        @Override
        public Object visit(BlockNode block) {
            visitChildrenDiscardReturn(block);
            return null;
        }

        @Override
        public Object visit(ParameterNode parameterNode) {
            variables.add(parameterNode.name);
            return null;
        }

        public static VariableSet process(MJNode node) {
            VariableDefinitionVisitor visitor = new VariableDefinitionVisitor();
            node.accept(visitor);
            return new VariableSet(visitor.variables);
        }
    }

    public SSAResolution2(Types types, MethodNode method, VariableSet collectedVariableNames) {
        this.collectedVariableNames = collectedVariableNames;
        reverseMapping = new HashMap<>();
        versionCount = new HashMap<>();
        scopes = new Stack<>();
        this.introducedVariables = new ArrayList<>();
        appendOnlyVariables = new HashSet<>();
        appendValueVariables = new HashSet<>();
        conditionalScopes = new Stack<>();
        conditionalScopes.push(new Scope());
        scopes.push(new Scope());
        if (method != null) {
            scopes.peek().definedVariables.addAll(method.parameters.parameterNodes.stream().map(p -> p.name).collect(Collectors.toList()));
        }
        this.types = types;
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
        scopes.push(new Scope());
    }

    void popNewVariablesScope(){
        scopes.pop();
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

    private void defineVariable(String variable){
        defineVariable(variable, true);
    }

    private void defineVariable(String variable, boolean hasAssignment) {
        scopes.peek().definedVariables.add(variable);
        conditionalScopes.peek().definedVariables.add(variable);
        if (!hasAssignment) {
            scopes.peek().definedVariablesWOAssigment.add(variable);
            conditionalScopes.peek().definedVariablesWOAssigment.add(variable);
        }
    }

    @Override
    public VisRet visit(AppendOnlyVariableDeclarationNode appendDecl) {
        appendOnlyVariables.add(appendDecl.variable);
        appendValueVariables.add(appendDecl.variable);
        defineVariable(appendDecl.variable);
        return new VisRet(true, appendDecl, new VariableAssignmentNode(appendDecl.location, create(appendDecl.variable, true), new IntegerLiteralNode(appendDecl.location, new Lattices.AppendOnlyValue().createEmpty())));
    }

    @Override
    public VisRet visit(VariableDeclarationNode declaration) {
        defineVariable(declaration.variable, declaration.hasInitExpression());
        return visit((MJNode)declaration);
    }

    @Override
    public VisRet visit(VariableAssignmentNode assignment) {
        if (assignment.expression instanceof VariableAccessNode){
            VariableAccessNode variableAccess = (VariableAccessNode) assignment.expression;
            assignment.expression = new VariableAccessNode(assignment.expression.location, resolve(variableAccess.ident));
        } else {
            assignment.expression.accept(this);
        }
        String newVariable = create(assignment.variable);
        return new VisRet(true,
                new VariableAssignmentNode(assignment.location, newVariable, assignment.expression));
    }

    @Override
    public VisRet visit(MultipleVariableAssignmentNode assignment) {
        assignment.expression.accept(this);
        return new VisRet(true,
                new MultipleVariableAssignmentNode(assignment.location,
                        assignment.variables.stream().map(this::create).toArray(String[]::new),
                        assignment.expression));
    }

    @Override
    public VisRet visit(ReturnStatementNode returnStatement) {
        if (returnStatement.hasReturnExpression()) {
            returnStatement.expression.accept(this);
        }
        return VisRet.DEFAULT;
    }

    @Override
    public VisRet visit(TupleLiteralNode tupleLiteral) {
        tupleLiteral.elements.forEach(e -> e.accept(this));
        return VisRet.DEFAULT;
    }

    @Override
    public VisRet visit(BlockNode block) {
        return visit(block, false);
    }

    public VisRet visit(BlockNode block, boolean assignGlobalVariables) {
        List<StatementNode> blockPartNodes = new ArrayList<>();
        for (StatementNode child : block.statementNodes) {
            if (child instanceof BlockNode) {
                pushNewVariablesScope();
            }
            VisRet ret = child.accept(this);
            if (child instanceof BlockNode){
                Scope scope = scopes.pop();
                scope.newVariables.forEach((o, n) -> {
                    if (appendOnlyVariables.contains(o)){
                        scopes.peek().newVariables.put(o, n);
                    }
                });
            }
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
        scopes.peek().newVariables.forEach((o, n) -> {
            if (scopes.peek().definedVariables.contains(o)){
                block.prependVariableDeclaration(n, types.INT, appendValueVariables.contains(o));
                reverseMapping.remove(n);
                introducedVariables.remove(n);
            } else {
                filtered.put(o, n);
            }
        });
        scopes.peek().newVariables.clear();
        scopes.peek().newVariables.putAll(filtered);
        return VisRet.DEFAULT;
    }

    @Override
    public VisRet visit(IfStatementNode ifStatement) {
        return visit(ifStatement, ifStatement.ifBlock, ifStatement.elseBlock);
    }

    public VisRet visit(ConditionalStatementNode statement, BlockNode ifBlock, BlockNode elseBlock) {
        visitChildrenDiscardReturn(statement.conditionalExpression);

        pushNewVariablesScope();
        conditionalScopes.push(new Scope());
        VisRet toAppend = ifBlock.accept(this);
        ifBlock.addAll(0, toAppend.statementsToPrepend);
        ifBlock.addAll(toAppend.statementsToAdd);
        Scope condIfScope = conditionalScopes.pop();
        Map<String, String> ifRedefines = condIfScope.getNewWithoutDefined();
        popNewVariablesScope();

        pushNewVariablesScope();
        conditionalScopes.push(new Scope());
        toAppend = elseBlock.accept(this);
        elseBlock.addAll(0, toAppend.statementsToPrepend);
        elseBlock.addAll(toAppend.statementsToAdd);
        Scope condElseScope = conditionalScopes.pop();
        Map<String, String> elseRedefines = condElseScope.getNewWithoutDefined();
        popNewVariablesScope();

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
            whileStatement.addPreCondVarAss(Collections.singletonList((VariableAssignmentNode) new VariableAssignmentNode(whileStatement.location, newWhilePhiPreVar, phi)));
            replaceVariable(beforeWhile, newWhilePhiPreVar, whileStatement.conditionalExpression);

          //  stmtsToAdd.add(new VariableAssignmentNode(statementNode.location, newWhilePhiPreVar, phi));
        }
        return new VisRet(ret.removeCurrentStatement,
                stmtsToAdd,
                prepend);
    }

    public static void process(SSAResolution2 parent, MethodNode method) {
        SSAResolution2 resolution = new SSAResolution2(parent.types, method, VariableDefinitionVisitor.process(method));
        resolution.pushNewVariablesScope();
        Map<String, String> pre = parent.appendOnlyVariables.stream().collect(Collectors.toMap(v -> v, v -> {
            resolution.appendValueVariables.add(v);
            resolution.appendOnlyVariables.add(v);
            return resolution.create(v, true);
        }));
        resolution.resolve(method.parameters);
        List<String> createdVars = resolution.resolve(method.body);
        createdVars.stream().filter(v -> !pre.values().contains(v))
                .forEach(v -> method.body.prependVariableDeclaration(v, parent.types.INT, resolution.appendValueVariables.contains(resolution.resolveOrigin(v))));
        Map<String, String> post = parent.appendOnlyVariables.stream().collect(Collectors.toMap(v -> v, resolution::resolve));
        parent.appendOnlyVariables.forEach(v -> method.globals.globalVarSSAVars.put(v, p(pre.get(v), post.get(v))));
    }

    public static void process(ProgramNode program){
        SSAResolution2 resolution = new SSAResolution2(program.types, null, VariableDefinitionVisitor.process(program.globalBlock));
        resolution.pushNewVariablesScope();
        resolution.resolveGlobalBlock(program.globalBlock)
                .forEach(v -> program.globalBlock.prependVariableDeclaration(v, program.types.INT, resolution.appendValueVariables.contains(v)));
        program.methods().forEach(m -> SSAResolution2.process(resolution, m));
    }

    void assignAppendOnlyVariables(BlockNode block){
        this.appendOnlyVariables.forEach(v -> block.add(new VariableAssignmentNode(block.location, v, new VariableAccessNode(block.location, resolveLocated(v)))));
    }

    @Override
    public VisRet visit(ParameterNode parameter) {
        return VisRet.DEFAULT;
    }

    @Override
    public VisRet visit(VariableAccessNode variableAccess) {
        variableAccess.ident = resolveLocated(variableAccess.ident);
        variableAccess.definition = null;
        return VisRet.DEFAULT;
    }

    @Override
    public VisRet visit(MethodInvocationNode methodInvocation) {
        visitChildrenDiscardReturn(methodInvocation);
        Map<String, String> appToBeforeSSA = appendOnlyVariables.stream().collect(Collectors.toMap(s -> s, this::resolve));
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
        for (int i = scopes.size() - 1; i >= 0; i--){
            if (scopes.get(i).newVariables.containsKey(variable)){
                return scopes.get(i).newVariables.get(variable);
            }
        }
        return variable;
    }

    /**
     * Resolve the current version of the variable
     */
    private String resolveLocated(String variable){
        for (int i = scopes.size() - 1; i >= 0; i--){
            if (scopes.get(i).newVariablesLocated.containsKey(variable)){
                return scopes.get(i).newVariablesLocated.get(variable);
            }
        }
        return variable;
    }

    /**
     * Resolve the current version of the variable
     */
    private Scope definingScope(String variable){
        for (int i = scopes.size() - 1; i >= 0; i--){
            if (scopes.get(i).definedVariables.contains(variable)){
                return scopes.get(i);
            }
        }
        return scopes.peek();
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
        if (scopes.peek().definedVariablesWOAssigment.contains(origin)) {
            scopes.peek().definedVariablesWOAssigment.remove(origin);
            scopes.peek().newVariablesLocated.put(origin, origin);
            return variable;
        }
        String newVariable = this.collectedVariableNames.create(origin + (numberOfVersions(origin) + 1));
        String pred = resolveLocated(variable);
        versionCount.put(origin, numberOfVersions(origin) + 1);
        reverseMapping.put(newVariable, origin);
        definingScope(origin).newVariablesLocated.put(origin, newVariable);
        scopes.peek().newVariables.put(origin, newVariable);
        conditionalScopes.peek().newVariables.put(origin, newVariable);
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

            @Override
            public Object visit(MethodInvocationNode methodInvocation) {
                methodInvocation.globals.globalVarSSAVars.forEach((v, p) -> {
                    if (p.first.equals(search)){
                        methodInvocation.globals.globalVarSSAVars.put(v, p(replacement, p.second));
                    }
                });
                visitChildrenDiscardReturn(methodInvocation);
                return null;
            }
        });
    }
}
