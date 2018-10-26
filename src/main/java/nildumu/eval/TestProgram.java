package nildumu.eval;

import java.util.*;
import java.util.function.Function;
import java.util.stream.*;

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
    public String methodToJavaCode(Parser.MethodNode method, String signaturePrefix,
                                   String integerTypeString){
        return String.format("%s %s %s(%s){\n%s\n}", signaturePrefix,
                integerType.toJavaTypeName(), method.name,
                method.parameters.parameterNodes.stream()
                        .map(p -> integerTypeString + " " + p.name)
                        .collect(Collectors.joining(", ")),
                toJavaCode(method.body, null, null, integerTypeString));
    }

    public String methodsToJavaCode(String signaturePrefix){
        return program.methods().stream()
                .map(m -> methodToJavaCode(m, signaturePrefix, integerType.toJavaTypeName()))
                .collect(Collectors.joining("\n\n"));
    }

    public String methodsToJavaCode(String signaturePrefix, String integerTypeString){
        return program.methods().stream()
                .map(m -> methodToJavaCode(m, signaturePrefix, integerTypeString))
                .collect(Collectors.joining("\n\n"));
    }

    public String globalToJavaCode(
            Function<Parser.InputVariableDeclarationNode, String> inputHandler,
            Function<Parser.OutputVariableDeclarationNode, String> outputHandler){
        return globalToJavaCode(inputHandler, outputHandler, integerType.toJavaTypeName());
    }

    /**
     * Transforms the global block into java code, without enclosing it in a method.
     */
    public String globalToJavaCode(
            Function<Parser.InputVariableDeclarationNode, String> inputHandler,
            Function<Parser.OutputVariableDeclarationNode, String> outputHandler,
            String integerTypeStr){
        return program.globalBlock.statementNodes.stream().map(s -> {
            return s.accept(new Parser.StatementVisitor<String>(){

                @Override
                public String visit(Parser.StatementNode statement) {
                    return toJavaCode(statement, null, null, integerTypeStr);
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
                              Function<Parser.OutputVariableDeclarationNode, String> outputHandler) {
        return toJavaCode(stmt, inputHandler, outputHandler, integerType.toJavaTypeName());
    }

        private String toJavaCode(Parser.StatementNode stmt,
                              Function<Parser.InputVariableDeclarationNode, String> inputHandler,
                              Function<Parser.OutputVariableDeclarationNode, String> outputHandler,
                                  String integerTypeStr){
        if (integerTypeStr.equals("int")){
            return stmt.toPrettyString();
        }
        return stmt.accept(new Parser.StatementVisitor<String>(){

            @Override
            public String visit(Parser.StatementNode statement) {
                return null;
            }

            @Override
            public String visit(Parser.VariableAssignmentNode assignment) {
                return String.format("%s = (%s)%s;", assignment.variable, integerTypeStr, formatExpression(assignment.expression));
            }

            @Override
            public String visit(Parser.VariableDeclarationNode variableDeclaration) {
                if (variableDeclaration.hasInitExpression()) {
                    return String.format("%s %s = (%s)(%s);",
                            integerTypeStr,
                            variableDeclaration.variable,
                            integerTypeStr,
                            formatExpression(variableDeclaration.expression));
                }
                return String.format("%s %s;",
                        integerTypeStr,
                        variableDeclaration.variable);
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
                        toJavaCode(s, null, null, integerTypeStr))
                        .collect(Collectors.joining("\n"));
            }

            @Override
            public String visit(Parser.IfStatementNode ifStatement) {
                String thenStr = toJavaCode(ifStatement.ifBlock, null, null, integerTypeStr);
                if (ifStatement.hasElseBlock()) {
                    return String.format("if (%s) {\n%s} else {\n%s\n}\n",
                            formatExpression(ifStatement.conditionalExpression), thenStr,
                            toJavaCode(ifStatement.elseBlock, null, null, integerTypeStr));
                } else {
                    return String.format("if (%s) {\n%s}\n",
                            formatExpression(ifStatement.conditionalExpression),
                            thenStr);
                }
            }

            @Override
            public String visit(Parser.IfStatementEndNode ifEndStatement) {
                return "";
            }

            @Override
            public String visit(Parser.WhileStatementNode whileStatement) {
                return String.format("while (%s) {\n%s}\n",
                        formatExpression(whileStatement.conditionalExpression),
                        toJavaCode(whileStatement.body, null, null, integerTypeStr));
            }

            @Override
            public String visit(Parser.WhileStatementEndNode whileEndStatement) {
                return "";
            }

            @Override
            public String visit(Parser.ExpressionStatementNode expressionStatement) {
                return formatExpression(expressionStatement.expression) + ";";
            }

            @Override
            public String visit(Parser.ReturnStatementNode returnStatement) {
                return String.format("return (%s)(%s);", integerTypeStr,
                        formatExpression(returnStatement.expression));
            }
        });
    }

    public boolean hasMethods(){
        return program.methods().size() > 0;
    }

    public Set<String> getInputVariables(){
        return program.globalBlock.statementNodes.stream()
                .flatMap(n -> n.accept(new Parser.StatementVisitor<Stream<String>>() {

                    @Override
                    public Stream<String> visit(Parser.StatementNode statement) {
                        return Stream.empty();
                    }

                    @Override
                    public Stream<String> visit(Parser.InputVariableDeclarationNode inputDecl) {
                        return Stream.of(inputDecl.variable);
                    }
                })).collect(Collectors.toSet());
    }

    public Set<String> getOutputVariables(){
        return program.globalBlock.statementNodes.stream()
                .flatMap(n -> n.accept(new Parser.StatementVisitor<Stream<String>>() {

                    @Override
                    public Stream<String> visit(Parser.StatementNode statement) {
                        return Stream.empty();
                    }

                    @Override
                    public Stream<String> visit(Parser.OutputVariableDeclarationNode outputDecl) {
                        return Stream.of(outputDecl.variable);
                    }
                })).collect(Collectors.toSet());
    }

    public String formatExpression(Parser.ExpressionNode expression){
        return expression.accept(new Parser.ExpressionVisitor<String>() {
            @Override
            public String visit(Parser.ExpressionNode expression) {
                return "";
            }

            @Override
            public String visit(Parser.IntegerLiteralNode literal) {
                return formatValue(literal.value);
            }

            @Override
            public String visit(Parser.UnaryOperatorNode unaryOperator) {
                return String.format("(%s%s)",
                        unaryOperator.operator.getTerminalDescription(),
                        formatExpression(unaryOperator.expression));
            }

            @Override
            public String visit(Parser.BinaryOperatorNode binaryOperator) {
                return "(" + formatExpression(binaryOperator.left) +
                        binaryOperator.operator.getTerminalDescription().replace("\\", "") +
                        formatExpression(binaryOperator.right) + ")";
            }

            @Override
            public String visit(Parser.VariableAccessNode variableAccess) {
                return variableAccess.ident;
            }

            @Override
            public String visit(Parser.ParameterAccessNode variableAccess) {
                return variableAccess.ident;
            }

            @Override
            public String visit(Parser.SingleUnaryOperatorNode unaryOperator) {
                assert false;
                return null;
            }
        });
    }


    public String formatValue(Lattices.Value value){
        StringBuilder builder = new StringBuilder();
        builder.append("0b");
        for (int i = integerType.width; i >= 1; i--){
            builder.append(value.get(i).val());
        }
        return builder.toString();
    }
}