package nildumu;

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
 */
public class LoopTransformer implements StatementVisitor<Optional<StatementNode>> {

    /**
     * Collect the read and written variables of a node
     */
    private static class VariableAccessVisitor implements NodeVisitor<Object> {
        private final Set<String> written = new HashSet<>();
        private final Set<String> accessed = new HashSet<>();

        public List<String> getWrittenVariables() {
            return Collections.unmodifiableList(new ArrayList<>(written));
        }

        public List<String> getAccessedVariables() {
            return Collections.unmodifiableList(new ArrayList<>(accessed));
        }

        @Override
        public Object visit(MJNode node) {
            visitChildrenDiscardReturn(node);
            return null;
        }

        @Override
        public Object visit(VariableAssignmentNode assignment) {
            written.add(assignment.variable);
            accessed.add(assignment.variable);
            visitChildrenDiscardReturn(assignment);
            return null;
        }

        @Override
        public Object visit(MultipleVariableAssignmentNode assignment) {
            written.addAll(assignment.variables);
            visitChildrenDiscardReturn(assignment);
            return null;
        }

        @Override
        public Object visit(VariableAccessNode variableAccess) {
            return accessed.add(variableAccess.ident);
        }
    }

    private final Map<MJNode, SymbolTable.Scope> scopePerNode;
    private final List<MethodNode> newMethods;

    private LoopTransformer(ProgramNode programNode) {
        NameResolution nameResolution = new NameResolution(programNode, true);
        nameResolution.resolve();
        scopePerNode = nameResolution.getScopePerNode();
        newMethods = new ArrayList<>();
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
    public Optional<StatementNode> visit(StatementNode statement) {
        visitChildrenDiscardReturn(statement);
        return Optional.empty();
    }

    @Override
    public Optional<StatementNode> visit(BlockNode block) {
        List<StatementNode> inner = block.statementNodes.stream().map(s -> s.accept(this).orElse(s)).collect(Collectors.toList());
        block.statementNodes.clear();
        block.statementNodes.addAll(inner);
        return Optional.empty();
    }

    @Override
    public Optional<StatementNode> visit(WhileStatementNode whileNode) {
        VariableAccessVisitor visitor = new VariableAccessVisitor();
        visitor.visit(whileNode);
        SymbolTable.Scope scope = scopePerNode.get(whileNode);
        List<String> accessedVariables = scope.filter(visitor.getAccessedVariables());
        String[] writtenVariables = scope.filter(visitor.getWrittenVariables()).toArray(new String[0]);

        Location location = whileNode.location;
        String methodName = "loop_method" + location.line + "_" + location.line;

        ParametersNode parametersNode = new ParametersNode(location,
                accessedVariables.stream().map(v -> new ParameterNode(location, v)).collect(Collectors.toList()));
        List<ExpressionNode> arguments = accessedVariables.stream().map(v -> new VariableAccessNode(location, v)).collect(Collectors.toList());
        ArgumentsNode argNode = new ArgumentsNode(location, arguments);

        MethodInvocationNode invocation = new MethodInvocationNode(location, methodName, argNode);
        StatementNode invocationAssignment;
        if (writtenVariables.length > 0) {
            invocationAssignment = new MultipleVariableAssignmentNode(location, writtenVariables, invocation);
        } else {
            invocationAssignment = new ExpressionStatementNode(invocation);
        }

        // if (condition) { body; written_vars = f(accessed_vars)} return written_vars
        BlockNode body = new BlockNode(location, asArrayList(
                new IfStatementNode(location, whileNode.conditionalExpression, new BlockNode(location, concatAsArrayList(whileNode.body.statementNodes,
                        Collections.singletonList(invocationAssignment))
                ), new BlockNode(location, Collections.emptyList())),
                new ReturnStatementNode(location, Arrays.stream(writtenVariables).map(v -> new VariableAccessNode(location, v)).collect(Collectors.toList())))
        );

        newMethods.add(new Parser.MethodNode(location, methodName, parametersNode, body,
                new Parser.GlobalVariablesNode(location, Collections.emptyMap())));
        visitChildrenDiscardReturn(whileNode);
        return Optional.of(invocationAssignment);
    }
}
