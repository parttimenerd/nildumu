package nildumu.eval;

import java.util.*;
import java.util.function.*;

import nildumu.*;
import swp.lexer.Location;

import static nildumu.Lattices.*;
import static nildumu.Parser.*;

public class Generator {

    static Location loc() {
        return new Location(0, 0);
    }

    public static IfStatementNode createNestedIfStatement(int comparisons,
                                                          Function<Integer, ExpressionNode> condExprCreator,
                                                          Function<Integer, StatementNode> thenStmtCreator) {
        StatementNode cur = new EmptyStatementNode(loc());
        for (int i = comparisons - 1; i > 0; i--) {
            cur = new IfStatementNode(loc(), condExprCreator.apply(i), thenStmtCreator.apply(i), cur);
        }
        return (IfStatementNode) cur;
    }

    static VariableAccessNode access(String variable){
        return new VariableAccessNode(loc(), variable);
    }

    /**
     * Each if statement has a condition of the form $h == I$, $h$ being the secret input variable
     * and $I$ being the nesting level, and $om$ and intermediate output value
     */
    public static ProgramNode createProgramOfNestedIfStmtsWithEqs(int comparisons,
                                                                  Function<Integer, StatementNode> thenStmtCreator) {
        ProgramNode program = new ProgramNode(new Context(BasicSecLattice.get(), 32));
        program.addGlobalStatement(new InputVariableDeclarationNode(loc(), "h", program.INT,
                new IntegerLiteralNode(loc(), vl.parse("0buuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuu")),
                "h"));
        program.addGlobalStatement(new VariableDeclarationNode(loc(), "om", program.INT, new IntegerLiteralNode(loc(), vl.parse(0))));
        program.addGlobalStatement(createNestedIfStatement(comparisons,
                i -> new BinaryOperatorNode(new VariableAccessNode(loc(), "h"),
                        new IntegerLiteralNode(loc(), vl.parse(i)), LexerTerminal.EQUALS), thenStmtCreator));
        program.addGlobalStatement(
                new OutputVariableDeclarationNode(loc(), "o", program.INT,
                        new VariableAccessNode(loc(), "om"), "l"));
        return program;
    }

    public static ProgramNode createProgramOfNestedIfStmtsWithEqsAndBasicAssign(int comparisons) {
        return createProgramOfNestedIfStmtsWithEqs(comparisons, i -> new VariableAssignmentNode(loc(), "o", new IntegerLiteralNode(loc(), vl.parse(i))));
    }

    public static BlockNode createMultipleIfStatements(int comparisons,
                                                       Function<Integer, ExpressionNode> condExprCreator,
                                                       Function<Integer, StatementNode> thenStmtCreator,
                                                       Function<Integer, StatementNode> elseStmtCreator) {
        List<StatementNode> ifs = new ArrayList<>();
        for (int i = 0; i < comparisons; i++) {
            ifs.add(new IfStatementNode(loc(), condExprCreator.apply(i), thenStmtCreator.apply(i), elseStmtCreator.apply(i)));
        }
        return new BlockNode(loc(), ifs);
    }

    /**
     * Each if statement has a condition of the form $h == I$, $h$ being the secret input variable
     * and $I$ being the nesting level, and $om$ and intermediate output value
     */
    public static ProgramNode createProgramOfIfStmtsWithEqs(int comparisons,
                                                                  Function<Integer, StatementNode> thenStmtCreator) {
        ProgramNode program = new ProgramNode(new Context(BasicSecLattice.get(), 32));
        program.addGlobalStatement(new InputVariableDeclarationNode(loc(), "h", program.INT,
                new IntegerLiteralNode(loc(), vl.parse("0buuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuu")),
                "h"));
        program.addGlobalStatement(new VariableDeclarationNode(loc(), "om", program.INT, new IntegerLiteralNode(loc(), vl.parse(1))));
        program.addGlobalStatement(createMultipleIfStatements(comparisons,
                i -> new BinaryOperatorNode(new VariableAccessNode(loc(), "h"),
                        literal(i + 1), LexerTerminal.EQUALS), thenStmtCreator,
                i -> new EmptyStatementNode(loc())));
        program.addGlobalStatement(
                new OutputVariableDeclarationNode(loc(), "o", program.INT,
                        new VariableAccessNode(loc(), "om"), "l"));
        return program;
    }

    public static ProgramNode createProgramOfIfStmtsWithEqs2(int comparisons) {
        ProgramNode program = new ProgramNode(new Context(BasicSecLattice.get(), 32));
        program.addGlobalStatement(new InputVariableDeclarationNode(loc(), "h", program.INT,
                new IntegerLiteralNode(loc(), vl.parse("0buuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuu")),
                "h"));
        for (int i = 0; i < 32; i++){
            List<Bit> bits = new ArrayList<>();
            for (int j = 0; j < 32; j++){
                if (j == i){
                    bits.add(bl.create(B.ONE));
                } else {
                    bits.add(bl.create(B.ZERO));
                }
            }
            program.addGlobalStatement(new VariableDeclarationNode(loc(), "v" + i, program.INT, new IntegerLiteralNode(loc(), new Value(bits))));
        }
        Function<Integer, ExpressionNode> intToExpr = i -> {
            Value val = vl.parse(i);
            ExpressionNode cur = literal(0);
            for (int k = 1; k <= 32; k++){
                if (val.get(k).val() == B.ONE){
                    VariableAccessNode acc = access("v" + (k - 1));
                    if (cur instanceof IntegerLiteralNode){
                        cur = acc;
                    } else {
                        cur = new BinaryOperatorNode(cur, acc, LexerTerminal.BOR);
                    }
                }
            }
            return cur;
        };
        program.addGlobalStatement(new VariableDeclarationNode(loc(), "om", program.INT, new IntegerLiteralNode(loc(), vl.parse(1))));
        program.addGlobalStatement(createMultipleIfStatements(comparisons,
                i -> new BinaryOperatorNode(new VariableAccessNode(loc(), "h"),
                        intToExpr.apply(i + 1), LexerTerminal.EQUALS), i -> new VariableAssignmentNode(loc(), "om", intToExpr.apply(i + 1)),
                i -> new EmptyStatementNode(loc())));
        program.addGlobalStatement(
                new OutputVariableDeclarationNode(loc(), "o", program.INT,
                        new VariableAccessNode(loc(), "om"), "l"));
        return program;
    }

    public static ProgramNode createProgramOfIfStmtsWithEqsSurroundedByCountingLoop(int comparisons,
                                                            Function<Integer, StatementNode> thenStmtCreator) {
        ProgramNode program = new ProgramNode(new Context(BasicSecLattice.get(), 32));
        program.addGlobalStatement(new InputVariableDeclarationNode(loc(), "h", program.INT,
                new IntegerLiteralNode(loc(), vl.parse("0buuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuu")),
                "h"));
        program.addGlobalStatement(new InputVariableDeclarationNode(loc(), "l", program.INT,
                new IntegerLiteralNode(loc(), vl.parse("0buuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuu")),
                "l"));
        program.addGlobalStatement(new VariableDeclarationNode(loc(), "z", program.INT, literal(1)));
        List<StatementNode> innerStmts = new ArrayList<>(createMultipleIfStatements(comparisons,
                i -> new BinaryOperatorNode(new VariableAccessNode(loc(), "h"),
                        literal(i + 1), LexerTerminal.EQUALS), thenStmtCreator,
                i -> new EmptyStatementNode(loc())).statementNodes);
        innerStmts.add(new VariableAssignmentNode(loc(), "l", new BinaryOperatorNode(new VariableAccessNode(loc(), "l"), literal(1), LexerTerminal.PLUS)));
        program.addGlobalStatement(new WhileStatementNode(loc(), new ArrayList<>(), new BinaryOperatorNode(access("l"), literal(0), LexerTerminal.LOWER), new BlockNode(loc(), innerStmts)));
        program.addGlobalStatement(
                new OutputVariableDeclarationNode(loc(), "o", program.INT,
                        new VariableAccessNode(loc(), "z"), "l"));
        return program;
    }

    public static ProgramNode createProgramOfIfStmtsWithEqsSurroundedByCountingLoop(int comparisons) {
        return createProgramOfIfStmtsWithEqsSurroundedByCountingLoop(comparisons, i -> new VariableAssignmentNode(loc(), "z", literal(i + 1)));
    }

    public static ProgramNode repeatedFibonaccis(int comparisons,
                                                 Function<Integer, ExpressionNode> fibArgumentCreator){
        ProgramNode program = new ProgramNode(new Context(BasicSecLattice.get(), 32));
        program.addMethod(((ProgramNode) generator.parse("int fib(int n) { int r = 1; if (n > 2) { r = fib(n - 1) + fib(n - 2);} return r;}"))
                .getMethod("fib"));
        program.addGlobalStatement(new InputVariableDeclarationNode(loc(), "h", program.INT,
                new IntegerLiteralNode(loc(), vl.parse("0buuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuu")),
                "h"));
        program.addGlobalStatement(new InputVariableDeclarationNode(loc(), "h2", program.INT,
                new IntegerLiteralNode(loc(), vl.parse("0buuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuu")),
                "h"));
        program.addGlobalStatement(new VariableDeclarationNode(loc(), "z", program.INT, literal(1)));
        List<StatementNode> innerStmts = new ArrayList<>();
        program.addGlobalStatements(createMultipleIfStatements(comparisons,
                i -> new BinaryOperatorNode(new VariableAccessNode(loc(), "h"),
                        literal(i + 1), LexerTerminal.EQUALS), i -> {
            return new VariableAssignmentNode(loc(), "z", new MethodInvocationNode(loc(), "fib", new ArgumentsNode(loc(), Collections.singletonList(fibArgumentCreator.apply(i)))));
                },
                i -> new EmptyStatementNode(loc())).statementNodes);
        program.addGlobalStatement(
                new OutputVariableDeclarationNode(loc(), "o", program.INT,
                        new VariableAccessNode(loc(), "z"), "l"));
        return program;
    }

    public static ProgramNode repeatedFibonaccis(int comparisons){
        return repeatedFibonaccis(comparisons, i -> new BinaryOperatorNode(
                new BinaryOperatorNode(access("h2"), access("h"), LexerTerminal.BAND),
                literal(0xffff), LexerTerminal.BAND));
    }

    public static ProgramNode repeatedManyFibonaccis(int comparisons,
                                                 Function<Integer, ExpressionNode> fibArgumentCreator){
        ProgramNode program = new ProgramNode(new Context(BasicSecLattice.get(), 32));
        for (int i = 0; i < comparisons; i++) {
            int other = i + 1 == comparisons ? 1: i + 2 % comparisons;
            program.addMethod(((ProgramNode) generator.parse(String.format("int fib%d(int n) { int r = 1; if (n > 2) { r = fib%d(n - 1) + fib%d(n - 2);} return r;}",
                    i + 1, other, other
                    )))
                    .getMethod(String.format("fib%d", i + 1)));
        }
        program.addGlobalStatement(new InputVariableDeclarationNode(loc(), "h", program.INT,
                new IntegerLiteralNode(loc(), vl.parse("0buuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuu")),
                "h"));
        program.addGlobalStatement(new InputVariableDeclarationNode(loc(), "h2", program.INT,
                new IntegerLiteralNode(loc(), vl.parse("0buuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuu")),
                "h"));
        program.addGlobalStatement(new VariableDeclarationNode(loc(), "z", program.INT, literal(1)));
        List<StatementNode> innerStmts = new ArrayList<>();
        program.addGlobalStatements(createMultipleIfStatements(comparisons,
                i -> new BinaryOperatorNode(new VariableAccessNode(loc(), "h"),
                        literal(i + 1), LexerTerminal.EQUALS), i -> {
                    return new VariableAssignmentNode(loc(), "z", new MethodInvocationNode(loc(), "fib" + (i + 1), new ArgumentsNode(loc(), Collections.singletonList(fibArgumentCreator.apply(i)))));
                },
                i -> new EmptyStatementNode(loc())).statementNodes);
        program.addGlobalStatement(
                new OutputVariableDeclarationNode(loc(), "o", program.INT,
                        new VariableAccessNode(loc(), "z"), "l"));
        return program;
    }

    public static ProgramNode repeatedManyFibonaccis(int comparisons){
        return repeatedManyFibonaccis(comparisons, i -> new BinaryOperatorNode(
                new BinaryOperatorNode(access("h2"), access("h"), LexerTerminal.BAND),
                literal(0xffff), LexerTerminal.BAND)
        );
    }


    public static ProgramNode createProgramOfIfStmtsWithEqsAndBasicAssign(int comparisons) {
        return createProgramOfIfStmtsWithEqs(comparisons, i -> new VariableAssignmentNode(loc(), "om", literal(i + 1)));
    }

    public static ProgramNode createProgramOfIfStmtsWithEqsAndBasicAssign2(int comparisons) {
        return createProgramOfIfStmtsWithEqs2(comparisons);
    }

    public static ProgramNode createProgramOfIfStmtsWithEqsSurroundedByWhile(int comparisons,
                                                            Supplier<BinaryOperatorNode> whileCondCreator,
                                                            Function<Integer, StatementNode> thenStmtCreator,
                                                            Function<Integer, StatementNode> elseStmtCreator) {
        ProgramNode program = new ProgramNode(new Context(BasicSecLattice.get(), 32));
        program.addGlobalStatement(new InputVariableDeclarationNode(loc(), "h", program.INT,
                new IntegerLiteralNode(loc(), vl.parse("0buuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuu")),
                "h"));
        program.addGlobalStatement(new VariableDeclarationNode(loc(), "om", program.INT, new IntegerLiteralNode(loc(), vl.parse(0))));
        program.addGlobalStatement(new WhileStatementNode(loc(), new ArrayList<>(), whileCondCreator.get(), createMultipleIfStatements(comparisons,
                i -> new BinaryOperatorNode(new VariableAccessNode(loc(), "h"),
                        new IntegerLiteralNode(loc(), vl.parse(i)), LexerTerminal.EQUALS), thenStmtCreator,
                elseStmtCreator)));
        program.addGlobalStatement(
                new OutputVariableDeclarationNode(loc(), "o", program.INT,
                        new VariableAccessNode(loc(), "om"), "l"));
        return program;
    }

    public static ProgramNode createProgramOfIfStmtsWithEqsAndBasicAssignSurroundedByWhile(int comparisons) {
        return createProgramOfIfStmtsWithEqsSurroundedByWhile(comparisons, () -> {
            return new BinaryOperatorNode(new BinaryOperatorNode(new VariableAccessNode(loc(), "h"), literal(31), LexerTerminal.LEFT_SHIFT), literal(0), LexerTerminal.EQUALS);
        }, i -> new VariableAssignmentNode(loc(), "om", new IntegerLiteralNode(loc(), vl.parse(i))),
                i -> i == comparisons - 1 ? new VariableAssignmentNode(loc(), "h", literal(1)) : new EmptyStatementNode(loc()));
    }
}
