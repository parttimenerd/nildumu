package nildumu;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import swp.parser.lr.BaseAST;

/**
 * Does a fix point iteration over nodes
 */
public class FixpointIteration {

    /**
     * Fix point iterates in topological order.
     * <p/>
     * Visits every node at least once. Uses the worklist algorithm
     *  @param nodeVisitor returns true if something changed
     * @param node node to start iterating on
     */
    public static void worklist(Parser.NodeVisitor<Boolean> nodeVisitor, Parser.MJNode node,
                                HashSet<Parser.StatementNode> statementNodesToOmitOneTime){
        assert !(node instanceof Parser.ExpressionNode);
        HashSet<Parser.MJNode> visitedBefore = new HashSet<>();
        Stack<Parser.MJNode> nodesToVisit = new Stack<>();
        nodesToVisit.add(node);
        while (!nodesToVisit.isEmpty() && !Thread.interrupted()){
            Parser.MJNode curNode = nodesToVisit.pop();
            boolean somethingChanged = curNode.accept(nodeVisitor);
            if (somethingChanged || !visitedBefore.contains(curNode)) {
                visitedBefore.add(curNode);
                List<Parser.MJNode> nodesToAdd = curNode.children().stream().filter(c -> {
                    if (statementNodesToOmitOneTime.contains(c)) {
                        statementNodesToOmitOneTime.remove(c);
                        return false;
                    }
                    return c instanceof Parser.MJNode && !(c instanceof Parser.ExpressionNode);
                }).map(c -> (Parser.MJNode)c).collect(Collectors.toList());
                Collections.reverse(nodesToAdd);
                nodesToVisit.addAll(nodesToAdd);
            }
        }
    }

    /**
     * Fix point iterates in topological order. Also walks the expressions.
     * <p/>
     * Visits every node at least once. Uses the worklist algorithm
     *  @param nodeVisitor returns true if something changed
     * @param node node to start iterating on
     */
    public static void worklist2(Parser.NodeVisitor<Boolean> nodeVisitor, Consumer<Parser.ExpressionNode> expressionConsumer,
                                 Parser.MJNode node, Set<Parser.StatementNode> statementNodesToOmitOneTime,
                                 Predicate<Parser.BinaryOperatorNode> evalSecondArgument,
                                 Consumer<Parser.BinaryOperatorNode> runAfterEvalSecondArgument){
        assert !(node instanceof Parser.ExpressionNode);
        Set<Parser.MJNode> visitedBefore = new HashSet<>();
        Stack<Parser.MJNode> nodesToVisit = new Stack<>();
        nodesToVisit.add(node);
        while (!nodesToVisit.isEmpty()){
            if (Thread.interrupted()){
                Thread.currentThread().interrupt();
                return;
            }
            Parser.MJNode curNode = nodesToVisit.pop();
            if (curNode instanceof Parser.WhileStatementNode){
                for (Parser.VariableAssignmentNode preCondVarAss : ((Parser.WhileStatementNode) curNode).getPreCondVarAss()) {
                    for (BaseAST childNode : preCondVarAss.children()){
                        if (childNode instanceof Parser.ExpressionNode){
                            if (childNode instanceof Parser.VariableAccessNode){
                                childNode = ((Parser.VariableAccessNode) childNode).definingExpression;
                            }
                            walkExpression(expressionConsumer, (Parser.ExpressionNode)childNode, evalSecondArgument, runAfterEvalSecondArgument);
                        }
                    }
                    preCondVarAss.accept(nodeVisitor);
                }
            }
            for (BaseAST childNode : curNode.children()){
                if (childNode instanceof Parser.ExpressionNode){
                    if (childNode instanceof Parser.VariableAccessNode){
                        Parser.ExpressionNode defExpr =
                                ((Parser.VariableAccessNode) childNode).definingExpression;
                        if (defExpr != null) {
                            childNode = defExpr;
                        }
                    }
                    walkExpression(expressionConsumer, (Parser.ExpressionNode)childNode, evalSecondArgument, runAfterEvalSecondArgument);
                }
            }
            boolean somethingChanged = curNode.accept(nodeVisitor);
            if (somethingChanged || !visitedBefore.contains(curNode)) {
                visitedBefore.add(curNode);
                if (curNode instanceof Parser.WhileStatementEndNode){
                    nodesToVisit.add(((Parser.WhileStatementEndNode) curNode).whileStatement);
                } else {
                    List<BaseAST> children = curNode.children();
                    List<Parser.MJNode> nodesToAdd = children.stream().filter(c -> {
                        if (statementNodesToOmitOneTime.contains(c)) {
                            statementNodesToOmitOneTime.remove(c);
                            return false;
                        }
                        return c instanceof Parser.MJNode && !(c instanceof Parser.ExpressionNode) && !(c instanceof Parser.MethodNode);
                    }).map(c -> (Parser.MJNode) c).collect(Collectors.toList());
                    Collections.reverse(nodesToAdd);
                    nodesToVisit.addAll(nodesToAdd);
                }
            }
        }
    }

    /**
     * Visits every node in an expression tree in a reversed topological order (i.e. post order).
     * Each sub expression is visited with the visiting results of its children as parameters.
     * <p/>
     * Assumes expression trees.
     *
     * @param expressionVisitor
     * @param expression expression to start with
     */
    public static <V> V walkExpression(Parser.ExpressionVisitorWArgs<V, List<V>> expressionVisitor, Parser.ExpressionNode expression){
        List<V> childResults = expression.children().stream().filter(c -> c instanceof Parser.ExpressionNode)
                .map(c -> walkExpression(expressionVisitor, (Parser.ExpressionNode)c))
                .collect(Collectors.toList());
        return expression.accept(expressionVisitor, childResults);
    }


    /**
     * Visits every node in an expression tree in a reversed topological order (i.e. post order).
     * Each sub expression is visited with the visiting results of its children as parameters.
     * <p/>
     * Assumes expression trees.
     */
    public static void walkExpression(Consumer<Parser.ExpressionNode> visitor, Parser.ExpressionNode expression,
                                      Predicate<Parser.BinaryOperatorNode> visitSecondArgument,
                                      Consumer<Parser.BinaryOperatorNode> runAfterEvalSecondArgument){
        if (Thread.interrupted()) {
            Thread.currentThread().interrupt();
            return;
        }
        List<Parser.ExpressionNode> children = expression.children().stream().filter(c -> c instanceof Parser.ExpressionNode &&
                !((c instanceof Parser.VariableAccessNode && ((Parser.VariableAccessNode) c).definingExpression != null) &&
                        !(c instanceof Parser.ParameterAccessNode)) && !(c instanceof Parser.PhiNode))
                .map(e -> (Parser.ExpressionNode)e).collect(Collectors.toList());
        if (expression instanceof Parser.BinaryOperatorNode && children.size() == 2) {
            walkExpression(visitor, children.get(0), visitSecondArgument, runAfterEvalSecondArgument);
            if (visitSecondArgument.test((Parser.BinaryOperatorNode) expression)){
                walkExpression(visitor, children.get(1), visitSecondArgument, runAfterEvalSecondArgument);
                runAfterEvalSecondArgument.accept((Parser.BinaryOperatorNode) expression);
            }
        } else {
            children.forEach(c -> walkExpression(visitor, c, visitSecondArgument, runAfterEvalSecondArgument));
        }


        visitor.accept(expression);
    }

    /**
     * Just lists the nodes in the order, that they would be traversed if the fix point would end after the first iteration
     */
    public static void trialRun(Parser.MJNode node){
        worklist2(new Parser.NodeVisitor<Boolean>() {
            @Override
            public Boolean visit(Parser.MJNode node) {
                return false;
            }

            @Override
            public Boolean visit(Parser.ExpressionNode expression) {
                walkExpression((expression1, argument) -> {
                    System.out.println("  → " + node.toString());
                    return null;
                }, expression);
                return false;
            }
        }, e -> System.out.println("    → " + e.toString() + " " + e.shortType()), node, new HashSet<>(), b -> true, b -> {});
    }
}
