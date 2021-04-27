package nildumu;

import nildumu.parser.LangBaseVisitor;
import nildumu.parser.LangLexer;
import nildumu.parser.LangParser;
import nildumu.typing.Type;
import nildumu.typing.Types;
import org.antlr.v4.runtime.*;
import swp.lexer.Location;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static nildumu.Lattices.vl;
import static nildumu.util.Util.p;

/**
 * @author Alexander Weigl
 * @author Johannes Bechberger
 * @version 2 (4/14/22)
 */
public class ParserFacade {

    static Lexer createLexer(CharStream stream, ExceptionErrorListener listener) {
        nildumu.parser.LangLexer lexer = new LangLexer(stream);
        lexer.removeErrorListeners();
        lexer.addErrorListener(listener);
        return lexer;
    }

    static LangParser createParser(CharStream stream, ExceptionErrorListener listener) {
        LangParser parser = new LangParser(new CommonTokenStream(createLexer(stream, listener)));
        parser.removeErrorListeners();
        parser.addErrorListener(listener);
        return parser;
    }

    static Parser.ProgramNode parse(CharStream stream, ExceptionErrorListener listener) {
        LangParser parser = createParser(stream, listener);
        LangParser.FileContext ctx = parser.file();
        return (Parser.ProgramNode)ctx.accept(new Translator());
    }

    public static Parser.ProgramNode parse(String program) {
        return parse(CharStreams.fromString(program), new ExceptionErrorListener(program));
    }

}

class ParseError extends NildumuError {

    public ParseError(int line, int charPositionInLine, String msg) {
        super(String.format("at %d:%d: %s", line, charPositionInLine, msg));
    }
}

class ExceptionErrorListener extends BaseErrorListener {
    private final String program;

    // public final List<Triple<Integer, Integer, String>> errors = new ArrayList<>();

    public ExceptionErrorListener(String program) {
        this.program = program;
    }

    @Override
    public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
        super.syntaxError(recognizer, offendingSymbol, line, charPositionInLine, msg, e);
        String codeLine = this.program.split("\n")[line - 1];
        codeLine = codeLine.substring(0, charPositionInLine) + "·" + codeLine.substring(charPositionInLine);
        throw new ParseError(line, charPositionInLine, msg + String.format(" in line '%s'", codeLine));
    }

    public void throwIfErrorneous() {

    }
}

class Translator extends LangBaseVisitor<Object> {

    public static Location tokenToLocation(Token token) {
        return new Location(token.getLine(), token.getCharPositionInLine());
    }

    @Override
    public Parser.ProgramNode visitFile(LangParser.FileContext ctx) {
        Lattices.SecurityLattice<?> secLattice =
                ctx.use_sec() == null
                        ? Lattices.BasicSecLattice.get()
                        : (Lattices.SecurityLattice<?>) ctx.use_sec().accept(this);
        accept(ctx.bit_width());
        Parser.ProgramNode node = new Parser.ProgramNode(new Context(secLattice, vl.bitWidth), types);
        List<Parser.StatementNode> statements = listOf(ctx.statement_w_semi());
        Parser.StatementNode end_statement = accept(ctx.statement_wo_semi());
        Parser.NodeVisitor visitor = new Parser.NodeVisitor<Object>() {

            @Override
            public Object visit(Parser.MJNode node) {
                return null;
            }

            @Override
            public Object visit(Parser.StatementNode statement) {
                node.addGlobalStatement(statement);
                return null;
            }

            @Override
            public Object visit(Parser.InputVariableDeclarationNode inputDecl) {
                node.context.addInputValue(secLattice.parse(inputDecl.secLevel), ((Parser.IntegerLiteralNode) inputDecl.expression).value);
                visit((Parser.StatementNode) inputDecl);
                return null;
            }

            @Override
            public Object visit(Parser.AppendOnlyVariableDeclarationNode appendDecl) {
                node.context.addAppendOnlyVariable(secLattice.parse(appendDecl.secLevel), appendDecl.variable);
                visit((Parser.StatementNode) appendDecl);
                return null;
            }
        };
        if (statements != null) {
            statements.forEach(n -> n.accept(visitor));
        }
        if (end_statement != null) {
            end_statement.accept(visitor);
        }
        ctx.method().forEach(m -> node.addMethod((Parser.MethodNode)m.accept(this)));
        node.handleInputAndPrint();
        return node;
    }

    @Override
    public Lattices.SecurityLattice visitUse_sec(LangParser.Use_secContext ctx) {
        return Lattices.SecurityLattice.forName(ctx.IDENT().getText());
    }

    private Location location(ParserRuleContext ctx) {
        if (ctx.start != null) {
            return new Location(ctx.start.getLine(), ctx.start.getCharPositionInLine());
        }
        return Location.ZERO;
    }

    @Override
    public Integer visitBit_width(LangParser.Bit_widthContext ctx) {
        vl.bitWidth = Integer.parseInt(ctx.INTEGER_LITERAL().getText());
        return vl.bitWidth;
    }

    @Override
    public Parser.BlockNode visitBlock_statement(LangParser.Block_statementContext ctx) {
        return accept(ctx.block());
    }

    @Override
    public Parser.StatementNode visitStatement_w_semi(LangParser.Statement_w_semiContext ctx) {
        return accept(ctx.normal_statement(), ctx.control_statement());
    }

    final Types types = new Types();

    @Override
    public Parser.VariableDeclarationNode visitVardecl(LangParser.VardeclContext ctx) {
        Parser.TypeNode typeNode = accept(ctx.type());
        Type type = typeNode.type;
        Parser.ExpressionNode expr = accept(ctx.expression(), ctx.phi());
        if (ctx.OUTPUT() != null) {
            return new Parser.OutputVariableDeclarationNode(
                    location(ctx),
                    ctx.ident().getText(),
                    type,
                    expr,
                    ctx.IDENT() == null ? "l" : ctx.IDENT().getText());
        }
        return new Parser.VariableDeclarationNode(
                location(ctx),
                ctx.ident().getText(),
                type == types.AINT ? types.INT : type,
                expr,
                type == types.AINT);
    }

    @Override
    public Object visitAppenddecl(LangParser.AppenddeclContext ctx) {
        boolean isInput = ctx.INPUT() != null;
        return new Parser.AppendOnlyVariableDeclarationNode(location(ctx), ctx.ident().getText(), types.INT,
                ctx.IDENT() != null ? ctx.IDENT().getText() : (isInput ? "h" : "l"), isInput);
    }

    @Override
    public Object visitInputdecl(LangParser.InputdeclContext ctx) {
        Parser.TypeNode typeNode = accept(ctx.type());
        Type type = typeNode.type;
        String secLevel = ctx.IDENT() == null ? "h" : ctx.IDENT().getText();
        Lattices.Value value = ctx.INPUT_LITERAL() != null ? vl.parse(ctx.INPUT_LITERAL().getText()) :
                vl.parse("0b" + java.util.stream.IntStream.range(0, vl.bitWidth).mapToObj(i -> "u")
                .collect(Collectors.joining()));
        Parser.IntegerLiteralNode integerLiteralNode = new Parser.IntegerLiteralNode(location(ctx), value);
        if (ctx.mod.getType() == LangParser.TMP_INPUT) {
            return new Parser.TmpInputVariableDeclarationNode(
                    location(ctx),
                    ctx.ident().getText(),
                    type,
                    integerLiteralNode,
                    secLevel);
        }
        return new Parser.InputVariableDeclarationNode(
                location(ctx),
                ctx.ident().getText(),
                type,
                integerLiteralNode,
                secLevel);
    }

    @Override
    public Parser.WhileStatementNode visitWhile_statement(LangParser.While_statementContext ctx) {
        List<Parser.VariableAssignmentNode> pres;
        if (ctx.assignments() == null) {
            pres = new ArrayList<>();
        } else {
            pres = accept(ctx.assignments());
        }
        return new Parser.WhileStatementNode(
                location(ctx),
                pres,
                accept(ctx.expression()),
                accept(ctx.statement_w_semi()));
    }

    @Override
    public Parser.IfStatementNode visitIf_statement(LangParser.If_statementContext ctx) {
        return new Parser.IfStatementNode(location(ctx),
                accept(ctx.expression()),
                accept(ctx.block(0)),
                ctx.block().size() < 2 ?
                        new Parser.BlockNode(Location.ZERO, new ArrayList<>()) : accept(ctx.block(1)));
    }

    @Override
    public Parser.ExpressionStatementNode visitExpression_statement(LangParser.Expression_statementContext ctx) {
        return new Parser.ExpressionStatementNode(accept(ctx.expression()));
    }

    @Override
    public Parser.ArrayAssignmentNode visitArray_assignment_statement(LangParser.Array_assignment_statementContext ctx) {
        return new Parser.ArrayAssignmentNode(location(ctx),
                ctx.ident().getText(),
                accept(ctx.expression(0)),
                accept(ctx.expression(1)));
    }

    @Override
    public Parser.ReturnStatementNode visitReturn_statement(LangParser.Return_statementContext ctx) {
        if (ctx.expression() == null || ctx.expression().size() == 0) {
            return new Parser.ReturnStatementNode(location(ctx));
        }
        if (ctx.expression().size() == 1) {
            return new Parser.ReturnStatementNode(location(ctx), (Parser.ExpressionNode)accept(ctx.expression(0)));
        }
        return new Parser.ReturnStatementNode(location(ctx), new Parser.TupleLiteralNode(Location.ZERO,
                ctx.expression().stream().map(e -> (Parser.ExpressionNode)accept(e)).collect(Collectors.toList())));
    }

    @SuppressWarnings("unchecked")
    private <T> T accept(ParserRuleContext ctx) {
        if (ctx == null) return null;
        return (T) ctx.accept(this);
    }

    @SuppressWarnings("unchecked")
    private <T> T accept(ParserRuleContext ctx, ParserRuleContext alt) {
        if (ctx == null) return accept(alt);
        return (T) ctx.accept(this);
    }

    @SuppressWarnings("unchecked")
    private <T> T accept(ParserRuleContext ctx, ParserRuleContext alt, ParserRuleContext alt2) {
        if (ctx == null) return accept(alt, alt2);
        return (T) ctx.accept(this);
    }

    @Override
    public List<Parser.StatementNode> visitStatements(LangParser.StatementsContext ctx) {
        return Stream.concat(ctx.statement_w_semi() != null ? ctx.statement_w_semi()
                .stream().map(s -> (Parser.StatementNode)s.accept(this)) : Stream.empty(),
                ctx.statement_wo_semi() != null ? Stream.of((Parser.StatementNode)ctx.statement_wo_semi().accept(this)) : Stream.empty()).collect(Collectors.toList());
    }

    @Override
    public Parser.MethodNode visitMethod(LangParser.MethodContext ctx) {
        Type type;
        if (ctx.type().size() == 1) {
            type = ((Parser.TypeNode)accept(ctx.type(0))).type;
        } else {
            type = types.getOrCreateTupleType(ctx.type().stream()
                    .map(t -> ((Parser.TypeNode)accept(t)).type).collect(Collectors.toList()));
        }
        return new Parser.MethodNode(
                location(ctx),
                ctx.ident().getText(),
                type,
                ctx.parameters() != null ? accept(ctx.parameters()) : new Parser.ParametersNode(Location.ZERO, new ArrayList<>()),
                accept(ctx.block()),
                ctx.globals() != null ? accept(ctx.globals()) : new Parser.GlobalVariablesNode(Location.ZERO, new HashMap<>()));
    }

    @Override
    public Parser.ParametersNode visitParameters(LangParser.ParametersContext ctx) {
        return new Parser.ParametersNode(location(ctx),
                listOf(ctx.parameter()));
    }

    private <T> List<T> listOf(List<? extends ParserRuleContext> ctxs) {
        return ctxs.stream().map(it -> (T) accept(it)).collect(Collectors.toList());
    }

    @Override
    public Parser.ParameterNode visitParameter(LangParser.ParameterContext ctx) {
        return new Parser.ParameterNode(location(ctx),
                ((Parser.TypeNode)accept(ctx.type())).type, ctx.ident().getText());
    }

    @Override
    public Object visitMultiple_assignment(LangParser.Multiple_assignmentContext ctx) {
        return new Parser.MultipleVariableAssignmentNode(location(ctx), ctx.ident().stream()
                .map(RuleContext::getText).toArray(String[]::new),
                ctx.unpack() != null ? accept(ctx.unpack()) : new Parser.UnpackOperatorNode(accept(ctx.method_invocation())));
    }

    @Override
    public Parser.StatementNode visitSingle_assignment(LangParser.Single_assignmentContext ctx) {
        if (ctx.unpack() == null) {
            return new Parser.VariableAssignmentNode(
                    location(ctx),
                    ctx.ident().getText(),
                    accept(ctx.expression(), ctx.phi()));
        }
        return new Parser.MultipleVariableAssignmentNode(
                location(ctx),
                new String[]{ctx.ident().getText()},
                accept(ctx.unpack()));
    }

    @Override
    public Parser.BlockNode visitBlock(LangParser.BlockContext ctx) {
        return new Parser.BlockNode(location(ctx), ctx.statements() != null ? visitStatements(ctx.statements()) : new ArrayList<>());
    }

    @Override
    public List<Parser.VariableAssignmentNode> visitAssignments(LangParser.AssignmentsContext ctx) {
        return listOf(ctx.assignment());
    }

    @Override
    public Parser.ExpressionNode visitExpression(LangParser.ExpressionContext ctx) {
        if (ctx.primary_expression() != null) {
            return accept(ctx.primary_expression());
        }
        if (ctx.un != null) {
            Parser.ExpressionNode un = accept(ctx.un);
            switch (ctx.op.getType()) {
                case LangLexer.INVERT:
                    return new Parser.UnaryOperatorNode(un, Parser.LexerTerminal.INVERT);
                case LangLexer.MINUS:
                    return new Parser.BinaryOperatorNode(new Parser.IntegerLiteralNode(Location.ZERO, vl.parse(0)), un, Parser.LexerTerminal.MINUS);
                case LangLexer.TILDE:
                    return new Parser.UnaryOperatorNode(un, Parser.LexerTerminal.TILDE);
                case LangLexer.DOT:
                case LangLexer.LBRACKET:
                    long index = vl.parse(ctx.INTEGER_LITERAL().getText()).asLong();
                    Parser.ExpressionNode expr = accept(ctx.un);
                    if (ctx.place_int != null) {
                        return new Parser.BitPlaceOperatorNode(expr, index);
                    } else {
                        return new Parser.BracketedAccessOperatorNode(expr, new Parser.IntegerLiteralNode(Location.ZERO, vl.parse(index)));
                    }
                default:
                    throw new UnsupportedOperationException();
            }
        }
        Parser.ExpressionNode left = accept(ctx.l);
        Parser.ExpressionNode right = accept(ctx.r);
        Parser.LexerTerminal op;
        switch (ctx.op.getType()) {
            case LangLexer.PLUS:
                op = Parser.LexerTerminal.PLUS;
                break;
            case LangLexer.MINUS:
                op = Parser.LexerTerminal.MINUS;
                break;
            case LangLexer.DIVIDE:
                op = Parser.LexerTerminal.DIVIDE;
                break;
            case LangLexer.MULTIPLY:
                op = Parser.LexerTerminal.MULTIPLY;
                break;
            case LangLexer.EQUALS:
            case LangLexer.EQUAL_SIGN:
                op = Parser.LexerTerminal.EQUALS;
                break;
            case LangLexer.UNEQUALS:
                op = Parser.LexerTerminal.UNEQUALS;
                break;
            case LangLexer.LOWER:
                op = Parser.LexerTerminal.LOWER;
                break;
            case LangLexer.LOWER_EQUALS:
                op = Parser.LexerTerminal.LOWER_EQUALS;
                break;
            case LangLexer.GREATER:
                op = Parser.LexerTerminal.GREATER;
                break;
            case LangLexer.GREATER_EQUALS:
                op = Parser.LexerTerminal.GREATER_EQUALS;
                break;
            case LangLexer.AND:
                op = Parser.LexerTerminal.AND;
                break;
            case LangLexer.BAND:
                op = Parser.LexerTerminal.BAND;
                break;
            case LangLexer.OR:
                op = Parser.LexerTerminal.OR;
                break;
            case LangLexer.BOR:
                op = Parser.LexerTerminal.BOR;
                break;
            case LangLexer.XOR:
                op = Parser.LexerTerminal.XOR;
                break;
            case LangLexer.LEFT_SHIFT:
                op = Parser.LexerTerminal.LEFT_SHIFT;
                break;
            case LangLexer.RIGHT_SHIFT:
                op = Parser.LexerTerminal.RIGHT_SHIFT;
                break;
            case LangLexer.LBRACKET:
                return new Parser.BracketedAccessOperatorNode(left, right);
            case LangLexer.MODULO:
                op = Parser.LexerTerminal.MODULO;
                break;
            case LangLexer.APPEND:
                op = Parser.LexerTerminal.APPEND;
                break;
            default:
                throw new UnsupportedOperationException();
        }
        return new Parser.BinaryOperatorNode(left, right, op);
    }

    @Override
    public Parser.ExpressionNode visitPrimary_expression(LangParser.Primary_expressionContext ctx) {
        if (ctx.LPAREN() != null) {
            return accept(ctx.expression());
        }
        return (Parser.ExpressionNode)super.visitPrimary_expression(ctx);
    }

    @Override
    public Object visitMethod_invocation(LangParser.Method_invocationContext ctx) {
        return new Parser.MethodInvocationNode(location(ctx), ctx.name.getText(), visitArguments(ctx.arguments()),
                ctx.globals() == null ? new Parser.GlobalVariablesNode(Location.ZERO, new HashMap<>()) : visitGlobals(ctx.globals()));
    }

    @Override
    public Object visitPhi(LangParser.PhiContext ctx) {
        return new Parser.PhiNode(location(ctx), ctx.ident().stream().map(i -> i.getText()).collect(Collectors.toList()));
    }

    @Override
    public Parser.ArgumentsNode visitArguments(LangParser.ArgumentsContext ctx) {
        return new Parser.ArgumentsNode(location(ctx), listOf(ctx.tuple_element()));
    }

    @Override
    public Object visitUnpack(LangParser.UnpackContext ctx) {
        return new Parser.UnpackOperatorNode((Parser.ExpressionNode)ctx.expression().accept(this));
    }

    @Override
    public Parser.TupleLiteralNode visitTuple_expression(LangParser.Tuple_expressionContext ctx) {
        return new Parser.TupleLiteralNode(location(ctx), visitTuple_inner(ctx.tuple_inner()));
    }

    @Override
    public Parser.ArrayLiteralNode visitArray_expression(LangParser.Array_expressionContext ctx) {
        return new Parser.ArrayLiteralNode(location(ctx), visitTuple_inner(ctx.tuple_inner()));
    }

    @Override
    public List<Parser.ExpressionNode> visitTuple_inner(LangParser.Tuple_innerContext ctx) {
        return listOf(ctx.tuple_element());
    }

    @Override
    public Parser.ExpressionNode visitTuple_element(LangParser.Tuple_elementContext ctx) {
        return accept(ctx.unpack(), ctx.expression());
    }

    @Override
    public Parser.VariableAccessNode visitVar_access(LangParser.Var_accessContext ctx) {
        return new Parser.VariableAccessNode(location(ctx), ctx.getText());
    }

    @Override
    public Object visitLiteral(LangParser.LiteralContext ctx) {
        if (ctx.TRUE() != null) {
            return new Parser.IntegerLiteralNode(location(ctx), vl.parse(1));
        }
        if (ctx.FALSE() != null) {
            return new Parser.IntegerLiteralNode(location(ctx), vl.parse(0));
        }
        Lattices.Value val = Lattices.ValueLattice.get().parse(ctx.getText());
        return new Parser.IntegerLiteralNode(location(ctx), val);
    }

    @Override
    public Parser.GlobalVariablesNode visitGlobals(LangParser.GlobalsContext ctx) {
        return new Parser.GlobalVariablesNode(location(ctx), ctx.global().stream().map(g -> (List<String>)g.accept(this))
                .collect(Collectors.toMap(l -> l.get(0), l -> p(l.get(1), l.get(2)))));
    }

    @Override
    public List<String> visitGlobal(LangParser.GlobalContext ctx) {
        return listOf(ctx.ident());
    }

    @Override
    public String visitIdent(LangParser.IdentContext ctx) {
        return ctx.getText();
    }

    @Override
    public Parser.TypeNode visitBaseTypeInt(LangParser.BaseTypeIntContext ctx) {
        return new Parser.TypeNode(location(ctx), ctx.getText().equals("aint") ? types.AINT : types.INT);
    }

    @Override
    public Parser.TypeNode visitArray_type(LangParser.Array_typeContext ctx) {
        Type subType = ((Parser.TypeNode)ctx.type().accept(this)).type;
        int length = (int)vl.parse(ctx.INTEGER_LITERAL().getText()).asLong();
        return new Parser.TypeNode(location(ctx),
                types.getOrCreateFixedArrayType(subType, Collections.singletonList(length)));
    }

    @Override
    public Parser.TypeNode visitTuple_type(LangParser.Tuple_typeContext ctx) {
        return new Parser.TypeNode(location(ctx), types.getOrCreateTupleType(ctx.type().stream()
                .map(t -> (Parser.TypeNode)t.accept(this))
                .map(t -> t.type).collect(Collectors.toList())));
    }

    @Override
    public Parser.TypeNode visitBaseTypeVar(LangParser.BaseTypeVarContext ctx) {
        return new Parser.TypeNode(location(ctx), types.get(ctx.getText()));
    }
}