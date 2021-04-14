package nildumu;

import nildumu.parser.LangBaseListener;
import nildumu.parser.LangBaseVisitor;
import nildumu.parser.LangLexer;
import nildumu.parser.LangParser;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import swp.parser.lr.ListAST;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static nildumu.Lattices.vl;

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
        return new Parser.WrapperNode<>(asts.getStartLocation(), Lattices.SecurityLattice.forName(asts.get(1).getMatchedString()));
    }

    @Override
    public Integer visitBit_width(LangParser.Bit_widthContext ctx) {
        vl.bitWidth = Integer.parseInt(asts.get(1).getMatchedString());
        return new Parser.WrapperNode<>(asts.getStartLocation(), vl.bitWidth);
    }

    @Override
    public Object visitBlock_statement(LangParser.Block_statementContext ctx) {
        return super.visitBlock_statement(ctx);
    }

    @Override
    public Object visitVardecl(LangParser.VardeclContext ctx) {
        return super.visitVardecl(ctx);
    }

    @Override
    public Object visitZIGNORE(LangParser.ZIGNOREContext ctx) {
        return super.visitZIGNORE(ctx);
    }

    @Override
    public Object visitWhile_statement(LangParser.While_statementContext ctx) {
        return super.visitWhile_statement(ctx);
    }

    @Override
    public Object visitIf_statement(LangParser.If_statementContext ctx) {
        return super.visitIf_statement(ctx);
    }

    @Override
    public Object visitExpression_statement(LangParser.Expression_statementContext ctx) {
        return super.visitExpression_statement(ctx);
    }

    @Override
    public Object visitArray_assignment_statement(LangParser.Array_assignment_statementContext ctx) {
        return super.visitArray_assignment_statement(ctx);
    }

    @Override
    public Object visitReturn_statement(LangParser.Return_statementContext ctx) {
        return super.visitReturn_statement(ctx);
    }

    @Override
    public Object visitStatements(LangParser.StatementsContext ctx) {
        return super.visitStatements(ctx);
    }

    @Override
    public Object visitMethod(LangParser.MethodContext ctx) {
        return super.visitMethod(ctx);
    }

    @Override
    public Object visitParameters(LangParser.ParametersContext ctx) {
        return super.visitParameters(ctx);
    }

    @Override
    public Object visitParameter(LangParser.ParameterContext ctx) {
        return super.visitParameter(ctx);
    }

    @Override
    public Object visitAssignment(LangParser.AssignmentContext ctx) {
        return super.visitAssignment(ctx);
    }

    @Override
    public Object visitBlock(LangParser.BlockContext ctx) {
        return super.visitBlock(ctx);
    }

    @Override
    public Object visitIdents(LangParser.IdentsContext ctx) {
        return super.visitIdents(ctx);
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
        return super.visitLiteral(ctx);
    }

    @Override
    public Object visitGlobals(LangParser.GlobalsContext ctx) {
        return super.visitGlobals(ctx);
    }

    @Override
    public Object visitGlobals_(LangParser.Globals_Context ctx) {
        return super.visitGlobals_(ctx);
    }

    @Override
    public Object visitGlobal(LangParser.GlobalContext ctx) {
        return super.visitGlobal(ctx);
    }

    @Override
    public Object visitIdent(LangParser.IdentContext ctx) {
        return super.visitIdent(ctx);
    }

    @Override
    public Object visitBaseTypeInt(LangParser.BaseTypeIntContext ctx) {
        return super.visitBaseTypeInt(ctx);
    }

    @Override
    public Object visitArray_type(LangParser.Array_typeContext ctx) {
        return super.visitArray_type(ctx);
    }

    @Override
    public Object visitTuple_type(LangParser.Tuple_typeContext ctx) {
        return super.visitTuple_type(ctx);
    }
}