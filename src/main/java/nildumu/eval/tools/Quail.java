package nildumu.eval.tools;

import nildumu.Parser;
import nildumu.eval.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Uses the Quail tool, https://project.inria.fr/quail/
 */
public class Quail extends AbstractTool {

    private static final Path TOOL = Paths.get("../eval-programs/quail-2.0/quail");

    protected Quail() {
        super("quail", false);
    }

    @Override
    public AnalysisPacket createPacket(TestProgram program, Path folder) {
        Path sourceFile = folder.resolve(program.getUniqueCodeName(".quail"));
        try {
            Files.write(sourceFile, Collections.singletonList(toQuailLang(program)));
            System.out.println(toQuailLang(program));
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        } catch (UnsupportedLanguageFeatureException f){
            return AnalysisPacket.empty(this, program);
        }
        return new AnalysisPacket(this, program) {
            @Override
            public String getShellCommand(PathFormatter formatter, Duration timeLimit) {
                return String.format("%s -t %d %s",
                        formatter.format(TOOL),
                        timeLimit.toMillis() - 500,
                        formatter.format(sourceFile));
            }

            @Override
            public LeakageParser getLeakageParser() {
                return LeakageParser.forLine(tool, "The leakage is ", " bits");
            }
        };
    }

    private String toQuailLang(TestProgram program){
        if (program.hasSpecialVersion("quail")){
            return program.getSpecialVersion("quail");
        }
        throw new UnsupportedLanguageFeatureException(this, "program transformation");
        //return placeDeclsAtTheBeginning(formatStatement(program.program.globalBlock,
        //        "int" + program.integerType.width) + "\nreturn;");
    }

    private String placeDeclsAtTheBeginning(String quail){
        List<String> decls = new ArrayList<>();
        List<String> nonDecls = new ArrayList<>();
        for (String line : quail.split("\n")) {
            if (line.startsWith("public ") || line.startsWith("observable") ||
                    line.startsWith("private") || line.startsWith("secret")){
                decls.add(line);
            } else {
                nonDecls.add(line);
            }
        }
        return Stream.concat(decls.stream(), nonDecls.stream()).collect(Collectors.joining("\n"));
    }

    private String formatStatement(Parser.StatementNode stmt, String integerTypeStr) {
        return stmt.accept(new Parser.StatementVisitor<String>(){

            @Override
            public String visit(Parser.StatementNode statement) {
                return null;
            }

            @Override
            public String visit(Parser.VariableAssignmentNode assignment) {
                return String.format("assign %s := %s;", assignment.variable,
                        formatExpression(assignment.expression));
            }

            @Override
            public String visit(Parser.VariableDeclarationNode variableDeclaration) {
                if (variableDeclaration.hasInitExpression()) {
                    return String.format("public %s %s;\nassign %s := %s;",
                            integerTypeStr,
                            variableDeclaration.variable,
                            variableDeclaration.variable,
                            formatExpression(variableDeclaration.expression));
                }
                return String.format("public %s %s;",
                        integerTypeStr,
                        variableDeclaration.variable);
            }

            @Override
            public String visit(Parser.OutputVariableDeclarationNode outputDecl) {
                return String.format("observable %s %s;\nassign %s := %s;",
                        integerTypeStr, outputDecl.variable, outputDecl.variable,
                        formatExpression(outputDecl.expression));
            }

            @Override
            public String visit(Parser.InputVariableDeclarationNode inputDecl) {
                return ("public INT L:=0;\n" +
                        "secret INT H;\n" +
                        "if (H>0) then\n" +
                        "    while (H!=L) do\n" +
                        "      assign L:=L+1;\n" +
                        "    od\n" +
                        "else\n" +
                        "    while (H!=L) do\n" +
                        "      assign L:=L-1;\n" +
                        "    od\n" +
                        "fi\n" +
                        "\n").replaceAll("INT", integerTypeStr)
                                .replaceAll("H", "llllllllll" + inputDecl.variable)
                                .replaceAll("L", inputDecl.variable);
            }

            @Override
            public String visit(Parser.BlockNode block) {
                return block.statementNodes.stream().map(s ->
                        formatStatement(s, integerTypeStr))
                        .collect(Collectors.joining("\n"));
            }

            @Override
            public String visit(Parser.IfStatementNode ifStatement) {
                String thenStr = formatStatement(ifStatement.ifBlock, integerTypeStr);
                if (ifStatement.hasElseBlock() && !(ifStatement.elseBlock.statementNodes.get(0) instanceof Parser.EmptyStatementNode)) {
                    return String.format("if (%s) then \n%s else \n%s\nfi\n",
                            formatExpression(ifStatement.conditionalExpression), thenStr,
                            formatStatement(ifStatement.elseBlock, integerTypeStr));
                } else {
                    return String.format("if (%s) then \n%s\nfi\n",
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
                return String.format("while (%s) do \n%s od\n",
                        formatExpression(whileStatement.conditionalExpression),
                        formatStatement(whileStatement.body, integerTypeStr));
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
                assert false;
                return String.format("return (%s)(%s);", integerTypeStr,
                        formatExpression(returnStatement.expressions.get(0)));
            }
        });
    }

    private String formatExpression(Parser.ExpressionNode expression){
        return expression.accept(new Parser.ExpressionVisitor<String>() {
            @Override
            public String visit(Parser.ExpressionNode expression) {
                return "";
            }

            @Override
            public String visit(Parser.IntegerLiteralNode literal) {
                return literal.value.asInt() + "";
            }

            @Override
            public String visit(Parser.UnaryOperatorNode unaryOperator) {
                return String.format("(%s%s)",
                        unaryOperator.operator.getTerminalDescription(),
                        formatExpression(unaryOperator.expression));
            }

            @Override
            public String visit(Parser.BinaryOperatorNode binaryOperator) {
                String left = formatExpression(binaryOperator.left);
                String right = formatExpression(binaryOperator.right);
                switch (binaryOperator.operator){
                    case MINUS:
                    case MULTIPLY:
                    case DIVIDE:
                    case MODULO:
                    case LOWER:
                    case LOWER_EQUALS:
                    case GREATER_EQUALS:
                    case GREATER:
                    case EQUALS:
                    case UNEQUALS:
                    case PLUS:
                        return left + binaryOperator.operator.getTerminalDescription().replace("\\", "") + right;
                    case LEFT_SHIFT:
                        if (binaryOperator.right instanceof Parser.IntegerLiteralNode){
                            return left + " * " + (2 << ((Parser.IntegerLiteralNode) binaryOperator.right).value.asInt());
                        }
                }
                throw new UnsupportedLanguageFeatureException(Quail.this,
                        binaryOperator.operator.toString());
            }

            @Override
            public String visit(Parser.VariableAccessNode variableAccess) {
                return variableAccess.ident;
            }

            @Override
            public String visit(Parser.ParameterAccessNode variableAccess) {
                assert false;
                return variableAccess.ident;
            }

            @Override
            public String visit(Parser.SingleUnaryOperatorNode unaryOperator) {
                assert false;
                return null;
            }
        });
    }
}
