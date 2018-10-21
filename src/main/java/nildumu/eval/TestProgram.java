package nildumu.eval;

import java.util.function.Function;
import java.util.stream.Collectors;

import nildumu.*;
import nildumu.Parser.ProgramNode;

/**
 * Wrapper around a nildumu test program
 */
public class TestProgram implements Comparable<TestProgram> {

    public final String name;
    public final ProgramNode program;
    public final IntegerType integerType;

    public TestProgram(String name, ProgramNode program, IntegerType integerType) {
        this.name = name;
        this.program = program;
        this.integerType = integerType;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public int compareTo(TestProgram o) {
        return name.compareTo(o.name);
    }

    /**
     * @param signaturePrefix like {@code public static}
     */
    public String methodToJavaCode(Parser.MethodNode method, String signaturePrefix){
        return String.format("%s %s %s(%s){\n%s\n}", signaturePrefix,
                integerType.toJavaTypeName(), method.name,
                method.parameters.parameterNodes.stream()
                        .map(p -> integerType.toJavaTypeName() + " " + p.name)
                        .collect(Collectors.joining(", ")),
                toJavaCode(method.body));
    }

    public String methodsToJavaCode(String signaturePrefix){
        return program.methods().stream()
                .map(m -> methodToJavaCode(m, signaturePrefix))
                .collect(Collectors.joining("\n\n"));
    }

    /**
     * Transforms the global block into java code, without enclosing it in a method.
     */
    public String globalToJavaCode(
            Function<Parser.InputVariableDeclarationNode, String> inputHandler,
            Function<Parser.OutputVariableDeclarationNode, String> outputHandler){
        return program.globalBlock.statementNodes.stream().map(s -> {
            return s.accept(new Parser.StatementVisitor<String>(){

                @Override
                public String visit(Parser.StatementNode statement) {
                    return statement.toPrettyString();
                }

                @Override
                public String visit(Parser.OutputVariableDeclarationNode outputDecl) {
                    return outputHandler.apply(outputDecl);
                }

                @Override
                public String visit(Parser.InputVariableDeclarationNode inputDecl) {
                    return inputHandler.apply(inputDecl);
                }
            });
        }).collect(Collectors.joining("\n"));
    }

    private String toJavaCode(Parser.StatementNode stmt){
        return toJavaCode(stmt, null, null);
    }


        private String toJavaCode(Parser.StatementNode stmt,
                              Function<Parser.InputVariableDeclarationNode, String> inputHandler,
                              Function<Parser.OutputVariableDeclarationNode, String> outputHandler){
        if (integerType == IntegerType.INT){
            return stmt.toPrettyString();
        }
        return stmt.accept(new Parser.StatementVisitor<String>(){

            @Override
            public String visit(Parser.StatementNode statement) {
                return null;
            }

            @Override
            public String visit(Parser.VariableAssignmentNode assignment) {
                return assignment.toPrettyString() + ";";
            }

            @Override
            public String visit(Parser.VariableDeclarationNode variableDeclaration) {
                return String.format("%s %s = %s;", integerType.toJavaTypeName(),
                        variableDeclaration.variable,
                        variableDeclaration.expression.toPrettyString());
            }

            @Override
            public String visit(Parser.OutputVariableDeclarationNode outputDecl) {
                return outputHandler.apply(outputDecl);
            }

            @Override
            public String visit(Parser.InputVariableDeclarationNode inputDecl) {
                return inputHandler.apply(inputDecl);
            }

            @Override
            public String visit(Parser.BlockNode block) {
                return block.statementNodes.stream().map(s ->
                        toJavaCode(s))
                        .collect(Collectors.joining("\n"));
            }

            @Override
            public String visit(Parser.IfStatementNode ifStatement) {
                String thenStr = toJavaCode(ifStatement);
                if (ifStatement.hasElseBlock()) {
                    return String.format("if (%s) {\n%s\n} else {\n%s\n}\n",
                            ifStatement.conditionalExpression, thenStr,
                            toJavaCode(ifStatement.elseBlock));
                } else {
                    return String.format("if (%s) {\n%s\n}\n", ifStatement.conditionalExpression,
                            thenStr);
                }
            }

            @Override
            public String visit(Parser.IfStatementEndNode ifEndStatement) {
                return "";
            }

            @Override
            public String visit(Parser.WhileStatementNode whileStatement) {
                return String.format("while (s) {\n%s\n}\n", whileStatement.conditionalExpression,
                        toJavaCode(whileStatement.body));
            }

            @Override
            public String visit(Parser.WhileStatementEndNode whileEndStatement) {
                return "";
            }

            @Override
            public String visit(Parser.ExpressionStatementNode expressionStatement) {
                return expressionStatement.toString();
            }

            @Override
            public String visit(Parser.ReturnStatementNode returnStatement) {
                return "return " + returnStatement.expression.toPrettyString() + ";";
            }
        });
    }
}