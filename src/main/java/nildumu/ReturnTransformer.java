package nildumu;

import nildumu.typing.Types;

import java.util.*;

import static nildumu.Parser.*;
import static swp.lexer.Location.ZERO;

/**
 * Idea transform from non-SSA without loops and return statement at arbitrary places to non-SSA without loops
 * and a return statement only at the end
 * <p>
 * Assumption: it gets passed the raw program without any transformations (or NameResolution for that matter)
 * except the loop transformation
 * <p>
 * Idea: Every return statement splits the current block (and all outer recursively) into two parts:
 * A part before the return and a part after. The part before is appended to set the ___return_val and
 * ___return_taken variables accordingly.
 * The part after is wrapped in an <code>if (!___return_taken){ … }</code>.
 * The boolean return type is true iff the part after the visited statement should be wrapped.
 */
public class ReturnTransformer implements StatementVisitor<Boolean> {

    private final String RETURN_TAKEN = "___return_taken";
    private final String RETURN_VAL = "___return_val";

    private final Types types;

    private ReturnTransformer(ProgramNode programNode) {
        this.types = programNode.types;
    }

    /**
     * don't pass the output to {@link SSAResolution2} directly, round trip through a string first
     */
    public static void process(ProgramNode program) {
        ReturnTransformer transformer = new ReturnTransformer(program);
        program.methods().forEach(transformer::visit);
    }

    @Override
    public Boolean visit(StatementNode statement) {
        return visitChildren(statement).stream().anyMatch(b -> b);
    }

    public void visit(MethodNode method) {
        if (!checkApplicability(method)) { // don't change programs that have at most at return statement at the end
            return;
        }
        method.body.accept(this);
        method.body.addAll(0, Arrays.asList(new VariableDeclarationNode(ZERO, RETURN_TAKEN, types.INT, literal(0)),
                                            new VariableDeclarationNode(ZERO, RETURN_VAL, method.getReturnType())));
        method.body.add(new ReturnStatementNode(ZERO, new VariableAccessNode(ZERO, RETURN_VAL)));
    }

    @Override
    public Boolean visit(BlockNode block) {
        // replace every return statement by an ___return_val assignment and ___return_taken
        // and wrap every block in an if (!___return_taken){}
        List<StatementNode> newBody = new ArrayList<>();
        for (int i = 0; i < block.statementNodes.size(); i++) {
            StatementNode statementNode = block.statementNodes.get(i);
            boolean shouldSplit;
            if (statementNode instanceof ReturnStatementNode) {
                ReturnStatementNode ret = (ReturnStatementNode)statementNode;
                newBody.add(new VariableAssignmentNode(ZERO, RETURN_TAKEN, literal(1)));
                if (ret.hasReturnExpression()) {
                    newBody.add(new VariableAssignmentNode(ZERO, RETURN_VAL, ret.expression));
                }
                shouldSplit = true;
            } else {
                shouldSplit = statementNode.accept(this);
                newBody.add(statementNode);
            }
            if (shouldSplit) {
                BlockNode rest = block.getSubBlock(i + 1);
                if (!rest.isEmpty()) {
                    IfStatementNode wrapper = new IfStatementNode(ZERO,
                            new UnaryOperatorNode(new VariableAccessNode(ZERO, RETURN_TAKEN),
                                    LexerTerminal.INVERT), rest);
                    wrapper.accept(this);
                    newBody.add(wrapper);
                }
                block.statementNodes.clear();
                block.statementNodes.addAll(newBody);
                return true;
            }
        }
        return false;
    }


    private boolean checkApplicability(MethodNode method) {
        StatementVisitor<Boolean> statementVisitor = new StatementVisitor<Boolean>() {

            @Override
            public Boolean visit(StatementNode statement) {
                return visitChildren(statement).stream().anyMatch(b -> b);
            }

            @Override
            public Boolean visit(ReturnStatementNode returnStatement) {
                return true;
            }
        };
        return (method.body.getLastStatementOrNull() instanceof ReturnStatementNode ?
                method.body.statementNodes.subList(0, method.body.statementNodes.size() - 1) :
                method.body.statementNodes)
                .stream().anyMatch(s -> s.accept(statementVisitor));
    }

}
