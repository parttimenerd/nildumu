package nildumu;

import nildumu.typing.Type;
import nildumu.typing.Types;
import swp.lexer.Location;

import java.util.*;
import java.util.stream.Collectors;

import static nildumu.Parser.*;
import static nildumu.util.Util.asArrayList;
import static nildumu.util.Util.concatAsArrayList;

/**
 * Idea transform from non-SSA with loops to non-SSA without loops.
 * <p>
 * Assumption: it gets passed the raw program without any transforms (or NameResolution for that matter)
 * <p>
 * break and continue statements are also transformed
 */
public class LoopTransformer implements StatementVisitor<List<StatementNode>> {

    /**
     * Collect the read and written variables of a node
     */
    private static class VariableAccessVisitor implements NodeVisitor<Object> {
        private final Set<Variable> written = new HashSet<>();
        private final Set<Variable> accessed = new HashSet<>();

        public List<Variable> getWrittenVariables() {
            return Collections.unmodifiableList(new ArrayList<>(written));
        }

        public List<Variable> getAccessedVariables() {
            return Collections.unmodifiableList(new ArrayList<>(accessed));
        }

        @Override
        public Object visit(MJNode node) {
            visitChildrenDiscardReturn(node);
            return null;
        }

        @Override
        public Object visit(VariableAssignmentNode assignment) {
            written.add(assignment.definition);
            accessed.add(assignment.definition);
            visitChildrenDiscardReturn(assignment);
            return null;
        }

        @Override
        public Object visit(MultipleVariableAssignmentNode assignment) {
            written.addAll(assignment.definitions);
            visitChildrenDiscardReturn(assignment);
            return null;
        }

        @Override
        public Object visit(VariableAccessNode variableAccess) {
            return accessed.add(variableAccess.definition);
        }
    }

    private final Types types;
    private final Map<MJNode, SymbolTable.Scope> scopePerNode;
    private final List<MethodNode> newMethods;

    private LoopTransformer(ProgramNode programNode) {
        NameResolution nameResolution = new NameResolution(programNode, true);
        nameResolution.resolve();
        scopePerNode = nameResolution.getScopePerNode();
        newMethods = new ArrayList<>();
        types = programNode.types;
    }

    /**
     * don't pass the output to {@link SSAResolution2} directly, round trip through a string first
     */
    public static void process(ProgramNode program) {
        LoopTransformer resolution = new LoopTransformer(program);
        resolution.visit(program.globalBlock);
        program.methods().forEach(m -> resolution.visit(m.body));
        resolution.newMethods.forEach(program::addMethod);
    }

    @Override
    public List<StatementNode> visit(StatementNode statement) {
        visitChildrenDiscardReturn(statement);
        return Collections.singletonList(statement);
    }

    @Override
    public List<StatementNode> visit(BlockNode block) {
        List<StatementNode> inner = block.statementNodes.stream().flatMap(s -> s.accept(this).stream())
                .collect(Collectors.toList());
        block.statementNodes.clear();
        block.statementNodes.addAll(inner);
        return Collections.emptyList();
    }

    private static class WhileContext {
        private final ReturnStatementNode returnStatement;
        private final StatementNode functionCall;

        private WhileContext(ReturnStatementNode returnStatement, StatementNode functionCall) {
            this.returnStatement = returnStatement;
            this.functionCall = functionCall;
        }
    }

    private Stack<WhileContext> whileContexts = new Stack<>();

    @Override
    public List<StatementNode> visit(WhileStatementNode whileNode) {

        VariableAccessVisitor visitor = new VariableAccessVisitor();
        visitor.visit(whileNode);
        SymbolTable.Scope scope = scopePerNode.get(whileNode);
        List<Variable> accessedVariables = scope.filterVariables(visitor.getAccessedVariables());
        List<Variable> writtenVariables = scope.filterVariables(visitor.getWrittenVariables());

        Location location = whileNode.location;
        String methodName = "loop_method" + location.line + "_" + location.column;

        ParametersNode parametersNode = new ParametersNode(location,
                accessedVariables.stream().map(v -> new ParameterNode(location, v.type, v.name)).collect(Collectors.toList()));
        List<ExpressionNode> arguments = accessedVariables.stream().map(v -> new VariableAccessNode(location, v)).collect(Collectors.toList());
        ArgumentsNode argNode = new ArgumentsNode(location, arguments);

        MethodInvocationNode invocation = new MethodInvocationNode(location, methodName, argNode);
        StatementNode invocationAssignment;
        if (writtenVariables.size() > 0) {
            invocationAssignment = new MultipleVariableAssignmentNode(location, writtenVariables, new UnpackOperatorNode(invocation));
        } else {
            invocationAssignment = new ExpressionStatementNode(invocation);
        }
        ReturnStatementNode returnStatement = new ReturnStatementNode(location, writtenVariables.stream().map(v -> new VariableAccessNode(location, v)).collect(Collectors.toList()));
        whileContexts.push(new WhileContext(returnStatement, invocationAssignment));

        visit(whileNode.body);
        whileContexts.pop();

        // if (condition) { body; written_vars = f(accessed_vars)} return written_vars
        BlockNode body = new BlockNode(location, asArrayList(
                new IfStatementNode(location, whileNode.conditionalExpression, new BlockNode(location, concatAsArrayList(whileNode.body.statementNodes,
                        Collections.singletonList(invocationAssignment))
                ), new BlockNode(location, Collections.emptyList())),
                returnStatement)
        );
        Type returnType = types.INT;
        if (writtenVariables.size() > 0) {
            returnType = new Type.TupleType(types, writtenVariables.stream().map(Variable::getType).collect(Collectors.toList()));
        }
        newMethods.add(new Parser.MethodNode(location, methodName, returnType, parametersNode, body,
                new Parser.GlobalVariablesNode(location, Collections.emptyMap())));
        visitChildrenDiscardReturn(whileNode);
        return Collections.singletonList(invocationAssignment);
    }

    @Override
    public List<StatementNode> visit(LoopInterruptionNode loopInterruptionNode) {
        WhileContext context = whileContexts.peek();
        switch (loopInterruptionNode.interruption) {
            case BREAK:
                return Collections.singletonList(context.returnStatement);
            case CONTINUE:
                return Arrays.asList(context.functionCall, context.returnStatement);
        }
        return Collections.emptyList();
    }
}
