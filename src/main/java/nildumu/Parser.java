package nildumu;

import nildumu.typing.Type;
import nildumu.typing.Types;
import nildumu.util.Util;
import swp.SWPException;
import swp.lexer.Lexer;
import swp.lexer.Location;
import swp.lexer.Token;
import swp.parser.lr.BaseAST;
import swp.parser.lr.CustomAST;
import swp.parser.lr.Generator;
import swp.parser.lr.ListAST;
import swp.util.Pair;
import swp.util.Utils;

import java.io.Serializable;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static nildumu.Lattices.*;
import static nildumu.Parser.LexerTerminal.*;
import static nildumu.util.Util.enumerate;
import static nildumu.util.Util.p;

/**
 * Parser and AST for a basic Java based language that has only integer as a data type.
 * It is basically a while language with basic functions.
 * <p/>
 * The biggest difference to the normal while language is, that the syntax for integers is different:<br/>
 * <ul>
 *     <li>normal literal = a normal fully public integer</li>
 *     <li>input literal = "0b" + a list of bits, a bit is either "u" (variable), "x" (don't care), "0" or "1" (constant),
 *     a bit can be followed by "{n}" where n is the number of repeats (e.g. "0bu{3}" is equal to "0buuu")</li>
 * </ul>
 */
public class Parser implements Serializable {

    /**
     * change this to switch between the ANTLR based parser ({@link ParserFacade})
     * and the custom LR parser
     */
    public static final boolean use_antlr = true;

    /**
     * The terminals with the matching regular expression
     */
    public enum LexerTerminal implements Generator.LexerTerminalEnum {
        EOF(""),
        COMMENT("/\\*([^*\\n]*(\\*[^/\\n])?)*\\*/"),
        WS("[\\s\\t]"),
        LBRK("[\\r\\n][\\r\\n]+"),
        INPUT("input"),
        TMP_INPUT("tmp_input"),
        OUTPUT("output"),
        APPEND_ONLY("append_only"),
        INT("[a]?int"),
        VAR("var"),
        RETURN("return"),
        IF("if"),
        WHILE("while"),
        ELSE("else"),
        TRUE("true"),
        FALSE("false"),
        VOID("void"),
        USE_SEC("use_sec"),
        BIT_WIDTH("bit_width"),
        PHI("phi"),
        TILDE("~"),

        LOWER_EQUALS("<="),
        GREATER_EQUALS(">="),
        MODULO("%"),

        PLUS("\\+", "+"),
        MINUS("\\-", "-"),
        DIVIDE("/"),
        MULTIPLY("\\*", "*"),
        EQUAL_SIGN("="),
        EQUALS("=="),
        UNEQUALS("!="),
        INVERT("!"),
        LOWER("<"),
        GREATER(">"),
        AND("&&"),
        OR("\\|\\|"),
        BAND("&"),
        BOR("\\|", "|"),
        XOR("\\^", "^"),
        LEFT_SHIFT("<<"),
        RIGHT_SHIFT(">>"),
        LPAREN("\\("),
        RPAREN("\\)"),
        LBRACKET("\\["),
        LBBRACKET("\\[\\["),
        RBRACKET("\\]"),
        RBBRACKET("\\]\\]"),
        ARROW("\\->", "->"),
        APPEND("@"),
        SEMICOLON("(\\;|\\n)+", ";"),
        INTEGER_LITERAL("(([1-9][0-9]*)|0)|(0b([01e]((\\{([0-9]+)\\})?))*)|(\\-([1-9][0-9]*)|(\\-0))"),
        INPUT_LITERAL("(0b([01ux]((\\{([0-9]+)\\})?))+)"),
        IDENT("[A-Za-z_][A-Za-z0-9_]*"),
        LCURLY("\\{"),
        RCURLY("\\}"),
        COLON(":"),
        COMMA("[,]", ","),
        DOT("\\.", "."),
        BIT_INDEX(""),
        BIT_SELECT_OP(""),
        BIT_PLACE_OP(""),
        BRACKET_ACCESS_OP("");

        private final String description;
        private final String representation;

        LexerTerminal(String description) {
            this(description, description);
        }

        LexerTerminal(String description, String representation) {
            this.description = description;
            this.representation = representation;
        }

        @Override
        public String getTerminalDescription() {
            return description;
        }

        private static final LexerTerminal[] terminals = values();

        static LexerTerminal valueOf(int id) {
            return terminals[id];
        }

        public String getRepresentation() {
            return representation;
        }
    }

    /**
     * The parser generator that loads a version of the parser and lexer from disc if possible.
     * Change the id, when changing the parser oder replace the id by {@code null} to build the parser and lexer
     * every time (takes long)
     */
    public static Generator generator;
    static {
        if (!use_antlr)
            generator = Generator.getCachedIfPossible("./parser/3", LexerTerminal.class, new String[]{"WS", "COMMENT", "LBRK"},
                    (builder) -> {
                        Util.Box<Integer> statedBitWidth = new Util.Box<>(2);
                        Types types = new Types();
                        builder.addRule("program", "use_sec? bit_width lines", asts -> {
                            SecurityLattice<?> secLattice = asts.get(0).children().isEmpty() ? BasicSecLattice.get() : ((ListAST<WrapperNode<SecurityLattice<?>>>) asts.get(0)).get(0).wrapped;
                            int declaredBitWidth = vl.bitWidth;
                            /*
                             * Calc bit width
                             */
                            List<MJNode> topLevelNodes = asts.get(2).<WrapperNode<List<MJNode>>>as().wrapped;
                            ProgramNode node = new ProgramNode(new Context(secLattice, vl.bitWidth), types);
                            NodeVisitor visitor = new NodeVisitor<Object>() {

                                @Override
                                public Object visit(MJNode node) {
                                    return null;
                                }

                                @Override
                                public Object visit(MethodNode method) {
                                    node.addMethod(method);
                                    return null;
                                }

                                @Override
                                public Object visit(StatementNode statement) {
                                    node.addGlobalStatement(statement);
                                    return null;
                                }

                                @Override
                                public Object visit(InputVariableDeclarationNode inputDecl) {
                                    node.context.addInputValue(secLattice.parse(inputDecl.secLevel),
                                            inputDecl.expression, ((IntegerLiteralNode) inputDecl.expression).value);
                                    visit((StatementNode) inputDecl);
                                    return null;
                                }

                                @Override
                                public Object visit(AppendOnlyVariableDeclarationNode appendDecl) {
                                    node.context.addAppendOnlyVariable(secLattice.parse(appendDecl.secLevel), appendDecl.variable);
                                    visit((StatementNode) appendDecl);
                                    return null;
                                }
                            };
                            topLevelNodes.forEach(n -> n.accept(visitor));
                            node.handleInputAndPrint();
                            return node;
                        })
                                .addRule("use_sec", "USE_SEC IDENT SEMICOLON", asts -> {
                                    return new WrapperNode<>(asts.getStartLocation(), SecurityLattice.forName(asts.get(1).getMatchedString()));
                                })
                                .addRule("bit_width", "BIT_WIDTH INTEGER_LITERAL SEMICOLON", asts -> {
                                    vl.bitWidth = Integer.parseInt(asts.get(1).getMatchedString());
                                    return new WrapperNode<>(asts.getStartLocation(), vl.bitWidth);
                                })
                                .addRule("bit_width", "", asts -> {
                                    vl.bitWidth = 32;
                                    return new WrapperNode<>(asts.getStartLocation(), 32);
                                })
                                .addRule("lines", "line_w_semi lines", asts -> {
                                    WrapperNode<List<MJNode>> left = (WrapperNode<List<MJNode>>) asts.get(1);
                                    MJNode right = (MJNode) asts.getAs(0);
                                    left.wrapped.add(0, right);
                                    return left;
                                })
                                .addRule("lines", "line", asts -> {
                                    return new WrapperNode<>(((MJNode) asts.get(0)).location, new ArrayList<>(Collections.singleton(asts.get(0))));
                                })
                                .addRule("lines", "", asts -> {
                                    return new WrapperNode<>(new Location(0, 0), new ArrayList<>());
                                })
                                .addRule("line_w_semi", "method")
                                .addRule("line_w_semi", "block_statement_w_semi")
                                .addRule("line_w_semi", "output_decl_statement SEMICOLON", asts -> asts.get(0))
                                .addRule("line_w_semi", "input_decl_statement SEMICOLON", asts -> asts.get(0))
                                .addRule("line_w_semi", "append_decl_statement SEMICOLON", asts -> asts.get(0))
                                .addRule("line", "method")
                                .addRule("line", "block_statement")
                                .addRule("line", "output_decl_statement")
                                .addRule("line", "input_decl_statement")
                                .addRule("line", "append_decl_statement")
                                .addRule("output_decl_statement", "IDENT OUTPUT type IDENT (EQUAL_SIGN expression)?", asts -> {
                                    return new OutputVariableDeclarationNode(
                                            asts.get(0).getMatchedTokens().get(0).location,
                                            asts.get(3).getMatchedString(),
                                            ((TypeNode) asts.get(2)).type,
                                            (ExpressionNode) ((ListAST) asts.get(4)).getAll(ExpressionNode.class).stream().findAny().orElse(null),
                                            asts.get(0).getMatchedString());
                                })
                                .addRule("input_decl_statement", "IDENT INPUT type IDENT EQUAL_SIGN input_literal", asts -> {
                                    return new InputVariableDeclarationNode(
                                            asts.getStartLocation(),
                                            asts.get(3).getMatchedString(),
                                            ((TypeNode) asts.get(2)).type,
                                            (IntegerLiteralNode) asts.get(5),
                                            asts.get(0).getMatchedString());
                                })
                                .addRule("tmp_input_decl_statement", "IDENT TMP_INPUT INT IDENT EQUAL_SIGN input_literal", asts -> {
                                    return new TmpInputVariableDeclarationNode(
                                            asts.getStartLocation(),
                                            asts.get(3).getMatchedString(),
                                            types.INT,
                                            (IntegerLiteralNode) asts.get(5),
                                            asts.get(0).getMatchedString());
                                })
                                .addRule("append_decl_statement", "IDENT APPEND_ONLY INT ident", asts -> {
                                    return new AppendOnlyVariableDeclarationNode(
                                            asts.getStartLocation(),
                                            asts.get(3).getMatchedString(),
                                            types.INT,
                                            asts.get(0).getMatchedString());
                                })
                                .addRule("append_decl_statement", "IDENT APPEND_ONLY INPUT INT ident", asts -> {
                                    return new AppendOnlyVariableDeclarationNode(
                                            asts.getStartLocation(),
                                            asts.get(4).getMatchedString(),
                                            types.INT,
                                            asts.get(0).getMatchedString(),
                                            true);
                                })
                                .addRule("method", "type ident LPAREN parameters RPAREN method_body", asts -> {
                                    return new MethodNode(asts.get(0).<MJNode>as().location,
                                            asts.get(1).getMatchedString(),
                                            ((TypeNode) asts.get(0)).type,
                                            (ParametersNode) asts.get(3), (BlockNode) asts.get(5),
                                            new GlobalVariablesNode(new Location(0, 0), new HashMap<>()));
                                })
                                .addRule("method", "type ident globals LPAREN parameters RPAREN method_body", asts -> {
                                    return new MethodNode(asts.get(0).<MJNode>as().location,
                                            asts.get(1).getMatchedString(),
                                            ((TypeNode) asts.get(0)).type,
                                            (ParametersNode) asts.get(4), (BlockNode) asts.get(6),
                                            (GlobalVariablesNode) asts.get(2));
                                })
                                .addRule("parameters", "", asts -> new ParametersNode(new Location(0, 0), new ArrayList<>()))
                                .addRule("parameters", "parameter COMMA parameters", asts -> {
                                    ParameterNode param = (ParameterNode) asts.get(0);
                                    ParametersNode node = new ParametersNode(param.location, Utils.makeArrayList(param));
                                    node.parameterNodes.addAll(((ParametersNode) asts.get(2)).parameterNodes);
                                    return node;
                                })
                                .addRule("parameters", "parameter", asts -> {
                                    ParameterNode param = (ParameterNode) asts.get(0);
                                    return new ParametersNode(param.location, Utils.makeArrayList(param));
                                })
                                .addRule("parameter", "type ident", asts -> {
                                    return new ParameterNode(asts.get(0).<MJNode>as().location, ((TypeNode) asts.get(0)).type,
                                            asts.get(1).getMatchedString());
                                })
                                .addEitherRule("statement", "block")
                                .addRule("expression_statement", "expression", asts -> {
                                    return new ExpressionStatementNode((ExpressionNode) asts.get(0));
                                })
                                .addRule("block", "LCURLY block_statements RCURLY", asts -> new BlockNode(asts.get(0).getMatchedTokens().get(0).location, asts.get(1).<WrapperNode<List<StatementNode>>>as().wrapped))
                                .addRule("method_body", "LCURLY method_block_statements RCURLY", asts -> new BlockNode(asts.get(0).getMatchedTokens().get(0).location, asts.get(1).<WrapperNode<List<StatementNode>>>as().wrapped))
                                .addRule("block_statements", "block_statement_w_semi block_statements", asts -> {
                                    WrapperNode<List<StatementNode>> left = (WrapperNode<List<StatementNode>>) asts.get(1);
                                    StatementNode right = (StatementNode) asts.getAs(0);
                                    left.wrapped.add(0, right);
                                    return left;
                                })
                                .addRule("block_statements", "block_statement", asts -> {
                                    return new WrapperNode<>(((MJNode) asts.get(0)).location, new ArrayList<>(Collections.singleton(asts.get(0))));
                                })
                                .addRule("block_statements", "", asts -> {
                                    return new WrapperNode<>(new Location(0, 0), new ArrayList<>());
                                })
                                .addRule("method_block_statements", "block_statement_w_semi method_block_statements", asts -> {
                                    WrapperNode<List<StatementNode>> left = (WrapperNode<List<StatementNode>>) asts.get(1);
                                    StatementNode right = (StatementNode) asts.getAs(0);
                                    left.wrapped.add(0, right);
                                    return left;
                                })
                                .addRule("method_block_statements", "block_statement", asts -> {
                                    return new WrapperNode<>(((MJNode) asts.get(0)).location, new ArrayList<>(Collections.singleton(asts.get(0))));
                                })
                                .addRule("method_block_statements", "return_statement SEMICOLON?", asts -> {
                                    return new WrapperNode<>(((MJNode) asts.get(0)).location, new ArrayList<>(Collections.singleton(asts.get(0))));
                                })
                                .addRule("method_block_statements", "", asts -> {
                                    return new WrapperNode<>(new Location(0, 0), new ArrayList<>());
                                })
                                .addRule("block_statement_w_semi", "statement SEMICOLON", asts -> asts.get(0))
                                .addRule("block_statement_w_semi", "var_decl SEMICOLON", asts -> asts.get(0))
                                .addRule("block_statement_w_semi", "local_variable_assignment_statement SEMICOLON", asts -> asts.get(0))
                                .addRule("block_statement_w_semi", "while_statement")
                                .addRule("block_statement_w_semi", "if_statement")
                                .addRule("block_statement_w_semi", "expression_statement SEMICOLON", asts -> asts.get(0))
                                .addRule("block_statement_w_semi", "print_statement SEMICOLON", asts -> asts.get(0))
                                .addRule("block_statement_w_semi", "input_statement SEMICOLON", asts -> asts.get(0))
                                .addRule("block_statement_w_semi", "tmp_input_decl_statement SEMICOLON", asts -> asts.get(0))
                                .addRule("block_statement_w_semi", "array_assignment_statement SEMICOLON", asts -> asts.get(0))
                                //.addRule("block_statement_w_semi", "assert_statement SEMICOLON", asts -> asts.get(0))
                                .addRule("block_statement_w_semi", "block")
                                .addRule("block_statement", "statement", asts -> asts.get(0))
                                .addRule("block_statement", "var_decl", asts -> asts.get(0))
                                .addRule("block_statement", "local_variable_assignment_statement", asts -> asts.get(0))
                                .addRule("block_statement", "while_statement")
                                .addRule("block_statement", "if_statement")
                                .addRule("block_statement", "expression_statement")
                                .addRule("block_statement", "print_statement", asts -> asts.get(0))
                                .addRule("block_statement", "input_statement")
                                .addRule("block_statement", "tmp_input_decl_statement")
                                .addRule("block_statement", "array_assignment_statement")
                                //.addRule("block_statement", "assert_statement")
                                .addRule("block_statement", "block")
                                .addRule("var_decl", "type ident", asts -> {
                                    Type type = ((TypeNode) asts.get(0)).type;
                                    return new VariableDeclarationNode(
                                            asts.get(0).<MJNode>as().location,
                                            asts.get(1).getMatchedString(),
                                            type == types.AINT ? types.INT : type,
                                            null,
                                            type == types.AINT);
                                })
                                .addRule("var_decl", "type ident EQUAL_SIGN (phi|expression)", asts -> {
                                    Type type = ((TypeNode) asts.get(0)).type;
                                    return new VariableDeclarationNode(
                                            asts.get(0).<MJNode>as().location,
                                            asts.get(1).getMatchedString(),
                                            type == types.AINT ? types.INT : type,
                                            (ExpressionNode) asts.get(3),
                                            type == types.AINT);
                                })
                                .addRule("local_variable_assignment_statement", "ident EQUAL_SIGN (phi|expression)", asts -> {
                                    return new VariableAssignmentNode(
                                            asts.getStartLocation(),
                                            asts.get(0).getMatchedString(),
                                            (ExpressionNode) asts.get(2));
                                })
                                .addRule("local_variable_assignment_statement", "ident EQUAL_SIGN unpack", asts -> {
                                    return new MultipleVariableAssignmentNode(
                                            asts.getStartLocation(),
                                            new String[]{asts.get(0).getMatchedString()},
                                            (UnpackOperatorNode) asts.get(2));
                                })
                                .addRule("local_variable_assignment_statement", "ident COMMA idents EQUAL_SIGN unpack", asts -> {
                                    return new MultipleVariableAssignmentNode(
                                            asts.getStartLocation(),
                                            Stream.concat(Stream.of(asts.get(0).getMatchedString()),
                                                    ((CustomAST<List<String>>) asts.get(2)).value.stream())
                                                    .collect(Collectors.toList()).toArray(new String[0]),
                                            (UnpackOperatorNode) asts.get(4));
                                })
                                .addRule("idents", "ident", asts -> {
                                    return new CustomAST<>(Collections.singletonList(asts.get(0).getMatchedString()));
                                })
                                .addRule("idents", "ident COMMA idents", asts -> {
                                    return new CustomAST<List<String>>(Stream.concat(Stream.of(asts.get(0).getMatchedString()),
                                            ((CustomAST<List<String>>) asts.get(2)).value.stream()).collect(Collectors.toList()));
                                })
                                .addRule("array_assignment_statement", "ident LBRACKET expression RBRACKET EQUAL_SIGN expression", asts -> {
                                    return new ArrayAssignmentNode(asts.getStartLocation(),
                                            asts.get(0).getMatchedString(), asts.get(2).<ExpressionNode>as(),
                                            asts.get(5).<ExpressionNode>as());
                                })
                                .addRule("while_statement", "WHILE (LBBRACKET assignments RBBRACKET)? LPAREN expression RPAREN block_statement", asts -> {
                                    ListAST pres = (ListAST) asts.get(1);
                                    return new WhileStatementNode(
                                            asts.getStartLocation(),
                                            pres.isEmpty() ? new ArrayList<>() :
                                                    ((WrapperNode<List<VariableAssignmentNode>>) pres.get(1)).wrapped,
                                            (ExpressionNode) asts.get(3),
                                            (StatementNode) asts.get(5));
                                })
                                .addRule("assignments", "local_variable_assignment_statement SEMICOLON assignments SEMICOLON?", asts -> {
                                    WrapperNode<List<VariableAssignmentNode>> left =
                                            (WrapperNode<List<VariableAssignmentNode>>) asts.get(2);
                                    VariableAssignmentNode right = (VariableAssignmentNode) asts.getAs(0);
                                    left.wrapped.add(0, right);
                                    return left;
                                })
                                .addRule("assignments", "local_variable_assignment_statement", asts -> {
                                    return new WrapperNode<>(((MJNode) asts.get(0)).location, new ArrayList<>(Collections.singleton(asts.get(0))));
                                })
                                .addRule("assignments", "", asts -> {
                                    return new WrapperNode<>(new Location(0, 0), new ArrayList<>());
                                })
                                .addRule("if_statement", "IF LPAREN expression RPAREN statement", asts -> {
                                    return new IfStatementNode(
                                            asts.getStartLocation(),
                                            (ExpressionNode) asts.get(2),
                                            (StatementNode) asts.get(4));
                                })
                                .addRule("if_statement", "IF LPAREN expression RPAREN statement ELSE statement", asts -> {
                                    return new IfStatementNode(asts.getStartLocation(), (ExpressionNode) asts.get(2),
                                            (StatementNode) asts.get(4), (StatementNode) asts.get(6));
                                })
                                .addRule("return_statement", "RETURN", asts -> {
                                    return new ReturnStatementNode(asts.getStartLocation());
                                })
                                .addRule("return_statement", "RETURN expression", asts -> {
                                    return new ReturnStatementNode(asts.getStartLocation(), (ExpressionNode) asts.get(1));
                                })
                                /*.addRule("assert_statement", "ASSERT expression", asts -> {
                                    return new Assert
                                })*/
                                .addOperators("expression", "postfix_expression", operators -> {
                                    operators.defaultBinaryAction((asts, op) -> {
                                        return new BinaryOperatorNode((ExpressionNode) asts.get(0), (ExpressionNode) asts.get(2), valueOf(op));
                                    })
                                            .defaultUnaryAction((asts, opStr) -> {
                                                LexerTerminal op = valueOf(opStr);
                                                ExpressionNode child = null;
                                                Token opToken = null;
                                                boolean exprIsLeft = false;
                                                if (asts.get(0) instanceof ExpressionNode) {
                                                    child = (ExpressionNode) asts.get(0);
                                                    opToken = asts.get(1).getMatchedTokens().get(0);
                                                    exprIsLeft = true;
                                                } else {
                                                    child = (ExpressionNode) asts.get(1);
                                                    opToken = asts.get(0).getMatchedTokens().get(0);
                                                }
                                                return new UnaryOperatorNode(child, op);
                                            })
                                            .closeLayer()
                                            .binaryLayer(APPEND)
                                            .binaryLayer(OR)
                                            .binaryLayer(AND)
                                            .binaryLayer(BOR)
                                            .binaryLayer(BAND)
                                            .binaryLayer(XOR)
                                            .binaryLayer(EQUALS, UNEQUALS)
                                            .binaryLayer(LOWER, LOWER_EQUALS, GREATER, GREATER_EQUALS)
                                            .binaryLayer(LEFT_SHIFT, RIGHT_SHIFT)
                                            .binaryLayer(PLUS, MINUS)
                                            .binaryLayer(MULTIPLY, DIVIDE, MODULO)
                                            .custom("expression LBRACKET expression RBRACKET", asts -> {
                                                return new BracketedAccessOperatorNode((ExpressionNode) asts.get(0),
                                                        (ExpressionNode) asts.get(2));
                                            })
                                            .unaryLayerLeft(INVERT, MINUS, TILDE)
                                            .custom("LBRACKET INTEGER_LITERAL RBRACKET expression", asts -> {
                                                return new BitPlaceOperatorNode((ExpressionNode) asts.get(3),
                                                        vl.parse(asts.get(1).getMatchedString()).asLong());
                                            });
                                })
                                .addRule("postfix_expression", "primary_expression")
                                .addEitherRule("postfix_expression", "method_invocation")
                                .addRule("method_invocation", "ident globals LPAREN arguments RPAREN", asts -> {
                                    return new MethodInvocationNode(asts.getStartLocation(), asts.get(0).getMatchedString(),
                                            (ArgumentsNode) asts.get(3), asts.get(1).as());
                                })
                                .addRule("method_invocation", "ident globals LPAREN expression RPAREN", asts -> {
                                    ExpressionNode arg = (ExpressionNode) asts.get(3);
                                    return new MethodInvocationNode(asts.getStartLocation(), asts.get(0).getMatchedString(),
                                            new ArgumentsNode(arg.location, Utils.makeArrayList(arg)),
                                            asts.get(1).as());
                                })
                                .addRule("method_invocation", "ident LPAREN arguments RPAREN", asts -> {
                                    return new MethodInvocationNode(asts.getStartLocation(), asts.get(0).getMatchedString(),
                                            (ArgumentsNode) asts.get(2),
                                            new GlobalVariablesNode(new Location(0, 0), new HashMap<>()));
                                })
                                .addRule("method_invocation", "ident LPAREN tuple_element RPAREN", asts -> {
                                    ExpressionNode arg = (ExpressionNode) asts.get(2);
                                    return new MethodInvocationNode(asts.getStartLocation(), asts.get(0).getMatchedString(),
                                            new ArgumentsNode(arg.location, Utils.makeArrayList(arg)),
                                            new GlobalVariablesNode(new Location(0, 0), new HashMap<>()));
                                })
                                .addRule("phi", "PHI LPAREN ident COMMA ident RPAREN", asts -> {
                                    return new PhiNode(asts.getStartLocation(), Arrays.asList(asts.get(2).getMatchedString(),
                                            asts.get(4).getMatchedString()));
                                })
                                .addRule("arguments", "", asts -> new ArgumentsNode(new Location(0, 0), new ArrayList<>()))
                                .addRule("arguments", "tuple_element", asts -> {
                                    return new ArgumentsNode(((ExpressionNode) asts.get(0)).location, Utils.makeArrayList((ExpressionNode) asts.get(0)));
                                })
                                .addRule("arguments", "tuple_element COMMA arguments", asts -> {
                                    List<ExpressionNode> args = Utils.makeArrayList((ExpressionNode) asts.get(0));
                                    ArgumentsNode argsNode = ((ArgumentsNode) asts.get(2));
                                    args.addAll(argsNode.arguments);
                                    return new ArgumentsNode(argsNode.location, args);
                                })
                                .addRule("unpack", "MULTIPLY postfix_expression", asts -> new UnpackOperatorNode(asts.get(1).as()))
                                .addRule("primary_expression", "FALSE", asts -> new IntegerLiteralNode(asts.getStartLocation(), ValueLattice.get().parse(0)))
                                .addRule("primary_expression", "TRUE", asts -> new IntegerLiteralNode(asts.getStartLocation(), ValueLattice.get().parse(1)))
                                .addRule("primary_expression", "INTEGER_LITERAL", asts -> {
                                    Value val = ValueLattice.get().parse(asts.getMatchedString());
                                    statedBitWidth.val = Math.max(statedBitWidth.val, val.size());
                                    return new IntegerLiteralNode(asts.getStartLocation(), val);
                                })
                                .addRule("primary_expression", "var_access")
                                .addRule("primary_expression", "array_access")
                                .addRule("primary_expression", "LPAREN expression RPAREN", asts -> {
                                    return asts.get(1);
                                })
                                .addRule("primary_expression", "tuple_expression")
                                .addRule("primary_expression", "array_expression")
                                .addRule("tuple_expression", "LPAREN tuple_inner RPAREN", asts -> {
                                    return new TupleLiteralNode(asts.getStartLocation(), ((TupleLiteralNode) asts.get(1)).elements);
                                })
                                .addRule("tuple_expression", "LPAREN tuple_element RPAREN", asts -> {
                                    return new TupleLiteralNode(asts.getStartLocation(), Collections.singletonList((ExpressionNode) asts.get(1)));
                                })
                                .addRule("tuple_inner", Arrays.asList(
                                        "tuple_element COMMA",
                                        "tuple_element (COMMA tuple_element)+"), asts -> {
                                    return new TupleLiteralNode(new Location(0, 0), asts.getAll(ExpressionNode.class));
                                })
                                .addRule("array_expression", "LCURLY tuple_inner RCURLY", asts -> {
                                    return new ArrayLiteralNode(asts.getStartLocation(), ((TupleLiteralNode) asts.get(1)).elements);
                                })
                                .addRule("tuple_element", "unpack | expression", asts -> (BaseAST) asts.getAll(ExpressionNode.class).get(0))
                                .addRule("var_access", "ident", asts -> new VariableAccessNode(asts.getStartLocation(), asts.getMatchedString()))
                                .addRule("input_literal", "INPUT_LITERAL|INTEGER_LITERAL", asts -> {
                            /*List<Bit> rev = asts.get(0).<ListAST<?>>as().stream().map(s -> new Bit(B.U.parse(s.getMatchedString().substring(1)))).collect(Collectors.toList());
                            Collections.reverse(rev);
                            return new IntegerLiteralNode(new Value(rev));*/
                                    Value val = ValueLattice.get().parse(asts.getMatchedString());
                                    statedBitWidth.val = Math.max(statedBitWidth.val, val.size());
                                    return new IntegerLiteralNode(asts.getStartLocation(), val);
                                })
                                .addRule("globals", "", asts -> new GlobalVariablesNode(new Location(0, 0), new HashMap<>()))
                                .addRule("globals", "LBBRACKET globals_ RBBRACKET", asts -> asts.get(1))
                                .addRule("globals_", "", asts -> new GlobalVariablesNode(new Location(0, 0), new HashMap<>()))
                                .addRule("globals_", "global", asts -> {
                                    Map<String, Pair<String, String>> globs = new HashMap<>();
                                    Utils.Triple<String, String, String> glob = asts.get(0).<WrapperNode<Utils.Triple<String, String, String>>>as().wrapped;
                                    globs.put(glob.first, p(glob.second, glob.third));
                                    return new GlobalVariablesNode(new Location(0, 0), globs);
                                })
                                .addRule("globals_", "global COMMA globals_", asts -> {
                                    Utils.Triple<String, String, String> glob = asts.get(0).<WrapperNode<Utils.Triple<String, String, String>>>as().wrapped;
                                    GlobalVariablesNode globalNode = ((GlobalVariablesNode) asts.get(2));
                                    globalNode.globalVarSSAVars.put(glob.first, p(glob.second, glob.third));
                                    return new GlobalVariablesNode(((WrapperNode<?>) asts.get(0)).location, globalNode.globalVarSSAVars);
                                })
                                // globals only usable after the type transformation
                                .addRule("global", "ident ARROW ident ARROW ident", asts -> {
                                    return new WrapperNode<>(asts.getStartLocation(),
                                            new Utils.Triple<>(asts.get(0).getMatchedString(), asts.get(2).getMatchedString(), asts.get(4).getMatchedString()));
                                })
                                .addRule("ident", "IDENT|INPUT")
                                .addRule("type", "INT", asts -> new TypeNode(asts.getStartLocation(),
                                        types.INT))
                                .addRule("type", "array_type")
                                .addRule("type", "tuple_type")
                                .addRule("type", "VAR", asts -> {
                                    String type = asts.getMatchedString();
                                    if (!types.containsKey(type)) {
                                        throw new NildumuError(String.format("No such type %s", type));
                                    }
                                    return new TypeNode(asts.getStartLocation(), types.get(type));
                                })
                                .addRule("array_type", "type LBRACKET INTEGER_LITERAL RBRACKET", asts -> {
                                    Type subType = asts.get(0).<TypeNode>as().type;
                                    int length = (int) vl.parse(asts.get(2).getMatchedString()).asLong();
                                    return new TypeNode(asts.get(0).<MJNode>as().location,
                                            types.getOrCreateFixedArrayType(subType, Collections.singletonList(length)));
                                })
                                .addRule("tuple_type", "LPAREN type (COMMA type)* RPAREN", asts -> {
                                    List<Type> elementTypes = (List<Type>) asts.getAll("type").stream().map(a -> ((TypeNode) a).type).collect(Collectors.toList());
                                    return new TypeNode(asts.getStartLocation(), types.getOrCreateTupleType(elementTypes));
                                });
                    }, "program");
    }

    public static ProgramNode parse(String input) {
        if (!use_antlr) {
            return (ProgramNode) generator.parse(input);
        }
        return ParserFacade.parse(input);
    }

    /**
     * Start a simple repl
     */
    public static void main(String[] args) {
        Generator generator = Parser.generator;
        Utils.repl(generator::createLexer);
    }

    public static class LexerAndASTRepl {
        public static void main(String[] args) {
            Utils.parserRepl(s -> {
                Lexer lexer = Parser.generator.createLexer(s);
                try {
                    do {
                        System.out.print(lexer.next().toSimpleString() + " ");
                    } while (lexer.cur().type != 0);
                } catch (SWPException ex) {
                    System.out.print("Caught error: " + ex.getMessage());
                }
                System.out.println(Parser.parse(s).toPrettyString());
                return null;
            });
        }
    }

    public static ProgramNode process(String input) {
        return process(input, false);
    }

    /**
     * Process the passed input.
     * Currently does a name resolution and converts the result into SSA form
     */
    public static ProgramNode process(String input, boolean transformPlus) {
        return process(input, transformPlus, false);
    }

    /**
     * Process the passed input.
     * Currently does a name resolution and converts the result into SSA form
     */
    public static ProgramNode process(String input, boolean transformPlus, boolean transformLoops) {
        return ProcessingPipeline.create(transformPlus).process(input);
    }

    /**
     * Visitor that delegates each not implemented visit method to the visit method for the parent class.
     */
    public interface NodeVisitor<R> extends StatementVisitor<R>, ExpressionVisitor<R> {

        R visit(MJNode node);

        default R visit(ProgramNode program) {
            return visit((MJNode) program);
        }

        default R visit(MethodNode method) {
            return visit((MJNode) method);
        }

        default <T> R visit(WrapperNode<T> wrapper) {
            return visit((MJNode) wrapper);
        }

        default R visit(MethodPartNode methodPart) {
            return visit((MJNode) methodPart);
        }

        default R visit(BlockPartNode blockPart) {
            return visit((MethodPartNode) blockPart);
        }

        default R visit(StatementNode statement) {
            return visit((BlockPartNode) statement);
        }

        default R visit(ArgumentsNode arguments) {
            return visit((BlockPartNode) arguments);
        }

        default R visit(ExpressionNode expression) {
            return visit((BlockPartNode) expression);
        }

        default R visit(MethodInvocationNode methodInvocation) {
            return visit((ExpressionNode) methodInvocation);
        }

        default R visit(VariableAssignmentNode assignment) {
            return visit((StatementNode) assignment);
        }

        default R visit(ArrayAssignmentNode assignment) {
            return visit((VariableAssignmentNode) assignment);
        }

        default R visit(MultipleVariableAssignmentNode assignment) {
            return visit((StatementNode) assignment);
        }

        default R visit(VariableDeclarationNode variableDeclaration) {
            return visit((VariableAssignmentNode) variableDeclaration);
        }

        default R visit(OutputVariableDeclarationNode outputDecl) {
            return visit((VariableDeclarationNode) outputDecl);
        }

        default R visit(AppendOnlyVariableDeclarationNode appendDecl) {
            return visit((VariableDeclarationNode) appendDecl);
        }

        default R visit(InputVariableDeclarationNode inputDecl) {
            return visit((VariableDeclarationNode) inputDecl);
        }

        default R visit(TmpInputVariableDeclarationNode inputDecl) {
            return visit((VariableDeclarationNode) inputDecl);
        }

        default R visit(BlockNode block) {
            return visit((StatementNode) block);
        }

        default R visit(ParametersNode parameters) {
            return visit((MethodPartNode) parameters);
        }

        default R visit(ParameterNode parameter) {
            return visit((MethodPartNode) parameter);
        }

        default R visit(PhiNode phi) {
            return visit((ExpressionNode) phi);
        }

        default R visit(ConditionalStatementNode condStatement) {
            return visit((StatementNode) condStatement);
        }

        default R visit(IfStatementNode ifStatement) {
            return visit((ConditionalStatementNode) ifStatement);
        }

        default R visit(IfStatementEndNode ifEndStatement) {
            return visit((StatementNode) ifEndStatement);
        }

        default R visit(WhileStatementNode whileStatement) {
            return visit((ConditionalStatementNode) whileStatement);
        }

        default R visit(WhileStatementEndNode whileEndStatement) {
            return visit((StatementNode) whileEndStatement);
        }

        default R visit(LoopInterruptionNode loopInterruptionNode) {
            return visit((StatementNode) loopInterruptionNode);
        }

        default R visit(VariableAccessNode variableAccess) {
            return visit((PrimaryExpressionNode) variableAccess);
        }

        default R visit(ParameterAccessNode variableAccess) {
            return visit((VariableAccessNode) variableAccess);
        }

        default R visit(BinaryOperatorNode binaryOperator) {
            return visit((ExpressionNode) binaryOperator);
        }

        default R visit(UnaryOperatorNode unaryOperator) {
            return visit((ExpressionNode) unaryOperator);
        }

        default R visit(UnpackOperatorNode unpackOperator) {
            return visit((UnaryOperatorNode) unpackOperator);
        }

        default R visit(BracketedAccessOperatorNode bracketedAccess) {
            return visit((BinaryOperatorNode) bracketedAccess);
        }

        default R visit(BitPlaceOperatorNode bitPlacement) {
            return visit((UnaryOperatorNode) bitPlacement);
        }

        default R visit(PrimaryExpressionNode primaryExpression) {
            return visit((ExpressionNode) primaryExpression);
        }

        default R visit(TupleLikeLiteralNode tupleLikeLiteral) {
            return visit((PrimaryExpressionNode) tupleLikeLiteral);
        }

        default R visit(TupleLiteralNode tupleLiteral) {
            return visit((TupleLikeLiteralNode) tupleLiteral);
        }

        default R visit(ArrayLiteralNode arrayLiteral) {
            return visit((TupleLikeLiteralNode) arrayLiteral);
        }

        default R visit(ExpressionStatementNode expressionStatement) {
            return visit((StatementNode) expressionStatement);
        }

        default R visit(ReturnStatementNode returnStatement) {
            return visit((StatementNode) returnStatement);
        }

        default R visit(TypeNode typeNode) {
            return visit((MJNode) typeNode);
        }

        /**
         * Visit all direct children with the visitor and return the results
         */
        default List<R> visitChildren(MJNode node) {
            try {
                return node.children().stream().map(c -> ((MJNode) c).accept(this)).collect(Collectors.toList());
            } catch (NullPointerException ex) {
                return null;
            }
        }

        Set<BaseAST> alreadyVisited = new HashSet<>();

        /**
         * Visit all direct children with the visitor and discard the results
         */
        default void visitChildrenDiscardReturn(MJNode node) {
            node.children().forEach(c -> ((MJNode) c).accept(this));
        }
    }

    public interface StatementVisitor<R> {

        R visit(StatementNode statement);

        default R visit(VariableAssignmentNode assignment) {
            return visit((StatementNode) assignment);
        }

        default R visit(MultipleVariableAssignmentNode assignment) {
            return visit((StatementNode) assignment);
        }

        default R visit(VariableDeclarationNode variableDeclaration) {
            return visit((VariableAssignmentNode) variableDeclaration);
        }

        default R visit(ArrayAssignmentNode variableDeclaration) {
            return visit((VariableAssignmentNode) variableDeclaration);
        }

        default R visit(OutputVariableDeclarationNode outputDecl) {
            return visit((VariableDeclarationNode) outputDecl);
        }

        default R visit(AppendOnlyVariableDeclarationNode appendDecl) {
            return visit((VariableDeclarationNode) appendDecl);
        }

        default R visit(InputVariableDeclarationNode inputDecl) {
            return visit((VariableDeclarationNode) inputDecl);
        }

        default R visit(TmpInputVariableDeclarationNode inputDecl) {
            return visit((VariableDeclarationNode) inputDecl);
        }

        default R visit(BlockNode block) {
            return visit((StatementNode) block);
        }

        default R visit(ConditionalStatementNode condStatement) {
            return visit((StatementNode) condStatement);
        }

        default R visit(IfStatementNode ifStatement) {
            return visit((ConditionalStatementNode) ifStatement);
        }

        default R visit(IfStatementEndNode ifEndStatement) {
            return visit((StatementNode) ifEndStatement);
        }

        default R visit(WhileStatementNode whileStatement) {
            return visit((ConditionalStatementNode) whileStatement);
        }

        default R visit(WhileStatementEndNode whileEndStatement) {
            return visit((StatementNode) whileEndStatement);
        }

        default R visit(LoopInterruptionNode loopInterruptionNode) {
            return visit((StatementNode) loopInterruptionNode);
        }

        default R visit(ExpressionStatementNode expressionStatement) {
            return visit((StatementNode) expressionStatement);
        }

        default R visit(ReturnStatementNode returnStatement) {
            return visit((StatementNode) returnStatement);
        }

        /**
         * Visit all direct children statements with the visitor and return the results
         */
        default List<R> visitChildren(MJNode node) {
            return node.children().stream().filter(c -> c instanceof StatementNode).map(c -> ((StatementNode) c).accept(this)).collect(Collectors.toList());
        }

        /**
         * Visit all direct children statements with the visitor and discard the results
         */
        default void visitChildrenDiscardReturn(MJNode node) {
            node.children().stream().filter(c -> c instanceof StatementNode).forEach(c -> ((StatementNode) c).accept(this));
        }
    }

    public interface ExpressionVisitor<R> {


        R visit(ExpressionNode expression);

        default R visit(PhiNode phi) {
            return visit((ExpressionNode) phi);
        }

        default R visit(VariableAccessNode variableAccess) {
            return visit((PrimaryExpressionNode) variableAccess);
        }

        default R visit(ParameterAccessNode variableAccess) {
            return visit((VariableAccessNode) variableAccess);
        }

        default R visit(IntegerLiteralNode literal) {
            return visit((PrimaryExpressionNode) literal);
        }

        default R visit(TupleLikeLiteralNode tupleLikeLiteral) {
            return visit((PrimaryExpressionNode) tupleLikeLiteral);
        }

        default R visit(TupleLiteralNode tupleLiteral) {
            return visit((TupleLikeLiteralNode) tupleLiteral);
        }

        default R visit(ArrayLiteralNode arrayLiteral) {
            return visit((TupleLikeLiteralNode) arrayLiteral);
        }

        default R visit(BinaryOperatorNode binaryOperator) {
            return visit((ExpressionNode) binaryOperator);
        }

        default R visit(UnaryOperatorNode unaryOperator) {
            return visit((ExpressionNode) unaryOperator);
        }

        default R visit(UnpackOperatorNode unpackOperator) {
            return visit((UnaryOperatorNode) unpackOperator);
        }

        default R visit(PrimaryExpressionNode primaryExpression) {
            return visit((ExpressionNode) primaryExpression);
        }

        default R visit(BracketedAccessOperatorNode bracketedAccess) {
            return visit((BinaryOperatorNode) bracketedAccess);
        }

        default R visit(BitPlaceOperatorNode bitPlacement) {
            return visit((UnaryOperatorNode) bitPlacement);
        }

        default R visit(MethodInvocationNode methodInvocation) {
            return visit((ExpressionNode) methodInvocation);
        }

        /**
         * Visit all direct children statements with the visitor and return the results
         */
        default List<R> visitChildren(MJNode node) {
            return node.children().stream().filter(c -> c instanceof ExpressionNode).map(c -> ((ExpressionNode) c).accept(this)).collect(Collectors.toList());
        }

        /**
         * Visit all direct children statements with the visitor and discard the results
         */
        default void visitChildrenDiscardReturn(MJNode node) {
            node.children().stream().filter(c -> c instanceof ExpressionNode).forEach(c -> ((ExpressionNode) c).accept(this));
        }
    }

    /**
     * Expression visitor that gets passed an argument that is usually related to the children of the node.
     * <p/>
     * Used for evaluation (see {@link FixpointIteration#walkExpression(ExpressionVisitorWArgs, ExpressionNode)}
     *
     * @param <R> type of the result of each visit
     * @param <A> type of the argument for the visit methods
     */
    public interface ExpressionVisitorWArgs<R, A> {


        R visit(ExpressionNode expression, A argument);

        default R visit(PhiNode phi, A argument) {
            return visit((ExpressionNode) phi, argument);
        }


        default R visit(VariableAccessNode variableAccess, A argument) {
            return visit((PrimaryExpressionNode) variableAccess, argument);
        }

        default R visit(ParameterAccessNode variableAccess, A argument) {
            return visit((VariableAccessNode) variableAccess, argument);
        }

        default R visit(IntegerLiteralNode literal, A argument) {
            return visit((PrimaryExpressionNode) literal, argument);
        }

        default R visit(TupleLikeLiteralNode tupleLikeLiteral, A argument) {
            return visit((PrimaryExpressionNode) tupleLikeLiteral, argument);
        }

        default R visit(TupleLiteralNode tupleLiteral, A argument) {
            return visit((TupleLikeLiteralNode) tupleLiteral, argument);
        }

        default R visit(ArrayLiteralNode arrayLiteral, A argument) {
            return visit((TupleLikeLiteralNode) arrayLiteral, argument);
        }

        default R visit(BinaryOperatorNode binaryOperator, A argument) {
            return visit((ExpressionNode) binaryOperator, argument);
        }

        default R visit(UnaryOperatorNode unaryOperator, A argument) {
            return visit((ExpressionNode) unaryOperator, argument);
        }

        default R visit(UnpackOperatorNode unpackOperator, A argument) {
            return visit((UnaryOperatorNode) unpackOperator, argument);
        }

        default R visit(BracketedAccessOperatorNode bracketedAccess, A argument) {
            return visit((BinaryOperatorNode) bracketedAccess, argument);
        }

        default R visit(BitPlaceOperatorNode bitPlacement, A argument) {
            return visit((UnaryOperatorNode) bitPlacement, argument);
        }

        default R visit(PrimaryExpressionNode primaryExpression, A argument) {
            return visit((ExpressionNode) primaryExpression, argument);
        }
    }

    /**
     * A basic AST Node for the language
     */
    public static abstract class MJNode extends BaseAST {

        private static int idCounter = 0;

        public final Location location;

        protected MJNode(Location location) {
            this.location = location;
        }


        @Override
        public List<Token> getMatchedTokens() {
            return null;
        }

        public abstract <R> R accept(NodeVisitor<R> visitor);

        @Override
        public List<BaseAST> children() {
            return new ArrayList<>();
        }

        public String getTextualId() {
            return shortType() + location.toString();
        }

        public static void resetIdCounter() {
            idCounter = 0;
        }

        public static int getCurrentIdCount() {
            return idCounter;
        }

        public Operator getOperator(Context c) {
            return getOperator();
        }

        Operator getOperator() {
            throw new UnsupportedOperationException();
        }

        public abstract String shortType();

        @Override
        public String type() {
            return shortType();
        }

        public Set<Bit> getInnerTmpInputs(Context context) {
            return getTmpInputVariableDeclarations().stream().flatMap(t -> context.getVariableValue(t.variable).stream())
                    .collect(Collectors.toSet());
        }

        public Set<Bit> getInnerTmpInputsFromAll(Context context) {
            return getTmpInputVariableDeclarationsFromAll().stream().flatMap(t -> context.getVariableValue(t.variable).stream())
                    .collect(Collectors.toSet());
        }

        public Set<TmpInputVariableDeclarationNode> getTmpInputVariableDeclarations() {
            return this.accept(new NodeVisitor<Set<TmpInputVariableDeclarationNode>>() {
                @Override
                public Set<TmpInputVariableDeclarationNode> visit(MJNode node) {
                    return visitChildren(node).stream().flatMap(Set::stream).collect(Collectors.toSet());
                }

                @Override
                public Set<TmpInputVariableDeclarationNode> visit(TmpInputVariableDeclarationNode inputDecl) {
                    return Collections.singleton(inputDecl);
                }
            });
        }

        /**
         * From all called methods, …
         */
        public List<TmpInputVariableDeclarationNode> getTmpInputVariableDeclarationsFromAll() {
            Set<MJNode> alreadyChecked = new HashSet<>();
            List<TmpInputVariableDeclarationNode> collection = new ArrayList<>();
            accept(new NodeVisitor<Object>() {
                @Override
                public Object visit(MJNode node) {
                    if (!alreadyChecked.contains(node)) {
                        alreadyChecked.add(node);
                        visitChildrenDiscardReturn(node);
                    }
                    return null;
                }

                @Override
                public Object visit(TmpInputVariableDeclarationNode inputDecl) {
                    collection.add(inputDecl);
                    return null;
                }

                @Override
                public Object visit(BlockNode block) {
                    if (!alreadyChecked.contains(block)) {
                        alreadyChecked.add(block);
                        visitChildrenDiscardReturn(block);
                    }
                    return null;
                }

                @Override
                public Object visit(MethodInvocationNode methodInvocation) {
                    visitChildrenDiscardReturn(methodInvocation);
                    visitChildrenDiscardReturn(methodInvocation.definition.body);
                    return null;
                }
            });
            return collection;
        }
    }

    /**
     * A node wrapping literal values.
     */
    public static class WrapperNode<T> extends MJNode {
        public final T wrapped;

        protected WrapperNode(Location location, T wrapped) {
            super(location);
            this.wrapped = wrapped;
        }

        @Override
        public <R> R accept(NodeVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public Operator getOperator() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String shortType() {
            return "s";
        }
    }

    /**
     * A program with some methods and a global block
     */
    public static class ProgramNode extends MJNode {

        public final Type INT;
        public final Context context;

        public final Types types;

        private final Map<Variable, Object> inputVariables = new IdentityHashMap<>();
        private final Map<Variable, Object> outputVariables = new IdentityHashMap<>();
        private final Map<String, MethodNode> methods = new HashMap<>();

        public final BlockNode globalBlock;

        public ProgramNode(Context context) {
            this(context, new Types());
        }

        public ProgramNode(Context context, Types types) {
            super(new Location(1, 1));
            this.context = context;
            this.types = types;
            globalBlock = new BlockNode(location, new ArrayList<>());
            INT = types.INT;
        }

        public void addMethod(MethodNode methodNode) {
            methods.put(methodNode.name, methodNode);
        }

        public List<String> getMethodNames() {
            return new ArrayList<>(methods.keySet());
        }

        public MethodNode getMethod(String name) {
            return methods.get(name);
        }

        public boolean hasMethod(String name) {
            return methods.containsKey(name);
        }

        public Set<Variable> getInputVariables() {
            return inputVariables.keySet();
        }

        public void addInputVariable(Variable variable) {
            assert variable.isInput;
            inputVariables.put(variable, null);
        }

        public Set<Variable> getOutputVariables() {
            return outputVariables.keySet();
        }

        public void addOuputVariable(Variable variable) {
            assert variable.isOutput;
            outputVariables.put(variable, null);
        }

        public void addGlobalStatement(StatementNode statement) {
            globalBlock.add(statement);
        }

        public void addGlobalStatements(List<StatementNode> statements) {
            globalBlock.addAll(statements);
        }

        @Override
        public String toString() {
            return globalBlock.toString() + "\ninput variables: " + getInputVariables().stream().map(v -> v.name).collect(Collectors.joining(";")) +
                    "\noutput variables: " + getOutputVariables().stream().map(Variable::toString).collect(Collectors.joining(";"));// +
            //   "methods:\n  " + methods.keySet().stream().sorted().map(methods::get).map(MethodNode::toString).collect(Collectors.joining("\n  "));
        }

        @Override
        public String type() {
            return "program";
        }

        @Override
        public List<BaseAST> children() {
            return Stream.concat(methods.values().stream(), Stream.of(globalBlock)).collect(Collectors.toList());
        }

        @Override
        public <R> R accept(NodeVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public Operator getOperator() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String shortType() {
            return "p";
        }

        @Override
        public String toPrettyString(String indent, String incr) {
            return String.format("use_sec %s;\nbit_width %d;", context.sl.latticeName(), context.maxBitWidth) + "\n" +
                    methods().stream().map(m -> m.toPrettyString(indent, incr)).collect(Collectors.joining("\n")) +
                    (globalBlock.isEmpty() ? "" : globalBlock.toPrettyString(indent, incr, false));
        }

        public Collection<MethodNode> methods() {
            return methods.values();
        }

        void handleInputAndPrint() {
            for (Sec<?> sec : context.sl.elements()) {
                handleInputAndPrint(sec);
            }
        }

        public String inputName(Sec<?> sec) {
            if (sec == sec.lattice().top()) {
                return "input";
            }
            return sec.toString() + "_input";
        }

        public static String printName(Sec<?> sec) {
            if (sec == sec.lattice().bot()) {
                return "print";
            }
            return sec.toString() + "_print";
        }

        private void handleInputAndPrint(Sec<?> sec) {
            handleInput(sec);
            handlePrint(sec);
        }

        private void handleInput(Sec<?> sec) {
            if (!isMethodUsed(inputName(sec))) {
                return;
            }
            Location loc = new Location(0, 0);
            addMethodIfNeeded(new MethodNode(loc, inputName(sec), types.INT,
                    new ParametersNode(loc, Collections.emptyList()), new BlockNode(loc, Utils.makeArrayList(
                    new TmpInputVariableDeclarationNode(loc, "tmp", types.INT,
                            new IntegerLiteralNode(loc, IntStream.range(0, context.maxBitWidth).mapToObj(i -> bl.create(Lattices.B.U)).collect(Lattices.Value.collector())),
                            sec.toString()),
                    new VariableAssignmentNode(loc, inputName(sec), new BinaryOperatorNode(new VariableAccessNode(loc, inputName(sec)), new VariableAccessNode(loc, "tmp"), APPEND)),
                    new ReturnStatementNode(loc, new VariableAccessNode(loc, "tmp"))
            )), new GlobalVariablesNode(loc, new HashMap<>())));
            addAppendOnlyVariableDeclarationIfNeeded(inputName(sec), sec);
        }

        private void handlePrint(Sec<?> sec) {
            if (!isMethodUsed(printName(sec))) {
                return;
            }
            Location loc = new Location(0, 0);
            addMethodIfNeeded(new MethodNode(loc, printName(sec), types.INT,
                    new ParametersNode(loc, Collections.singletonList(new ParameterNode(loc, types.INT, "x"))), new BlockNode(loc, Utils.makeArrayList(
                    new VariableAssignmentNode(loc, printName(sec),
                            new BinaryOperatorNode(
                                    new VariableAccessNode(loc, printName(sec)),
                                    new VariableAccessNode(loc, "x"),
                                    APPEND))
            )), new GlobalVariablesNode(loc, new HashMap<>())));
            addAppendOnlyVariableDeclarationIfNeeded(printName(sec), sec);
        }

        private void addAppendOnlyVariableDeclarationIfNeeded(String name, Sec<?> sec) {
            if (!doesVariableExist(name)) {
                globalBlock.statementNodes.add(0, new AppendOnlyVariableDeclarationNode(new Location(0, 0), name, types.INT, sec.toString()));
                context.addAppendOnlyVariable(sec, name);
            }
        }

        private void addMethodIfNeeded(MethodNode method) {
            if (!hasMethod(method.name)) {
                addMethod(method);
            }
        }

        private boolean doesVariableExist(String variable) {
            return globalBlock.accept(new StatementVisitor<Boolean>() {
                @Override
                public Boolean visit(StatementNode statement) {
                    return false;
                }

                @Override
                public Boolean visit(VariableDeclarationNode variableDeclaration) {
                    return variableDeclaration.variable.equals(variable);
                }

                @Override
                public Boolean visit(BlockNode block) {
                    return visitChildren(block).stream().anyMatch(Boolean::booleanValue);
                }
            });
        }

        private boolean isMethodUsed(String name) {
            return accept(new NodeVisitor<Boolean>() {
                @Override
                public Boolean visit(MJNode node) {
                    return visitChildren(node).stream().anyMatch(Boolean::booleanValue);
                }

                @Override
                public Boolean visit(MethodInvocationNode methodInvocation) {
                    return methodInvocation.method.equals(name) || visit((MJNode) methodInvocation);
                }
            });
        }
    }

    /**
     * Node representing a simple method that gets passed some integers and returns an integer
     */
    public static class MethodNode extends MJNode {
        public final String name;
        private Type returnType;
        public final ParametersNode parameters;
        public final BlockNode body;
        public final GlobalVariablesNode globals;
        Map<String, Pair<Variable, Variable>> globalStringDefs = null;
        public Map<Variable, Pair<Variable, Variable>> globalDefs = null;

        public MethodNode(Location location, String name, Type returnType, ParametersNode parameters, BlockNode body, GlobalVariablesNode globals) {
            super(location);
            this.name = name;
            this.returnType = returnType;
            this.parameters = parameters;
            this.body = body;
            this.globals = globals;
        }

        @Override
        public String toString() {
            return String.format("%s %s[[%s]](%s)", getReturnType(), name, globals, parameters);
        }

        @Override
        public String type() {
            return "method";
        }

        @Override
        public List<BaseAST> children() {
            return Utils.makeArrayList(parameters, globals, body);
        }

        @Override
        public <R> R accept(NodeVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public Operator getOperator() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String shortType() {
            return "m";
        }

        @Override
        public String getTextualId() {
            return "m:" + name;
        }

        public boolean hasReturnValue() {
            return !body.statementNodes.isEmpty() && body.getLastStatementOrNull() instanceof ReturnStatementNode && ((ReturnStatementNode) body.getLastStatementOrNull()).hasReturnExpression();
        }

        /**
         * empty → var args
         */
        public Optional<Integer> getNumberOfParameters() {
            return Optional.of(parameters.size());
        }

        @Override
        public String toPrettyString(String indent, String incr) {
            return indent + String.format("%s %s%s(%s){\n%s\n}\n", getReturnType(), name, globals.globalVarSSAVars.size() > 0 ? String.format("[[%s]]", globals) : "", parameters, body.toPrettyString(indent + incr, incr, false));
        }

        /**
         * Returns the variables that are defined in this statement
         */
        public Set<String> getDefinedVariables() {
            Set<String> set = new HashSet<>(body.getDefinedVariables());
            parameters.parameterNodes.forEach(p -> set.add(p.name));
            return set;
        }

        /**
         * DefinedVariables[this] \ DefinedVariables[inner]
         */
        public Set<String> getVariablesDefinedOutside(StatementNode inner) {
            Set<String> set = new HashSet<>(getDefinedVariables());
            set.removeAll(inner.getDefinedVariables());
            return set;
        }

        public boolean isPredefined() {
            return false;
        }

        public Type getReturnType() {
            return returnType;
        }

        public void setReturnType(Type returnType) {
            if (this.returnType != null && this.returnType != returnType) {
                throw new NildumuError(String.format("Return type of method %s cannot have both type %s and %s",
                        this.name, this.returnType, returnType));
            }
            this.returnType = returnType;
        }

        public int getNumberOfReturnValues() {
            if (hasReturnValue()) {
                assert returnType != null;
                return returnType.getNumberOfBlastedVariables();
            }
            return 0;
        }
    }

    public static class PredefinedMethodNode extends MethodNode {

        private final static Map<String, Function<ProgramNode, PredefinedMethodNode>> predefinedMethods = new HashMap<>();

        static {
            predefinedMethods.put("length",
                    program -> new PredefinedMethodNode("length", program.types.INT, -1, null, l -> {
                        throw new NildumuError("");
                    }));
            predefinedMethods.put("toInt",
                    program -> new PredefinedMethodNode("toInt", program.types.INT, 1,
                            Collections.singletonList(program.types.getOrCreateTupleType(Collections.singletonList(program.INT))), l -> {
                        return l.get(0);
                    }));
        }

        public static boolean isPredefined(String methodName) {
            return predefinedMethods.containsKey(methodName);
        }

        public static PredefinedMethodNode getPredefined(ProgramNode program, String methodName) {
            assert isPredefined(methodName);
            return predefinedMethods.get(methodName).apply(program);
        }

        private final int parameterCount;
        private final Function<List<Value>, Value> function;

        public PredefinedMethodNode(String name, Type returnType, int parameterCount, List<Type> paramTypes, Function<List<Value>, Value> function) {
            super(Location.ZERO, name, returnType, parameterCount != -1 ?
                    new ParametersNode(Location.ZERO, enumerate(paramTypes, (i, t) -> new ParameterNode(Location.ZERO, t, "p" + i))) : null,
                    null, new GlobalVariablesNode(Location.ZERO, new HashMap<>()));
            this.parameterCount = parameterCount;
            this.function = function;
        }

        @Override
        public Optional<Integer> getNumberOfParameters() {
            return parameterCount == -1 ? Optional.empty() : Optional.of(parameterCount);
        }

        public Value apply(List<Value> arguments) {
            if (arguments.size() != parameterCount) {
                throw new NildumuError(String.format("Not enought parameters for method %s", name));
            }
            return function.apply(arguments);
        }

        @Override
        public String toString() {
            return String.format("%s %s(%s)", getReturnType(), name, parameters);
        }

        @Override
        public String type() {
            return "method";
        }

        @Override
        public List<BaseAST> children() {
            return new ArrayList<>();
        }

        @Override
        public String shortType() {
            return "sm";
        }

        @Override
        public String getTextualId() {
            return "sm:" + name;
        }

        public boolean hasReturnValue() {
            return true;
        }

        public int getNumberOfReturnValues() {
            return hasReturnValue() ? 1 : 0;
        }

        @Override
        public boolean isPredefined() {
            return true;
        }
    }

    /**
     * Node that can be part of a method
     */
    public static abstract class MethodPartNode extends MJNode {
        public MethodNode parentMethod;

        public MethodPartNode(Location location) {
            super(location);
        }

        public abstract void setParentMethod(MethodNode parentMethod);

        public boolean hasParentMethod() {
            return parentMethod != null;
        }

        @Override
        public <R> R accept(NodeVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public Operator getOperator() {
            throw new UnsupportedOperationException();
        }

    }

    /**
     * A list of parameters (their definitions)
     */
    public static class ParametersNode extends MethodPartNode {

        public final List<ParameterNode> parameterNodes;

        public ParametersNode(Location location, List<ParameterNode> parameterNodes) {
            super(location);
            this.parameterNodes = parameterNodes;
        }

        public ParametersNode(List<Variable> parameters) {
            this(new Location(0, 0), parameters.stream().map(ParameterNode::new).collect(Collectors.toList()));
        }

        @Override
        public void setParentMethod(MethodNode parentMethod) {
            this.parentMethod = parentMethod;
            for (ParameterNode parameterNode : parameterNodes) {
                parameterNode.setParentMethod(parentMethod);
            }
        }

        @Override
        public String toString() {
            return Utils.toString(", ", parameterNodes);
        }

        @Override
        public String type() {
            return "parameters";
        }

        @Override
        public List<BaseAST> children() {
            return Arrays.asList(parameterNodes.toArray(new BaseAST[]{}));
        }

        @Override
        public <R> R accept(NodeVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public Operator getOperator() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String shortType() {
            return "ps";
        }

        public int size() {
            return parameterNodes.size();
        }

        public ParameterNode get(int i) {
            return parameterNodes.get(i);
        }
    }

    /**
     * A parameter defintion
     */
    public static class ParameterNode extends MethodPartNode {

        public Variable definition;
        public final Type type;
        public final String name;


        public ParameterNode(Location location, Type type, String name) {
            super(location);
            assert !name.isEmpty() && Objects.nonNull(type);
            this.type = type;
            this.name = name;
        }

        public ParameterNode(Variable variable) {
            this(new Location(0, 0), variable.type, variable.name);
            this.definition = variable;
        }

        @Override
        public String toString() {
            return String.format("%s %s", type, name);
        }

        @Override
        public void setParentMethod(MethodNode parentMethod) {
            this.parentMethod = parentMethod;
        }

        @Override
        public String type() {
            return "parameter";
        }

        @Override
        public List<BaseAST> children() {
            return Utils.makeArrayList();
        }

        @Override
        public <R> R accept(NodeVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public Operator getOperator() {
            return null;
        }

        @Override
        public String shortType() {
            return "p";
        }

        @Override
        public String getTextualId() {
            return "p:" + (definition != null ? definition.name : name);
        }
    }

    /**
     * A node that might be part of a block
     */
    public static abstract class BlockPartNode extends MethodPartNode {
        public BlockNode parentBlock = null;

        public BlockPartNode(Location location) {
            super(location);
        }

        public abstract BlockPartNode[] getBlockParts();

        public void setParentBlock(BlockNode parentBlock) {
            this.parentBlock = parentBlock;
            for (BlockPartNode blockPartNode : getBlockParts()) {
                if (blockPartNode != null) {
                    blockPartNode.setParentBlock(parentBlock);
                }
            }
        }

        @Override
        public void setParentMethod(MethodNode parentMethod) {
            this.parentMethod = parentMethod;
            for (BlockPartNode blockPartNode : getBlockParts()) {
                if (blockPartNode != null) {
                    blockPartNode.setParentMethod(parentMethod);
                }
            }
        }

        @Override
        public <R> R accept(NodeVisitor<R> visitor) {
            return visitor.visit(this);
        }
    }

    /**
     * A statement
     */
    public static abstract class StatementNode extends BlockPartNode {
        public StatementNode(Location location) {
            super(location);
        }

        @Override
        public <R> R accept(NodeVisitor<R> visitor) {
            return visitor.visit(this);
        }

        public <R> R accept(StatementVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public Operator getOperator() {
            throw new UnsupportedOperationException();
        }

        /**
         * Returns the variables that are defined in this statement
         */
        public Set<String> getDefinedVariables() {
            return Collections.emptySet();
        }

        /**
         * DefinedVariables[this] \ DefinedVariables[inner]
         */
        public Set<String> getVariablesDefinedOutside(StatementNode inner) {
            Set<String> set = new HashSet<>(getDefinedVariables());
            set.removeAll(inner.getDefinedVariables());
            return set;
        }
    }

    /**
     * A block of {@link BlockPartNode}s
     */
    public static class BlockNode extends StatementNode {
        public final List<StatementNode> statementNodes;
        private ConditionalStatementNode lastCondStatement = null;
        private final Set<String> definedVariables;

        /**
         * remove all unneeded blocks
         */
        static List<StatementNode> collapseBlocks(List<StatementNode> stmts) {
            while (stmts.size() == 1 && stmts.get(0) instanceof BlockNode) {
                stmts = ((BlockNode) stmts.get(0)).statementNodes;
            }
            return stmts;
        }

        public BlockNode(Location location, List<StatementNode> statementNodes) {
            this(location, statementNodes, true);
        }

        BlockNode(Location location, List<StatementNode> statementNodes, boolean setPhiConds) {
            super(location);
            this.statementNodes = collapseBlocks(statementNodes);
            ConditionalStatementNode lastCondStatement = null;
            if (setPhiConds) {
                for (BlockPartNode statementNode : this.statementNodes) {
                    statementNode.setParentBlock(this);
                    if (lastCondStatement != null) {
                        lastCondStatement.setPhisCondInNodes(Collections.singletonList(statementNode));
                    }
                    if (statementNode instanceof ConditionalStatementNode) {
                        lastCondStatement = (ConditionalStatementNode) statementNode;
                    }
                }
            }
            definedVariables = this.statementNodes.stream().flatMap(s -> s.getDefinedVariables().stream()).collect(Collectors.toSet());
        }

        public void add(StatementNode statementNode) {
            statementNodes.add(statementNode);
            statementNode.setParentBlock(this);
            if (lastCondStatement != null) {
                lastCondStatement.setPhisCondInNodes(Collections.singletonList(statementNode));
            }
            if (statementNode instanceof ConditionalStatementNode) {
                lastCondStatement = (ConditionalStatementNode) statementNode;
            }
            definedVariables.addAll(statementNode.getDefinedVariables());
        }

        public void addAll(Collection<StatementNode> statementNodes) {
            statementNodes.forEach(this::add);
        }

        @Override
        public BlockPartNode[] getBlockParts() {
            return statementNodes.toArray(new BlockPartNode[]{});
        }

        @Override
        public String toString() {
            return String.format("{\n%s\n}", statementNodes.stream().flatMap(s -> Arrays.stream(s.toString().split("\n")).map(str -> "  " + str)).collect(Collectors.joining("\n")));
        }

        @Override
        public String type() {
            return "block";
        }

        @Override
        public List<BaseAST> children() {
            return Arrays.asList(statementNodes.toArray(new BaseAST[]{}));
        }

        @Override
        public <R> R accept(NodeVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public <R> R accept(StatementVisitor<R> visitor) {
            return visitor.visit(this);
        }

        public StatementNode getLastStatementOrNull() {
            if (statementNodes.isEmpty()) {
                return null;
            }
            return statementNodes.get(statementNodes.size() - 1);
        }

        @Override
        public String shortType() {
            return "b";
        }

        @Override
        public String toPrettyString(String indent, String incr) {
            return toPrettyString(indent, incr, true);
        }

        public String toPrettyString(String indent, String incr, boolean showCurleyBrackets) {
            if (isEmpty()) {
                return "{}";
            }
            Pair<List<VariableDeclarationNode>, List<StatementNode>> partition = splitIntoDeclsAndRest();
            String res = showCurleyBrackets ? (indent + "{\n") : "";
            if (partition.first.size() > 0) {
                res += indent + partition.first.stream().map(BaseAST::toPrettyString).collect(Collectors.joining(" "))
                        + "\n";
            }
            res += partition.second.stream()
                    .map(s -> s.toPrettyString(showCurleyBrackets ? (indent + incr) : indent, incr))
                    .filter(s -> s.trim().length() != 0)
                    .collect(Collectors.joining("\n")) + (showCurleyBrackets ? ("\n" + indent + "}") : "");
            return res;
        }

        private Pair<List<VariableDeclarationNode>, List<StatementNode>> splitIntoDeclsAndRest() {
            List<VariableDeclarationNode> varDecls = new ArrayList<>();
            List<StatementNode> stmts = new ArrayList<>();
            int i = 0;
            for (; i < statementNodes.size() && statementNodes.get(i) instanceof VariableDeclarationNode && !((VariableDeclarationNode) statementNodes.get(i)).hasInitExpression(); i++) {
                varDecls.add((VariableDeclarationNode) statementNodes.get(i));
            }
            for (; i < statementNodes.size(); i++) {
                stmts.add(statementNodes.get(i));
            }
            return new Pair<>(varDecls, stmts);
        }

        public void prependVariableDeclaration(String variable, Type type, boolean hasAppendValue) {
            statementNodes.add(0, new VariableDeclarationNode(location, variable, type, null, hasAppendValue));
            definedVariables.add(variable);
        }

        public void addAll(int i, List<StatementNode> statements) {
            statementNodes.addAll(i, statements);
            definedVariables.addAll(statements.stream().flatMap(s -> s.getDefinedVariables().stream()).collect(Collectors.toSet()));
            lastCondStatement = null;
            for (StatementNode statement : statements) {
                if (statements instanceof ConditionalStatementNode) {
                    lastCondStatement = (ConditionalStatementNode) statements;
                }
                if (lastCondStatement != null) {
                    lastCondStatement.setPhisCondInNodes(Collections.singletonList(statement));
                }
            }
        }

        public void add(int i, VariableDeclarationNode variableDeclarationNode) {
            addAll(i, Collections.singletonList(variableDeclarationNode));
        }

        @Override
        public Set<String> getDefinedVariables() {
            return definedVariables;
        }

        public boolean isEmpty() {
            return statementNodes.isEmpty() || statementNodes.stream().allMatch(s -> (s instanceof BlockNode && ((BlockNode) s).isEmpty()) || s instanceof IfStatementEndNode);
        }

        public BlockNode getSubBlock(int start) {
            return new BlockNode(Location.ZERO, new ArrayList<>(statementNodes.subList(start, statementNodes.size())));
        }
    }

    /**
     * A variable declaration, introduces a variable in a scope
     */
    public static class VariableDeclarationNode extends VariableAssignmentNode {

        private final Type initialType;

        final boolean hasAppendValue;

        public VariableDeclarationNode(Location location, String name, Type type, ExpressionNode initExpression, boolean hasAppendValue) {
            super(location, name, initExpression);
            this.hasAppendValue = hasAppendValue;
            this.initialType = type;
        }

        public VariableDeclarationNode(Location location, String name, Type type, ExpressionNode initExpression) {
            this(location, name, type, initExpression, false);
        }

        public VariableDeclarationNode(Location location, String name, Type type) {
            this(location, name, type, null);
        }

        public VariableDeclarationNode(Location location, Variable variable, ExpressionNode initExpression) {
            this(location, variable.name, variable.type, initExpression);
            definition = variable;
        }

        public boolean hasInitExpression() {
            return expression != null;
        }

        @Override
        public String toString() {
            String intStr = hasAppendValue && !(this instanceof AppendOnlyVariableDeclarationNode) ? "aint" : getVarType().toString();
            if (hasInitExpression()) {
                return String.format("%s %s = %s;", intStr, variable, expression);
            } else {
                return String.format("%s %s;", intStr, variable);
            }
        }

        public Type getVarType() {
            if (definition != null) {
                return definition.getType();
            }
            return initialType;
        }

        @Override
        public String type() {
            return "var_decl";
        }

        @Override
        public BlockPartNode[] getBlockParts() {
            return new BlockPartNode[]{expression};
        }

        @Override
        public List<BaseAST> children() {
            if (hasInitExpression()) {
                return Utils.makeArrayList(expression);
            }
            return Utils.makeArrayList();
        }

        @Override
        public <R> R accept(NodeVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public <R> R accept(StatementVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public Operator getOperator() {
            return null;
        }

        @Override
        public String shortType() {
            return "d";
        }

        @Override
        public String toPrettyString(String indent, String incr) {
            return indent + toString();
        }

        @Override
        public Set<String> getDefinedVariables() {
            return hasInitExpression() ? Collections.singleton(variable) : Collections.emptySet();
        }
    }

    public static class OutputVariableDeclarationNode extends VariableDeclarationNode {
        public final String secLevel;

        public OutputVariableDeclarationNode(Location location, String name, Type type, ExpressionNode initExpression, String secLevel) {
            super(location, name, type, initExpression);
            this.secLevel = secLevel;
        }

        @Override
        public String toString() {
            return String.format("%s output %s", secLevel, super.toString());
        }

        @Override
        public String type() {
            return "output_decl";
        }

        @Override
        public <R> R accept(NodeVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public <R> R accept(StatementVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public Operator getOperator(Context c) {
            return null;
        }

        @Override
        public String shortType() {
            return "do";
        }
    }

    public static class AppendOnlyVariableDeclarationNode extends VariableDeclarationNode {
        public final String secLevel;
        final boolean isInput;

        public AppendOnlyVariableDeclarationNode(Location location, String name, Type type, String secLevel, boolean isInput) {
            super(location, name, type, null, true);
            assert type == type.getTypes().INT;
            this.secLevel = secLevel;
            this.isInput = isInput;
        }

        public AppendOnlyVariableDeclarationNode(Location location, String name, Type type, String secLevel) {
            this(location, name, type, secLevel, false);
        }

        @Override
        public String toString() {
            return String.format("%s append_only %s%s", secLevel, isInput ? "input " : "", super.toString());
        }

        @Override
        public String type() {
            return "append_decl";
        }

        @Override
        public <R> R accept(NodeVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public <R> R accept(StatementVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public Operator getOperator(Context c) {
            return null;
        }

        @Override
        public String shortType() {
            return "da";
        }
    }

    public static class InputVariableDeclarationNode extends VariableDeclarationNode {
        public final String secLevel;

        public InputVariableDeclarationNode(Location location, String name, Type type, IntegerLiteralNode initExpression, String secLevel) {
            super(location, name, type, initExpression);
            this.secLevel = secLevel;
        }

        @Override
        public String type() {
            return "input_decl";
        }

        @Override
        public <R> R accept(NodeVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public <R> R accept(StatementVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public String shortType() {
            return "di";
        }

        @Override
        public String toString() {
            return String.format("%s input %s", secLevel, super.toString());
        }
    }

    public static class TmpInputVariableDeclarationNode extends VariableDeclarationNode {
        public final String secLevel;

        public TmpInputVariableDeclarationNode(Location location, String name, Type type, IntegerLiteralNode initExpression, String secLevel) {
            super(location, name, type, initExpression);
            assert type == type.getTypes().INT;
            this.secLevel = secLevel;
        }

        @Override
        public String type() {
            return "tmp_input_decl";
        }

        @Override
        public <R> R accept(NodeVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public <R> R accept(StatementVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public String shortType() {
            return "tdi";
        }

        @Override
        public String toString() {
            return String.format("%s tmp_input %s", secLevel, super.toString());
        }
    }


    /**
     * A variable assignment
     */
    public static class VariableAssignmentNode extends StatementNode {
        public Variable definition;
        public final String variable;
        public ExpressionNode expression;

        public VariableAssignmentNode(Location location, String variable, ExpressionNode expression) {
            super(location);
            this.variable = variable;
            this.expression = expression;
        }

        public VariableAssignmentNode(Location location, Variable variable, ExpressionNode expression) {
            super(location);
            this.variable = variable.name;
            this.expression = expression;
            this.definition = variable;
        }

        @Override
        public String type() {
            return "local_variable_assignment_statement";
        }

        @Override
        public String toString() {
            return String.format("%s = %s;", definition == null ? variable : definition.name, expression);
        }

        @Override
        public BlockPartNode[] getBlockParts() {
            return new BlockPartNode[]{expression};
        }

        @Override
        public <R> R accept(NodeVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public <R> R accept(StatementVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public List<BaseAST> children() {
            if (expression != null) {
                return Collections.singletonList(expression);
            }
            return Collections.emptyList();
        }

        @Override
        public String getTextualId() {
            return shortType() + ":" + (definition != null ? definition.name : variable) + location.toString();
        }

        @Override
        public String shortType() {
            return "a";
        }

        @Override
        public String toPrettyString(String indent, String incr) {
            return indent + toString();
        }

        @Override
        public Set<String> getDefinedVariables() {
            return expression != null ? Collections.singleton(variable) : Collections.emptySet();
        }
    }

    /**
     * A variable assignment
     */
    public static class ArrayAssignmentNode extends VariableAssignmentNode {
        public ExpressionNode arrayIndex;

        public ArrayAssignmentNode(Location location, String variable, ExpressionNode arrayIndex, ExpressionNode expression) {
            super(location, variable, expression);
            this.arrayIndex = arrayIndex;
        }

        public ArrayAssignmentNode(Location location, Variable variable, ExpressionNode arrayIndex, ExpressionNode expression) {
            super(location, variable, expression);
            this.arrayIndex = arrayIndex;
        }

        @Override
        public String type() {
            return "array_assignment_statement";
        }

        @Override
        public String toString() {
            return String.format("%s[%s] = %s;", definition == null ? variable : definition.name, arrayIndex, expression);
        }

        @Override
        public BlockPartNode[] getBlockParts() {
            return new BlockPartNode[]{arrayIndex, expression};
        }

        @Override
        public <R> R accept(NodeVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public <R> R accept(StatementVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public List<BaseAST> children() {
            if (expression != null) {
                return Arrays.asList(arrayIndex, expression);
            }
            return Collections.singletonList(arrayIndex);
        }

        @Override
        public String getTextualId() {
            return shortType() + ":" + (definition != null ? definition.name : variable) + location.toString();
        }

        @Override
        public String shortType() {
            return "aa";
        }

        @Override
        public String toPrettyString(String indent, String incr) {
            return indent + toString();
        }

        @Override
        public Set<String> getDefinedVariables() {
            return Collections.emptySet();
        }
    }

    /**
     * Assigment that assigns multiple variables (that are already defined) to the value of a return statement
     */
    public static class MultipleVariableAssignmentNode extends StatementNode {
        public List<Variable> definitions;
        public final List<String> variables;
        public UnpackOperatorNode expression;

        public MultipleVariableAssignmentNode(Location location, String[] variables, UnpackOperatorNode expression) {
            super(location);
            assert variables.length > 0;
            this.variables = Arrays.asList(variables);
            this.expression = expression;
        }

        public MultipleVariableAssignmentNode(Location location, List<Variable> variables, UnpackOperatorNode expression) {
            super(location);
            assert variables.size() > 0;
            this.variables = variables.stream().map(v -> v.name).collect(Collectors.toList());
            this.expression = expression;
            this.definitions = variables;
        }

        public List<String> getVariableNames() {
            if (definitions != null) {
                return definitions.stream().map(v -> v.name).collect(Collectors.toList());
            }
            return variables;
        }

        @Override
        public String type() {
            return "local_variable_assignment_statement";
        }

        @Override
        public String toString() {
            return String.format("%s = %s;", String.join(", ", getVariableNames()), expression);
        }

        @Override
        public BlockPartNode[] getBlockParts() {
            return new BlockPartNode[]{expression};
        }

        @Override
        public <R> R accept(NodeVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public <R> R accept(StatementVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public List<BaseAST> children() {
            if (expression != null) {
                return Collections.singletonList(expression);
            }
            return Collections.emptyList();
        }

        @Override
        public String getTextualId() {
            return shortType() + ":" + String.join("_", getVariableNames()) + location.toString();
        }

        @Override
        public String shortType() {
            return "a";
        }

        @Override
        public String toPrettyString(String indent, String incr) {
            return indent + toString();
        }

        @Override
        public Set<String> getDefinedVariables() {
            return new HashSet<>(getVariableNames());
        }
    }

    /**
     * An empty statement that has no effect
     */
    public static class EmptyStatementNode extends StatementNode {

        public EmptyStatementNode(Location location) {
            super(location);
        }

        @Override
        public BlockPartNode[] getBlockParts() {
            return new BlockPartNode[]{};
        }

        @Override
        public String toString() {
            return "[empty statement]";
        }

        @Override
        public String type() {
            return "empty_statement";
        }

        @Override
        public <R> R accept(NodeVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public String shortType() {
            return "e";
        }

        @Override
        public <R> R accept(StatementVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public String toPrettyString(String indent, String incr) {
            return indent;
        }

        @Override
        public Set<String> getDefinedVariables() {
            return Collections.emptySet();
        }
    }

    /**
     * A while statement
     */
    public static class WhileStatementNode extends ConditionalStatementNode {
        private final List<VariableAssignmentNode> preCondVarAss;
        public final BlockNode body;

        public WhileStatementNode(Location location, List<VariableAssignmentNode> preCondVarAss, ExpressionNode conditionalExpression, StatementNode body) {
            super(location, conditionalExpression);
            this.preCondVarAss = preCondVarAss;
            this.body = appendWhileEnd(body instanceof BlockNode ? (BlockNode) body : new BlockNode(body.location, new ArrayList<>(Collections.singletonList(body))));
            setPhisCondInNodes(preCondVarAss);
            setPhisCondInNodes(Collections.singletonList(conditionalExpression));
        }

        private BlockNode appendWhileEnd(BlockNode blockNode) {
            if (!(blockNode.getLastStatementOrNull() instanceof WhileStatementEndNode)) {
                List<StatementNode> tmp = new ArrayList<>(blockNode.statementNodes);
                tmp.add(new WhileStatementEndNode(this, location));
                return new BlockNode(blockNode.location, tmp, false);
            }
            return blockNode;
        }

        @Override
        public String toString() {
            return String.format("while [[%s]] (%s) %s", preCondVarAss.stream()
                            .map(MJNode::toString).collect(Collectors.joining(" ")),
                    conditionalExpression, body);
        }

        @Override
        public String type() {
            return "while";
        }

        @Override
        public BlockPartNode[] getBlockParts() {
            return new BlockPartNode[]{body};
        }

        @Override
        public List<BaseAST> children() {
            return Stream.concat(preCondVarAss.stream(), Stream.of(conditionalExpression, body))
                    .collect(Collectors.toList());
        }

        @Override
        public <R> R accept(NodeVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public <R> R accept(StatementVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public String shortType() {
            return "w";
        }

        @Override
        public String toPrettyString(String indent, String incr) {
            String pres = preCondVarAss.stream()
                    .map(MJNode::toString).collect(Collectors.joining(" "));
            return String.format("%swhile %s (%s) {\n%s\n%s}", indent, pres.isEmpty() ? "" : "[[" + pres + "]]",
                    conditionalExpression, body.toPrettyString(indent + incr, incr), indent);
        }

        public List<VariableAssignmentNode> getPreCondVarAss() {
            return Collections.unmodifiableList(preCondVarAss);
        }

        public void addPreCondVarAss(List<VariableAssignmentNode> asList) {
            preCondVarAss.addAll(asList);
        }

        @Override
        public Set<String> getDefinedVariables() {
            return Stream.concat(preCondVarAss.stream().map(a -> a.variable), body.getDefinedVariables().stream()).collect(Collectors.toSet());
        }
    }

    public abstract static class ConditionalStatementNode extends StatementNode {
        public final ExpressionNode conditionalExpression;

        public ConditionalStatementNode(Location location, ExpressionNode conditionalExpression) {
            super(location);
            this.conditionalExpression = conditionalExpression;
        }

        void setPhisCondInNodes(List<? extends BaseAST> nodes) {
            nodes.forEach(n -> {
                if (n instanceof MJNode) {
                    ((MJNode) n).accept(new NodeVisitor<Object>() {
                        @Override
                        public Object visit(MJNode node) {
                            return null;
                        }

                        @Override
                        public Object visit(ConditionalStatementNode condStatement) {
                            return null;
                        }

                        @Override
                        public Object visit(PhiNode phi) {
                            phi.controlDeps.add(conditionalExpression);
                            phi.controlDepStatement = ConditionalStatementNode.this;
                            return null;
                        }

                        @Override
                        public Object visit(ExpressionNode expression) {
                            visitChildrenDiscardReturn(expression);
                            return null;
                        }

                        @Override
                        public Object visit(VariableAssignmentNode assignment) {
                            visitChildrenDiscardReturn(assignment);
                            return null;
                        }
                    });
                }
            });
        }
    }

    /**
     * An if statement with two branches
     */
    public static class IfStatementNode extends ConditionalStatementNode {
        public final BlockNode ifBlock;
        public final BlockNode elseBlock;

        public IfStatementNode(Location location, ExpressionNode conditionalExpression, StatementNode ifBlock, StatementNode elseBlock) {
            super(location, conditionalExpression);
            this.ifBlock = appendIfEnd(ifBlock instanceof BlockNode ? (BlockNode) ifBlock : new BlockNode(ifBlock.location, new ArrayList<>(Collections.singletonList(ifBlock))));
            this.elseBlock = appendIfEnd(elseBlock instanceof BlockNode ? (BlockNode) elseBlock : new BlockNode(elseBlock.location, new ArrayList<>(Collections.singletonList(elseBlock))));
        }

        public IfStatementNode(Location location, ExpressionNode conditionalExpression, StatementNode ifBlock) {
            this(location, conditionalExpression, ifBlock, new BlockNode(location, new ArrayList<>()));
        }

        private BlockNode appendIfEnd(BlockNode blockNode) {
            if (!(blockNode.getLastStatementOrNull() instanceof IfStatementEndNode)) {
                List<StatementNode> tmp = new ArrayList<>(blockNode.statementNodes);
                tmp.add(new IfStatementEndNode(location));
                return new BlockNode(blockNode.location, tmp, false);
            }
            return blockNode;
        }

        @Override
        public BlockPartNode[] getBlockParts() {
            return new BlockPartNode[]{conditionalExpression, ifBlock, elseBlock};
        }

        public boolean hasElseBlock() {
            return elseBlock != null && elseBlock.statementNodes.size() > 1; // ifEnd node can be ignored
        }

        @Override
        public String type() {
            return "if";
        }

        @Override
        public String toString() {
            if (hasElseBlock()) {
                return String.format("if (%s) %s \n else %s", conditionalExpression, ifBlock, elseBlock);
            } else {
                return String.format("if (%s) %s", conditionalExpression, ifBlock);
            }
        }

        @Override
        public List<BaseAST> children() {
            return Utils.makeArrayList(conditionalExpression, ifBlock, elseBlock);
        }

        @Override
        public <R> R accept(NodeVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public <R> R accept(StatementVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public Set<String> getDefinedVariables() {
            Set<String> set = new HashSet<>(ifBlock.getDefinedVariables());
            set.addAll(elseBlock.getDefinedVariables());
            return set;
        }

        @Override
        public String shortType() {
            return "i";
        }

        @Override
        public String toPrettyString(String indent, String incr) {
            String thenStr = ifBlock.toPrettyString(indent + incr, incr);
            if (hasElseBlock()) {
                return String.format("%sif (%s)\n%s \n%selse\n%s", indent,
                        conditionalExpression, thenStr, indent,
                        elseBlock.toPrettyString(indent + incr, incr));
            } else {
                return String.format("%sif (%s)\n%s", indent, conditionalExpression, thenStr);
            }
        }
    }

    /**
     * Indicates the end of an {@link IfStatementNode}
     */
    public static class IfStatementEndNode extends StatementNode {

        public IfStatementEndNode(Location location) {
            super(location);
        }

        @Override
        public BlockPartNode[] getBlockParts() {
            return new BlockPartNode[0];
        }

        @Override
        public <R> R accept(NodeVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public String shortType() {
            return "ie";
        }

        @Override
        public <R> R accept(StatementVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public String type() {
            return "ifEnd";
        }

        @Override
        public String toPrettyString(String indent, String incr) {
            return indent;
        }
    }

    /**
     * Indicates the end of an {@link WhileStatementNode}
     */
    public static class WhileStatementEndNode extends StatementNode {

        public final WhileStatementNode whileStatement;

        public WhileStatementEndNode(WhileStatementNode whileStatement, Location location) {
            super(location);
            this.whileStatement = whileStatement;
        }

        @Override
        public BlockPartNode[] getBlockParts() {
            return new BlockPartNode[0];
        }

        @Override
        public <R> R accept(NodeVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public <R> R accept(StatementVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public String type() {
            return "whileEnd";
        }

        @Override
        public String shortType() {
            return "we";
        }

        @Override
        public String toPrettyString(String indent, String incr) {
            return indent;
        }
    }

    public static class LoopInterruptionNode extends StatementNode {

        public enum Interruption {
            BREAK,
            CONTINUE
        }

        public final Interruption interruption;

        public LoopInterruptionNode(Location location, Interruption interruption) {
            super(location);
            this.interruption = interruption;
        }

        @Override
        public String shortType() {
            return "li";
        }

        @Override
        public BlockPartNode[] getBlockParts() {
            return new BlockPartNode[0];
        }

        @Override
        public String toPrettyString(String indent, String incr) {
            switch (interruption) {
                case BREAK:
                    return indent + "break";
                case CONTINUE:
                    return indent + "continue";
            }
            return "";
        }

        public boolean isContinue() { return interruption.equals(Interruption.CONTINUE); }

        public boolean isBreak() { return interruption.equals(Interruption.BREAK); }

        @Override
        public <R> R accept(NodeVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public <R> R accept(StatementVisitor<R> visitor) {
            return visitor.visit(this);
        }
    }

    /**
     * An expression statement that essentially consists of an expression
     */
    public static class ExpressionStatementNode extends StatementNode {
        public final ExpressionNode expression;

        public ExpressionStatementNode(ExpressionNode expression) {
            super(expression.location);
            this.expression = expression;
        }

        @Override
        public BlockPartNode[] getBlockParts() {
            return new BlockPartNode[]{expression};
        }

        @Override
        public String toString() {
            return expression.toString() + ";";
        }

        @Override
        public String type() {
            return "expression_statement";
        }

        @Override
        public List<BaseAST> children() {
            return Utils.makeArrayList(expression);
        }

        @Override
        public String shortType() {
            return ";" + expression.shortType();
        }

        @Override
        public <R> R accept(NodeVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public <R> R accept(StatementVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public String toPrettyString(String indent, String incr) {
            return indent + toString();
        }
    }

    /**
     * A return statement. An empty return statement return {@code 0l}
     */
    public static class ReturnStatementNode extends StatementNode {

        /**
         * might be null
         */
        public final ExpressionNode expression;

        public ReturnStatementNode(Location location) {
            //super(new IntegerLiteralNode(location, ValueLattice.get().bot()));
            this(location, Collections.emptyList());
        }

        public ReturnStatementNode(Location location, List<ExpressionNode> expressions) {
            this(location, expressions != null && expressions.size() > 0 ? new TupleLiteralNode(location, expressions) : null);
        }

        public ReturnStatementNode(Location location, ExpressionNode expression) {
            super(location);
            this.expression = expression;
        }

        @Override
        public String toString() {
            if (hasReturnExpression()) {
                return String.format("return %s;", expression);
            } else {
                return "return;";
            }
        }

        @Override
        public String toPrettyString(String indent, String incr) {
            if (hasReturnExpression()) {
                return String.format("%sreturn %s;", indent, expression);
            } else {
                return indent + "return;";
            }
        }

        @Override
        public String type() {
            return "return_statement";
        }

        public boolean hasReturnExpression() {
            return expression != null;
        }

        @Override
        public List<BaseAST> children() {
            return hasReturnExpression() ? Collections.singletonList(expression) : Collections.emptyList();
        }

        @Override
        public <R> R accept(NodeVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public <R> R accept(StatementVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public String shortType() {
            return "r";
        }

        @Override
        public BlockPartNode[] getBlockParts() {
            return hasReturnExpression() ? new BlockPartNode[]{expression} : new BlockPartNode[0];
        }
    }

    /**
     * Base node for all expressions
     */
    public static abstract class ExpressionNode extends BlockPartNode {

        /**
         * type assigned by the name resolution
         */
        public Type type;

        public ExpressionNode(Location location) {
            super(location);
        }

        public ExpressionNode setExpressionType(Type type) {
            this.type = type;
            return this;
        }

        @Override
        public <R> R accept(NodeVisitor<R> visitor) {
            return visitor.visit(this);
        }

        public <R> R accept(ExpressionVisitor<R> visitor) {
            return visitor.visit(this);
        }

        public <R, A> R accept(ExpressionVisitorWArgs<R, A> visitor, A argument) {
            return visitor.visit(this, argument);
        }

        @Override
        public abstract Operator getOperator();
    }

    /**
     * A binary operator expression
     */
    public static class BinaryOperatorNode extends ExpressionNode {
        public final ExpressionNode left;
        public final ExpressionNode right;
        public final LexerTerminal operator;
        public final Operator op;

        public BinaryOperatorNode(ExpressionNode left, ExpressionNode right, LexerTerminal operator) {
            super(left.location);
            Objects.requireNonNull(left);
            Objects.requireNonNull(right);
            Objects.requireNonNull(operator);
            this.left = left;
            this.right = right;
            this.operator = operator;
            op = getOperator(operator);
        }

        static Operator getOperator(LexerTerminal operator) {
            switch (operator) {
                case BAND:
                    return Operator.AND;
                case BOR:
                    return Operator.OR;
                case XOR:
                    return Operator.XOR;
                case EQUALS:
                    return Operator.EQUALS;
                case UNEQUALS:
                    return Operator.UNEQUALS;
                case LOWER:
                    return Operator.LESS;
                case PLUS:
                    return Operator.ADD;
                case MULTIPLY:
                    return Operator.MULTIPLY;
                case DIVIDE:
                    return Operator.DIVIDE;
                case LEFT_SHIFT:
                    return Operator.LEFT_SHIFT;
                case RIGHT_SHIFT:
                    return Operator.RIGHT_SHIFT;
                case MODULO:
                    return Operator.MODULO;
                case APPEND:
                    return Operator.APPEND;
                case AND:
                    return Operator.LOGICAL_AND;
                case OR:
                    return Operator.LOGICAL_OR;
                default:
                    return null;
            }
        }

        @Override
        public BlockPartNode[] getBlockParts() {
            return new BlockPartNode[]{left, right};
        }

        @Override
        public String toString() {
            return String.format("(%s %s %s)", left, operator.description.replace("\\", ""), right);
        }

        @Override
        public String type() {
            return "binary_operator";
        }

        @Override
        public List<BaseAST> children() {
            return Utils.makeArrayList(left, right);
        }

        @Override
        public <R> R accept(NodeVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public <R> R accept(ExpressionVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public <R, A> R accept(ExpressionVisitorWArgs<R, A> visitor, A argument) {
            return visitor.visit(this, argument);
        }

        @Override
        public Operator getOperator() {
            return op;
        }

        @Override
        public String shortType() {
            return operator.representation.replace("\\", "");
        }

    }

    /**
     * An unary operator expression
     */
    public static class UnaryOperatorNode extends ExpressionNode {
        public final ExpressionNode expression;
        public final LexerTerminal operator;
        public final Operator op;

        UnaryOperatorNode(ExpressionNode expression, LexerTerminal operator, Operator op) {
            super(expression.location);
            this.operator = operator;
            this.expression = expression;
            this.op = op;
        }

        public UnaryOperatorNode(ExpressionNode expression, LexerTerminal operator) {
            super(expression.location);
            this.expression = expression;
            this.operator = operator;
            switch (operator) {
                case INVERT:
                case TILDE:
                    op = Operator.NOT;
                    break;
                case MULTIPLY:
                    op = Operator.UNPACK;
                    break;
                default:
                    throw new UnsupportedOperationException(operator.toString());
            }
        }

        @Override
        public String toString() {
            return operator.representation + expression;
        }

        @Override
        public String type() {
            return "unary_operator";
        }

        @Override
        public BlockPartNode[] getBlockParts() {
            return new BlockPartNode[]{expression};
        }

        @Override
        public List<BaseAST> children() {
            return Utils.makeArrayList(expression);
        }

        @Override
        public <R> R accept(NodeVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public <R> R accept(ExpressionVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public <R, A> R accept(ExpressionVisitorWArgs<R, A> visitor, A argument) {
            return visitor.visit(this, argument);
        }

        @Override
        public Operator getOperator() {
            return op;
        }

        @Override
        public String shortType() {
            return operator.representation;
        }
    }

    public static class UnpackOperatorNode extends UnaryOperatorNode {

        public UnpackOperatorNode(ExpressionNode expression) {
            super(expression, MULTIPLY);
            assert !(expression instanceof UnpackOperatorNode);
        }

        @Override
        public <R> R accept(NodeVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public <R> R accept(ExpressionVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public <R, A> R accept(ExpressionVisitorWArgs<R, A> visitor, A argument) {
            return visitor.visit(this, argument);
        }
    }

    /**
     * Implements the bit place operator. It only works for constant indices, as it is primarily intended to be used
     * to create circuits.
     */
    public static class BitPlaceOperatorNode extends UnaryOperatorNode {

        public final long index;

        public BitPlaceOperatorNode(ExpressionNode expression, long index) {
            super(expression, BIT_PLACE_OP, new Operator.PlaceBit(index));
            assert index > 0;
            this.index = index;
        }

        @Override
        public String toString() {
            return String.format("[%d]%s", index, expression);
        }

        @Override
        public String shortType() {
            return String.format("[%d]·", index);
        }

        @Override
        public <R> R accept(NodeVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public <R> R accept(ExpressionVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public <R, A> R accept(ExpressionVisitorWArgs<R, A> visitor, A argument) {
            return visitor.visit(this, argument);
        }

        @Override
        public String toPrettyString(String indent, String incr) {
            return indent + String.format("[%d]%s", index, expression.toPrettyString());
        }
    }

    public static class BracketedAccessOperatorNode extends BinaryOperatorNode {

        public BracketedAccessOperatorNode(ExpressionNode left, ExpressionNode right) {
            super(left, right, BRACKET_ACCESS_OP);
        }

        @Override
        public String toString() {
            return String.format("(%s[%s])", left, right);
        }

        @Override
        public String toPrettyString(String indent, String incr) {
            return indent + String.format("(%s[%s])", left.toPrettyString(), right.toPrettyString());
        }

        @Override
        public String shortType() {
            return String.format("·[·]");
        }

        @Override
        public <R> R accept(NodeVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public <R> R accept(ExpressionVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public <R, A> R accept(ExpressionVisitorWArgs<R, A> visitor, A argument) {
            return visitor.visit(this, argument);
        }

        @Override
        public Operator getOperator() {
            return new Operator.SelectBit();
        }
    }

    /**
     * Arguments for a method call
     */
    public static class ArgumentsNode extends BlockPartNode {
        public List<ExpressionNode> arguments;

        public ArgumentsNode(Location location, List<ExpressionNode> arguments) {
            super(location);
            this.arguments = arguments;
        }

        @Override
        public BlockPartNode[] getBlockParts() {
            return arguments.toArray(new BlockPartNode[]{});
        }

        @Override
        public String type() {
            return "arguments";
        }

        @Override
        public String toString() {
            return Utils.toString(", ", arguments);
        }

        @Override
        public List<BaseAST> children() {
            return Arrays.asList(arguments.toArray(new BaseAST[]{}));
        }

        @Override
        public <R> R accept(NodeVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public String shortType() {
            return "args";
        }

        public int size() {
            return arguments.size();
        }

        public ExpressionNode get(int i) {
            return arguments.get(i);
        }
    }

    /**
     * Global arguments for a method call
     */
    public static class GlobalVariablesNode extends BlockPartNode {
        public final Map<String, Pair<String, String>> globalVarSSAVars;

        public GlobalVariablesNode(Location location, Map<String, Pair<String, String>> globalVarSSAVars) {
            super(location);
            this.globalVarSSAVars = globalVarSSAVars;
        }

        @Override
        public String type() {
            return "arguments";
        }

        @Override
        public String toString() {
            return globalVarSSAVars.entrySet().stream()
                    .map(e -> String.format("%s -> %s -> %s", e.getKey(), e.getValue().first, e.getValue().second))
                    .collect(Collectors.joining(", "));
        }

        @Override
        public BlockPartNode[] getBlockParts() {
            return new BlockPartNode[0];
        }

        @Override
        public <R> R accept(NodeVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public String shortType() {
            return "globals";
        }
    }

    /**
     * Base class for all primary expressions
     */
    public static abstract class PrimaryExpressionNode extends ExpressionNode {
        public PrimaryExpressionNode(Location location) {
            super(location);
        }

        @Override
        public BlockPartNode[] getBlockParts() {
            return new BlockPartNode[]{};
        }

        @Override
        public <R> R accept(NodeVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public <R> R accept(ExpressionVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public <R, A> R accept(ExpressionVisitorWArgs<R, A> visitor, A argument) {
            return visitor.visit(this, argument);
        }
    }

    public static class TypeNode extends MJNode {

        public final Type type;

        protected TypeNode(Location location, Type type) {
            super(location);
            this.type = type;
        }

        @Override
        public <R> R accept(NodeVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public String toString() {
            return type.toString();
        }

        @Override
        public String toPrettyString(String indent, String incr) {
            return indent + toString();
        }

        @Override
        public String shortType() {
            return "t";
        }

        @Override
        public String type() {
            return "type";
        }
    }

    /**
     * An integer literal expression
     */
    public static class IntegerLiteralNode extends PrimaryExpressionNode {
        public final Value value;
        public final Operator op;

        public IntegerLiteralNode(Location location, Value value) {
            super(location);
            this.value = value;
            this.op = new Operator.LiteralOperator(value);
        }

        @Override
        public String toString() {
            return value.toLiteralString();
        }

        @Override
        public String type() {
            return "integer_literal";
        }

        @Override
        public List<BaseAST> children() {
            return Utils.makeArrayList(new WrapperNode<>(location, value));
        }

        @Override
        public <R> R accept(NodeVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public <R> R accept(ExpressionVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public <R, A> R accept(ExpressionVisitorWArgs<R, A> visitor, A argument) {
            return visitor.visit(this, argument);
        }

        @Override
        public Operator getOperator() {
            return op;
        }

        @Override
        public String shortType() {
            return "i";
        }
    }

    public static IntegerLiteralNode literal(int num) {
        return new IntegerLiteralNode(new Location(0, 0), vl.parse(num));
    }

    public static abstract class TupleLikeLiteralNode extends PrimaryExpressionNode {
        public final List<ExpressionNode> elements;
        private final char left;
        private final char right;

        public TupleLikeLiteralNode(Location location, List<ExpressionNode> elements, char left, char right) {
            super(location);
            this.elements = elements;
            this.left = left;
            this.right = right;
        }

        @Override
        public String toString() {
            if (elements.size() == 1) {
                return String.format("%s%s,%s", left, elements.get(0), right);
            }
            return String.format("%s%s%s", left, elements.stream().map(Object::toString).collect(Collectors.joining(", ")), right);
        }

        @Override
        public List<BaseAST> children() {
            return new ArrayList<>(elements);
        }

        @Override
        public <R> R accept(NodeVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public <R> R accept(ExpressionVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public <R, A> R accept(ExpressionVisitorWArgs<R, A> visitor, A argument) {
            return visitor.visit(this, argument);
        }
    }

    public static class TupleLiteralNode extends TupleLikeLiteralNode {
        public TupleLiteralNode(Location location, List<ExpressionNode> elements) {
            super(location, elements, '(', ')');
        }

        @Override
        public String type() {
            return "tuple_literal";
        }

        @Override
        public <R> R accept(NodeVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public <R> R accept(ExpressionVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public <R, A> R accept(ExpressionVisitorWArgs<R, A> visitor, A argument) {
            return visitor.visit(this, argument);
        }

        @Override
        public String shortType() {
            return "tl";
        }

        @Override
        public Operator getOperator() {
            return Operator.TUPLE_LITERAL;
        }
    }

    public static class ArrayLiteralNode extends TupleLikeLiteralNode {

        public ArrayLiteralNode(Location location, List<ExpressionNode> elements) {
            super(location, elements, '{', '}');
        }

        @Override
        public String type() {
            return "array_literal";
        }

        @Override
        public <R> R accept(NodeVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public <R> R accept(ExpressionVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public String shortType() {
            return "al";
        }

        @Override
        public Operator getOperator() {
            return Operator.ARRAY_LITERAL;
        }
    }

    /**
     * A method invocation
     */
    public static class MethodInvocationNode extends PrimaryExpressionNode {
        public MethodNode definition;
        public final String method;
        public final ArgumentsNode arguments;
        public final GlobalVariablesNode globals;
        Map<Variable, Pair<Variable, Variable>> globalDefs = null;

        public MethodInvocationNode(Location location, String method, ArgumentsNode arguments, GlobalVariablesNode globals) {
            super(location);
            this.method = method;
            this.arguments = arguments;
            this.globals = globals;
        }

        public MethodInvocationNode(Location location, String method, ArgumentsNode arguments) {
            this(location, method, arguments, new GlobalVariablesNode(location, new HashMap<>()));
        }

        public MethodInvocationNode(Location location, MethodNode method, ArgumentsNode arguments) {
            this(location, method.name, arguments, new GlobalVariablesNode(location, new HashMap<>()));
            this.definition = method;
            this.type = method.returnType;
        }

        public MethodInvocationNode(Location location, MethodNode method, ExpressionNode arg, List<ExpressionNode> args) {
            this(location, method, new ArgumentsNode(location, Stream.concat(Stream.of(arg), args.stream()).collect(Collectors.toList())));
        }

        public MethodInvocationNode(Location location, MethodNode method, ExpressionNode arg) {
            this(location, method, new ArgumentsNode(location, Collections.singletonList(arg)));
        }

        @Override
        public String toString() {
            return String.format("%s%s(%s)", method, globals.globalVarSSAVars.size() > 0 ? String.format("[[%s]]", globals) : "", arguments);
        }

        @Override
        public String type() {
            return "local_method_invocation";
        }

        @Override
        public BlockPartNode[] getBlockParts() {
            return new BlockPartNode[]{arguments};
        }

        @Override
        public List<BaseAST> children() {
            return (List<BaseAST>) (List<?>) arguments.arguments;
        }

        @Override
        public <R> R accept(NodeVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public <R> R accept(ExpressionVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public <R, A> R accept(ExpressionVisitorWArgs<R, A> visitor, A argument) {
            return visitor.visit(this, argument);
        }

        @Override
        public String getTextualId() {
            return String.format("%s(c %s)", super.getTextualId(), method);
        }

        @Override
        public Operator getOperator() {
            return new Operator.MethodInvocation(this);
        }

        @Override
        public String shortType() {
            return "mi";
        }
    }

    /**
     * Access of a variable
     */
    public static class VariableAccessNode extends PrimaryExpressionNode {
        public String ident;
        public Variable definition;
        ExpressionNode definingExpression;

        public VariableAccessNode(Location location, String ident) {
            super(location);
            this.ident = ident;
        }

        public VariableAccessNode(Location location, Variable variable) {
            super(location);
            this.ident = variable.name;
            this.definition = variable;
            this.type = definition.type;
        }

        @Override
        public String toString() {
            return definition == null ? ident : definition.name;
        }

        @Override
        public String type() {
            return "identifier_literal";
        }

        @Override
        public List<BaseAST> children() {
            if (definingExpression != null) {
                return Utils.makeArrayList(definingExpression);
            }
            return Utils.makeArrayList();
        }

        @Override
        public <R> R accept(NodeVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public <R> R accept(ExpressionVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public <R, A> R accept(ExpressionVisitorWArgs<R, A> visitor, A argument) {
            return visitor.visit(this, argument);
        }

        @Override
        public Operator getOperator() {
            throw new NildumuError(String.format("No operator defined for %s", this));
        }

        @Override
        public String getTextualId() {
            return shortType() + ":" + (definition != null ? definition.name : ident) + location.toString();
        }

        @Override
        public String shortType() {
            return "ac";
        }

    }

    public static class ParameterAccessNode extends VariableAccessNode {

        public ParameterAccessNode(Location location, String ident) {
            super(location, ident);
        }

        @Override
        public <R> R accept(NodeVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public <R> R accept(ExpressionVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public <R, A> R accept(ExpressionVisitorWArgs<R, A> visitor, A argument) {
            return visitor.visit(this, argument);
        }

        @Override
        public Operator getOperator() {
            return new Operator.ParameterAccess(this.definition);
        }

        @Override
        public String shortType() {
            return "pac";
        }
    }

    /**
     * A phi node to join two variables from different control paths
     */
    public static class PhiNode extends ExpressionNode {
        public List<ExpressionNode> controlDeps;
        public final List<VariableAccessNode> joinedVariables;
        public ConditionalStatementNode controlDepStatement;

        public PhiNode(Location location, List<ExpressionNode> controlDeps, ArrayList<VariableAccessNode> joinedVariables) {
            super(location);
            this.controlDeps = controlDeps;
            this.joinedVariables = joinedVariables;
        }

        public PhiNode(Location location, List<ExpressionNode> controlDeps, List<Variable> joinedVariables) {
            super(location);
            this.controlDeps = controlDeps;
            this.joinedVariables = joinedVariables.stream().map(v -> {
                VariableAccessNode n = new VariableAccessNode(location, v.name);
                n.definition = v;
                return n;
            }).collect(Collectors.toList());
        }

        public PhiNode(Location location, List<String> varsToJoin) {
            this(location, new ArrayList<>(),
                    varsToJoin.stream().map(Variable::new).collect(Collectors.toList()));
        }

        @Override
        public List<BaseAST> children() {
            List<BaseAST> children = new ArrayList<>(joinedVariables);
            children.addAll(controlDeps);
            return children;
        }

        @Override
        public BlockPartNode[] getBlockParts() {
            return new BlockPartNode[0];
        }

        @Override
        public <R> R accept(NodeVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public String type() {
            return "ɸ";
        }

        @Override
        public String toString() {
            return String.format("phi(%s)",
                    joinedVariables.stream().map(v -> v.ident)
                            .collect(Collectors.joining(", ")));
        }

        @Override
        public String getTextualId() {
            return toString();
        }

        @Override
        public <R> R accept(ExpressionVisitor<R> visitor) {
            return visitor.visit(this);
        }

        @Override
        public <R, A> R accept(ExpressionVisitorWArgs<R, A> visitor, A argument) {
            return visitor.visit(this, argument);
        }

        @Override
        public Operator getOperator() {
            if (joinedVariables.size() == 2) {
                return Operator.PHI;
            }
            return Operator.PHI_GENERIC;
        }

        /*@Override
        public String getTextualId() {
            return shortType() + "(" + joinedVariables.stream().map(MJNode::getTextualId).collect(Collectors.joining(",")) + ")";
        }*/

        @Override
        public String shortType() {
            return "ϕ";
        }

        public void alterCondDeps(Function<ExpressionNode, ExpressionNode> mapper) {
            controlDeps = controlDeps.stream().map(mapper)
                    .collect(Collectors.toList());
        }
    }

    /**
     * A basic error message
     */
    public static class MJError extends SWPException {

        public MJError(String message) {
            super(message);
        }
    }
}
