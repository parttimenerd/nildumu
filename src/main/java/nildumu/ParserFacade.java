package nildumu;

import nildumu.parser.LangBaseVisitor;
import nildumu.parser.LangLexer;
import nildumu.parser.LangParser;
import nildumu.typing.Type;
import nildumu.typing.Types;
import org.antlr.v4.runtime.*;
import swp.lexer.Location;
import swp.parser.lr.CustomAST;
import swp.parser.lr.ListAST;
import swp.util.Utils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static nildumu.Lattices.vl;
import static nildumu.util.Util.p;

/**
 * @author Alexander Weigl
 * @version 1 (4/14/21)
 */
public class ParserFacade {
    public Lexer createLexer(CharStream stream) {
        nildumu.parser.LangLexer lexer = new LangLexer(stream);
        lexer.removeErrorListeners();
        lexer.addErrorListener(new ExceptionErrorListener());
        return lexer;
    }

    public LangParser createParser(CharStream stream) {
        LangParser parser = new LangParser(new CommonTokenStream(createLexer(stream)));
        parser.removeErrorListeners();
        parser.addErrorListener(new ExceptionErrorListener());
        return parser;
    }

    public Object parseFile(File file) throws IOException {
        LangParser parser = createParser(CharStreams.fromFileName(file.getAbsolutePath()));
        LangParser.FileContext ctx = parser.file();
        return ctx.accept(new Translator());
    }

}

class ExceptionErrorListener extends BaseErrorListener {
    @Override
    public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
        super.syntaxError(recognizer, offendingSymbol, line, charPositionInLine, msg, e);
        throw new RuntimeException(msg);
    }
}

class Translator extends LangBaseVisitor<Object> {
    @Override
    public Parser.ProgramNode visitFile(LangParser.FileContext ctx) {
        Lattices.SecurityLattice<?> secLattice =
                ctx.use_sec() == null
                        ? Lattices.BasicSecLattice.get()
                        : ((ListAST<Parser.WrapperNode<Lattices.SecurityLattice<?>>>) ctx.use_sec().accept(this));

        int declaredBitWidth = ctx.bit_width() == null ? vl.bitWidth : ctx.bit_width().accept(this);
        List<Parser.MJNode> topLevelNodes = asts.get(2).<Parser.WrapperNode<List<Parser.MJNode>>>as().wrapped;
        Parser.ProgramNode node = new Parser.ProgramNode(new Context(secLattice, vl.bitWidth), types);
        Parser.NodeVisitor visitor = new Parser.NodeVisitor<Object>() {

            @Override
            public Object visit(Parser.MJNode node) {
                return null;
            }

            @Override
            public Object visit(Parser.MethodNode method) {
                node.addMethod(method);
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
        topLevelNodes.forEach(n -> n.accept(visitor));
        node.handleInputAndPrint();
        return node;
    }

    @Override
    public Object visitUse_sec(LangParser.Use_secContext ctx) {
        return new Parser.WrapperNode<>(location(ctx),
                Lattices.SecurityLattice.forName(ctx.getText()));
    }

    private Location location(ParserRuleContext ctx) {
        return null;
    }

    @Override
    public Object visitBit_width(LangParser.Bit_widthContext ctx) {
        vl.bitWidth = Integer.parseInt(ctx.INTEGER_LITERAL().getText());
        return new Parser.WrapperNode<>(location(ctx), vl.bitWidth);
    }

    @Override
    public Object visitBlock_statement(LangParser.Block_statementContext ctx) {
        return new Parser.BlockNode(location(ctx),
                accept(ctx.block()));
        //asts.get(1).<Parser.WrapperNode<List<Parser.StatementNode>>>as().wrapped));
    }

    final Types types = new Types();

    @Override
    public Object visitVardecl(LangParser.VardeclContext ctx) {
        Type type = accept(ctx.type());
        return new Parser.VariableDeclarationNode(
                ctx.IDENT().get(0).getText(),
                type == types.AINT ? types.INT : type,
                (Parser.ExpressionNode) accept(ctx.expression()),
                type == types.AINT);
    }

    @Override
    public Object visitWhile_statement(LangParser.While_statementContext ctx) {
        List<Parser.VariableAssignmentNode> pres;
        if (ctx.assignments() == null)
            pres = new ArrayList<>();
        else
            pres = (ListAST) accept(ctx.assignments());
        return new Parser.WhileStatementNode(
                location(ctx),
                pres,
                accept(ctx.expression()),
                accept(ctx.statement()));
    }

    @Override
    public Object visitIf_statement(LangParser.If_statementContext ctx) {
        return new Parser.IfStatementNode(location(ctx),
                accept(ctx.expression()),
                accept(ctx.statements(0)),
                accept(ctx.statements(1)));
    }

    @Override
    public Object visitExpression_statement(LangParser.Expression_statementContext ctx) {
        return new Parser.ExpressionStatementNode(accept(ctx.expression()));
    }

    @Override
    public Object visitArray_assignment_statement(LangParser.Array_assignment_statementContext ctx) {
        return new Parser.ArrayAssignmentNode(location(ctx),
                ctx.ident().getText(),
                accept(ctx.expression(0)),
                accept(ctx.expression(1)));
    }

    @Override
    public Object visitReturn_statement(LangParser.Return_statementContext ctx) {
        return new Parser.ReturnStatementNode(location(ctx),
                (Parser.ExpressionNode) accept(ctx));
    }

    @SuppressWarnings("unchecked")
    private <T> T accept(ParserRuleContext ctx) {
        if (ctx == null) return null;
        return (T) ctx.accept(this);
    }

    @Override
    public Object visitStatements(LangParser.StatementsContext ctx) {
        Parser.WrapperNode<List<Parser.StatementNode>> left = (Parser.WrapperNode<List<Parser.StatementNode>>) asts.get(1);
        Parser.StatementNode right = (Parser.StatementNode) asts.getAs(0);
        left.wrapped.add(0, right);
        return left;
    }

    @Override
    public Object visitMethod(LangParser.MethodContext ctx) {
        return new Parser.MethodNode(
                location(ctx),
                ctx.ident().getText(),
                accept(ctx.type()),
                accept(ctx.parameters()),
                accept(ctx.block()),
                accept(ctx.globals()));
    }

}

    @Override
    public Object visitParameters(LangParser.ParametersContext ctx) {
        Parser.ParametersNode node = new Parser.ParametersNode(location(ctx),
                listOf(ctx.parameter()));
        return node;
    }

    private <T> List<T> listOf(List<? extends ParserRuleContext> ctxs) {
        return ctxs.stream().map(it -> (T) accept(it)).collect(Collectors.toList());
    }

    @Override
    public Object visitParameter(LangParser.ParameterContext ctx) {
        return new Parser.ParameterNode(location(ctx),
                accept(ctx.type()), ctx.ident().getText());
    }

    @Override
    public Object visitAssignment(LangParser.AssignmentContext ctx) {
        return new Parser.VariableAssignmentNode(
                location(ctx),
                ctx.ident(0).getText(),
                accept(ctx.expression()));
    }

    @Override
    public Object visitBlock(LangParser.BlockContext ctx) {
        return super.visitBlock(ctx);
    }

    @Override
    public Object visitIdents(LangParser.IdentsContext ctx) {
        return new CustomAST<List<String>>
                (Stream.concat(Stream.of(asts.get(0).getMatchedString()),
                ((CustomAST<List<String>>) asts.get(2)).value.stream()).collect(Collectors.toList()));
    }

    @Override
    public Object visitAssignments(LangParser.AssignmentsContext ctx) {
        return super.visitAssignments(ctx);
    }

    @Override
    public Object visitExpression(LangParser.ExpressionContext ctx) {
        return super.visitExpression(ctx);
    }

    @Override
    public Object visitPrimary_expression(LangParser.Primary_expressionContext ctx) {
        return super.visitPrimary_expression(ctx);
    }

    @Override
    public Object visitMethod_invocation(LangParser.Method_invocationContext ctx) {
        return super.visitMethod_invocation(ctx);
    }

    @Override
    public Object visitPhi(LangParser.PhiContext ctx) {
        return super.visitPhi(ctx);
    }

    @Override
    public Object visitArguments(LangParser.ArgumentsContext ctx) {
        return super.visitArguments(ctx);
    }

    @Override
    public Object visitUnpack(LangParser.UnpackContext ctx) {
        return super.visitUnpack(ctx);
    }

    @Override
    public Object visitTuple_expression(LangParser.Tuple_expressionContext ctx) {
        return super.visitTuple_expression(ctx);
    }

    @Override
    public Object visitArray_expression(LangParser.Array_expressionContext ctx) {
        return super.visitArray_expression(ctx);
    }

    @Override
    public Object visitTuple_inner(LangParser.Tuple_innerContext ctx) {
        return super.visitTuple_inner(ctx);
    }

    @Override
    public Object visitTuple_element(LangParser.Tuple_elementContext ctx) {
        return super.visitTuple_element(ctx);
    }

    @Override
    public Object visitVar_access(LangParser.Var_accessContext ctx) {
        return super.visitVar_access(ctx);
    }

    @Override
    public Object visitLiteral(LangParser.LiteralContext ctx) {
        Lattices.Value val = Lattices.ValueLattice.get().parse(asts.getMatchedString());
        statedBitWidth.val = Math.max(statedBitWidth.val, val.size());
        return new Parser.IntegerLiteralNode(asts.getStartLocation(), val);
    }

    @Override
    public Object visitGlobals(LangParser.GlobalsContext ctx) {
        Utils.Triple<String, String, String> glob = asts.get(0).<Parser.WrapperNode<Utils.Triple<String, String, String>>>as().wrapped;
        Parser.GlobalVariablesNode globalNode = ((Parser.GlobalVariablesNode) asts.get(2));
        globalNode.globalVarSSAVars.put(glob.first, p(glob.second, glob.third));
        return new Parser.GlobalVariablesNode(((Parser.WrapperNode<?>) asts.get(0)).location, globalNode.globalVarSSAVars);
    }

    @Override
    public Object visitGlobals_(LangParser.Globals_Context ctx) {
        new Parser.GlobalVariablesNode(new Location(0, 0), new HashMap<>())

    }

    @Override
    public Object visitGlobal(LangParser.GlobalContext ctx) {
        return new Parser.WrapperNode<>(asts.getStartLocation(),
                new Utils.Triple<>(asts.get(0).getMatchedString(), asts.get(2).getMatchedString(), asts.get(4).getMatchedString()));
    }

    @Override
    public Object visitIdent(LangParser.IdentContext ctx) {
        return ctx.getText();
    }

    @Override
    public Object visitBaseTypeInt(LangParser.BaseTypeIntContext ctx) {
        return super.visitBaseTypeInt(ctx);
        if (!types.containsKey(type)) {
            throw new NildumuError(String.format("No such type %s", type));
        }
        return new Parser.TypeNode(asts.getStartLocation(), types.get(type));
        new Parser.TypeNode(asts.getStartLocation(),
                types.INT)
    }

    @Override
    public Object visitArray_type(LangParser.Array_typeContext ctx) {
        Type subType = asts.get(0).<Parser.TypeNode>as().type;
        int length = (int)vl.parse(asts.get(2).getMatchedString()).asLong();
        return new Parser.TypeNode(asts.get(0).<Parser.MJNode>as().location,
                types.getOrCreateFixedArrayType(subType, Collections.singletonList(length)));
    }

    @Override
    public Object visitTuple_type(LangParser.Tuple_typeContext ctx) {
        List<Type> elementTypes = (List<Type>) asts.getAll("type").stream().map(a -> ((Parser.TypeNode) a).type).collect(Collectors.toList());
        return new Parser.TypeNode(asts.getStartLocation(), types.getOrCreateTupleType(elementTypes));

    }
}