package nildumu;

import nildumu.util.DefaultMap;
import swp.util.Pair;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static nildumu.Lattices.vl;
import static nildumu.Parser.*;
import static nildumu.Parser.LexerTerminal.*;
import static nildumu.util.Util.p;

/**
 * Only runs after the type transformation
 */
public class MetaOperatorTransformator implements NodeVisitor<MJNode> {

    private final int maxBitWidth;
    private final boolean transformPlus;

    private final DefaultMap<ExpressionNode, ExpressionNode> replacedMap = new DefaultMap<>((map, node) -> repl(node));

    private final DefaultMap<ConditionalStatementNode, ConditionalStatementNode> replacedCondStmtsMap = new DefaultMap<>((map, stmt) -> stmt);

    MetaOperatorTransformator(int maxBitWidth, boolean transformPlus) {
        this.maxBitWidth = maxBitWidth;
        this.transformPlus = transformPlus;
    }

    public ProgramNode process(ProgramNode program){
        ProgramNode newProgram = (ProgramNode)visit(program);
        setDefiningAndConditionalExpressions(newProgram);
        return newProgram;
    }

    ExpressionNode replaceAndWrapIfNeeded(ExpressionNode expression){
        ExpressionNode tmpExpr = expression.accept(new ExpressionVisitor<ExpressionNode>() {

            @Override
            public ExpressionNode visit(ExpressionNode expression) {
                return expression;
            }

            @Override
            public ExpressionNode visit(PhiNode phi) {
                return wrap(phi);
            }

            @Override
            public ExpressionNode visit(VariableAccessNode variableAccess) {
                return wrap(variableAccess);
            }

            ExpressionNode wrap(ExpressionNode expr){
                return new BinaryOperatorNode(new BracketedAccessOperatorNode(expr, new IntegerLiteralNode(expr.location, vl.parse(1))),
                        new IntegerLiteralNode(expr.location, vl.parse(1)), EQUALS);
            }
        });
        replacedMap.put(expression, repl(tmpExpr));
        return replacedMap.get(expression);
    }

    ExpressionNode replace(ExpressionNode expression){
        return replacedMap.get(expression);
    }

    private ExpressionNode repl(ExpressionNode expression){
        return replaceExpressionWithExpression(expression, (node) -> node instanceof BinaryOperatorNode || node instanceof UnaryOperatorNode,  (node) -> {
            if (node instanceof BinaryOperatorNode) {
                BinaryOperatorNode binOp = (BinaryOperatorNode) node;
                switch (binOp.operator) {
                    case GREATER:
                        return new BinaryOperatorNode(binOp.right, binOp.left, LOWER);
                    case LOWER_EQUALS:
                    case GREATER_EQUALS:
                        ExpressionNode left = binOp.left;
                        ExpressionNode right = binOp.right;
                        LexerTerminal op = LOWER;
                        if (binOp.operator == GREATER_EQUALS) {
                            ExpressionNode tmp = left;
                            left = right;
                            right = tmp;
                            op = LOWER;
                        }
                        return repl(new BinaryOperatorNode(new BinaryOperatorNode(left, right, op), new BinaryOperatorNode(left, right, EQUALS), BOR));
                    case MINUS:
                        return repl(new BinaryOperatorNode(new BinaryOperatorNode(binOp.left, new UnaryOperatorNode(binOp.right, TILDE), PLUS), new IntegerLiteralNode(binOp.location, Lattices.ValueLattice.get().parse(1)), PLUS));
                    case PLUS:
                        if (transformPlus) {
                            return plus(binOp);
                        }
                    default:
                        return binOp;
                }
            } else if (node instanceof UnaryOperatorNode){
                UnaryOperatorNode unOp = (UnaryOperatorNode)node;
                if (unOp.operator == MINUS) {
                    return new BinaryOperatorNode(new IntegerLiteralNode(unOp.location, Lattices.ValueLattice.get().parse(1)), new UnaryOperatorNode(unOp.expression, INVERT), PLUS);
                }
                return unOp;
            }
            return node;
        });
    }

    private Pair<ExpressionNode, ExpressionNode> halfAdder(ExpressionNode a, ExpressionNode b){
        return p(binop(XOR, a, b), binop(BAND, a, b));
    }

    private Pair<ExpressionNode, ExpressionNode> fullAdder(ExpressionNode a, ExpressionNode b, ExpressionNode c){
        Pair<ExpressionNode, ExpressionNode> pair = halfAdder(a, b);
        Pair<ExpressionNode, ExpressionNode> pair2 = halfAdder(pair.first, c);
        ExpressionNode carry = binop(BOR, pair.second, pair2.second);
        return p(pair2.first, carry);
    }

    private ExpressionNode plus(BinaryOperatorNode node){
        List<ExpressionNode> res = new ArrayList<>();
        ExpressionNode zero = new IntegerLiteralNode(node.location, Lattices.ValueLattice.get().parse(0));
        ExpressionNode result = zero;
        ExpressionNode carry = zero;
        for (int i = 1; i <= maxBitWidth; i++){
            Pair<ExpressionNode, ExpressionNode> rCarry = fullAdder(new BracketedAccessOperatorNode(node.left,
                            new IntegerLiteralNode(node.location, vl.parse(i))),
                    new BracketedAccessOperatorNode(node.right, new IntegerLiteralNode(node.location, vl.parse(i))), carry);
            carry = rCarry.second;
            result = binop(BOR, result, new BitPlaceOperatorNode(rCarry.first, i));
        }
        return result;
    }


    private BinaryOperatorNode binop(LexerTerminal op, ExpressionNode left, ExpressionNode right){
        return new BinaryOperatorNode(left, right, op);
    }

    ExpressionNode replaceExpressionWithExpression(ExpressionNode node, Predicate<ExpressionNode> matcher, Function<ExpressionNode, ExpressionNode> replacement){
        if (node == null){
            return null;
        }
        ExpressionNode replExpr = node.accept(new NodeVisitor<ExpressionNode>() {
            @Override
            public ExpressionNode visit(MJNode node) {
                visitChildrenDiscardReturn(node);
                return null;
            }

            @Override
            public ExpressionNode visit(ExpressionNode expression) {
                return replace(expression);
            }

            @Override
            public ExpressionNode visit(UnaryOperatorNode unaryOperator) {
                return replace(new UnaryOperatorNode(visitAndReplace(unaryOperator.expression), unaryOperator.operator));
            }

            @Override
            public ExpressionNode visit(BitPlaceOperatorNode unaryOperator) {
                return replace(new BitPlaceOperatorNode(visitAndReplace(unaryOperator.expression), unaryOperator.index));
            }

            @Override
            public ExpressionNode visit(BinaryOperatorNode binaryOperator) {
                return replace(new BinaryOperatorNode(visitAndReplace(binaryOperator.left), visitAndReplace(binaryOperator.right), binaryOperator.operator));
            }

            @Override
            public ExpressionNode visit(BracketedAccessOperatorNode bracketedAccess) {
                return replace(new BracketedAccessOperatorNode(visitAndReplace(bracketedAccess.left),
                        visitAndReplace(bracketedAccess.right)));
            }

            @Override
            public ExpressionNode visit(PrimaryExpressionNode primaryExpression) {
                return replace(primaryExpression);
            }

            @Override
            public ExpressionNode visit(PhiNode phi) {
                PhiNode node = new PhiNode(phi.location, new ArrayList<>(), new ArrayList<>(phi.joinedVariables.stream().map(j -> (VariableAccessNode)visit(j)).collect(Collectors.toList())));
                node.controlDepStatement = phi.controlDepStatement;
                return node;
            }

            private ExpressionNode replace(ExpressionNode node){
                if (matcher.test(node)){
                    return replacement.apply(node);
                }
                return node;
            }

            private ExpressionNode visitAndReplace(ExpressionNode node){
                node = replace(node);
                node = node.accept(this);
                node = replace(node);
                return node;
            }

            @Override
            public ExpressionNode visit(MethodInvocationNode methodInvocation) {
                MethodInvocationNode node =
                        new MethodInvocationNode(methodInvocation.location, methodInvocation.method,
                                new Parser.ArgumentsNode(methodInvocation.arguments.location,
                                        methodInvocation.arguments.arguments.stream()
                                                .map(this::replace).collect(Collectors.toList())),
                                methodInvocation.globals);
                node.globalDefs = methodInvocation.globalDefs;
                node.definition = methodInvocation.definition;
                return node;
            }

            @Override
            public ExpressionNode visit(VariableAssignmentNode assignment) {
                assignment.expression = assignment.expression.accept(this);
                return null;
            }

            @Override
            public ExpressionNode visit(ArrayAssignmentNode assignment) {
                assignment.expression = assignment.expression.accept(this);
                assignment.arrayIndex = assignment.arrayIndex.accept(this);
                return null;
            }

            @Override
            public ExpressionNode visit(ArrayLiteralNode primaryExpression) {
                return new ArrayLiteralNode(primaryExpression.location, primaryExpression.elements.stream().map(e -> e.accept(this)).collect(Collectors.toList()));
            }

            @Override
            public ExpressionNode visit(TupleLiteralNode primaryExpression) {
                return new TupleLiteralNode(primaryExpression.location, primaryExpression.elements.stream().map(e -> e.accept(this)).collect(Collectors.toList()));
            }
        });
        replacedMap.put(node, replExpr);
        return replExpr;
    }

    @Override
    public MJNode visit(MJNode node) {
        visitChildrenDiscardReturn(node);
        return node;
    }

    @Override
    public MJNode visit(ProgramNode program) {
        List<StatementNode> newStatements = program.globalBlock.statementNodes.stream().map(s -> (StatementNode)s.accept(this)).collect(Collectors.toList());
        program.globalBlock.statementNodes.clear();
        program.globalBlock.addAll(newStatements);
        for (String methodName : program.getMethodNames()){
            MethodNode method = program.getMethod(methodName);
            visit(method);
        }
        return program;
    }

    @Override
    public MJNode visit(MethodNode method) {
        List<StatementNode> newStatementNodes = method.body.statementNodes.stream().map(v -> (StatementNode)v.accept(this)).collect(Collectors.toList());
        method.body.statementNodes.clear();
        method.body.addAll(newStatementNodes);
        return null;
    }

    @Override
    public MJNode visit(VariableAssignmentNode assignment){
        VariableAssignmentNode node = new VariableAssignmentNode(assignment.location, assignment.variable, replace(assignment.expression));
        node.definition = assignment.definition;
        return node;
    }

    @Override
    public MJNode visit(ArrayAssignmentNode assignment){
        VariableAssignmentNode node = new ArrayAssignmentNode(assignment.location, assignment.variable,
                replace(assignment.arrayIndex), replace(assignment.expression));
        node.definition = assignment.definition;
        return node;
    }

    @Override
    public MJNode visit(VariableDeclarationNode variableDeclaration){
        VariableDeclarationNode node = new VariableDeclarationNode(variableDeclaration.location, variableDeclaration.variable, variableDeclaration.getVarType(), replace(variableDeclaration.expression),
                variableDeclaration.hasAppendValue);
        node.definition = variableDeclaration.definition;
        return node;
    }

    @Override
    public MJNode visit(OutputVariableDeclarationNode decl){
        OutputVariableDeclarationNode node = new OutputVariableDeclarationNode(decl.location, decl.variable, decl.getVarType(), replace(decl.expression), decl.secLevel);
        node.definition = decl.definition;
        return node;
    }

    @Override
    public MJNode visit(InputVariableDeclarationNode decl){
        InputVariableDeclarationNode node = new InputVariableDeclarationNode(decl.location, decl.variable, decl.getVarType(), (IntegerLiteralNode) replace(decl.expression), decl.secLevel);
        node.definition = decl.definition;
        return node;
    }

    @Override
    public MJNode visit(TmpInputVariableDeclarationNode decl){
        TmpInputVariableDeclarationNode node = new TmpInputVariableDeclarationNode(decl.location, decl.variable, decl.getVarType(), (IntegerLiteralNode) replace(decl.expression), decl.secLevel);
        node.definition = decl.definition;
        return node;
    }

    @Override
    public MJNode visit(AppendOnlyVariableDeclarationNode decl){
        AppendOnlyVariableDeclarationNode node = new AppendOnlyVariableDeclarationNode(decl.location, decl.variable, decl.getVarType(), decl.secLevel, decl.isInput);
        node.definition = decl.definition;
        return node;
    }


    @Override
    public MJNode visit(BlockNode block){
        return new BlockNode(block.location, block.statementNodes.stream().map(s -> (StatementNode)s.accept(this)).filter(Objects::nonNull).collect(Collectors.toList()));
    }

    @Override
    public MJNode visit(IfStatementNode ifStatement){
        ConditionalStatementNode stmt = new IfStatementNode(ifStatement.location, replace(ifStatement.conditionalExpression),
                (StatementNode)ifStatement.ifBlock.accept(this), (StatementNode)ifStatement.elseBlock.accept(this));
        replacedCondStmtsMap.put(ifStatement, stmt);
        return stmt;
    }

    @Override
    public MJNode visit(IfStatementEndNode ifEndStatement){
        return ifEndStatement;
    }

    @Override
    public MJNode visit(WhileStatementNode whileStatement){
        ConditionalStatementNode stmt = new WhileStatementNode(whileStatement.location,
                whileStatement.getPreCondVarAss().stream()
                        .map(v -> (VariableAssignmentNode)v.accept(this)).collect(Collectors.toList()),
                replaceAndWrapIfNeeded(whileStatement.conditionalExpression),
                (StatementNode)whileStatement.body.accept(this));
        replacedCondStmtsMap.put(whileStatement, stmt);
        return stmt;
    }

    @Override
    public MJNode visit(WhileStatementEndNode whileEndStatement){
        return null;
    }

    @Override
    public MJNode visit(ExpressionStatementNode expressionStatement){
        return new ExpressionStatementNode(replace(expressionStatement.expression));
    }

    @Override
    public MJNode visit(ReturnStatementNode returnStatement){
        if (returnStatement.hasReturnExpression()){
            return new ReturnStatementNode(returnStatement.location, replace(returnStatement.expression));
        }
        return new ReturnStatementNode(returnStatement.location);
    }

    public void setDefiningAndConditionalExpressions(MJNode node){
        node.accept(new NodeVisitor<Object>() {

            @Override
            public Object visit(MJNode node) {
                visitChildrenDiscardReturn(node);
                return null;
            }

            @Override
            public Object visit(VariableAccessNode variableAccess) {
                variableAccess.definingExpression = replace(variableAccess.definingExpression);
                return null;
            }

            @Override
            public Object visit(PhiNode phi) {
                visitChildrenDiscardReturn(phi);
                //phi.controlDeps = phi.controlDeps.stream().map(MetaOperatorTransformator.this::replace).collect(Collectors.toList());
                phi.joinedVariables.forEach(v -> v.definingExpression = replace(v.definingExpression));
                return null;
            }

            @Override
            public Object visit(WhileStatementNode whileStatement) {
                visitChildrenDiscardReturn(whileStatement);
                return null;
            }
        });
        node.accept(new NodeVisitor<Object>() {

            MethodNode currentMethod;

            @Override
            public Object visit(MJNode node) {
                visitChildrenDiscardReturn(node);
                return null;
            }

            @Override
            public Object visit(VariableAccessNode variableAccess) {
                /*variableAccess.definingExpression = variableToExpr.get(variableAccess.definition);
                if (currentMethod != null && currentMethod.parameters.parameterNodes.stream().anyMatch(p -> p.definition == variableAccess.definition)){
                    variableAccess.definingExpression = new ParameterAccessNode(variableAccess.location, variableAccess.ident);
                    ((ParameterAccessNode) variableAccess.definingExpression).definition = variableAccess.definition;
                }*/
                return null;
            }

            @Override
            public Object visit(PhiNode phi) {
                visitChildrenDiscardReturn(phi);
                phi.controlDepStatement = replacedCondStmtsMap.get(phi.controlDepStatement);
                assert phi.controlDeps.size() == 1;
                phi.controlDeps = Collections.singletonList(phi.controlDepStatement.conditionalExpression);
                return null;
            }


            @Override
            public Object visit(MethodNode method) {
                currentMethod = method;
                visitChildrenDiscardReturn(method);
                return null;
            }
        });
    }
}
