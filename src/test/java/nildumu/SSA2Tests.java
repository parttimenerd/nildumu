package nildumu;

import nildumu.mih.MethodInvocationHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.logging.Level;

import static nildumu.Checks.checkAndThrow;
import static nildumu.FunctionTests.parse;
import static nildumu.Parser.generator;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests to check especially the SSA generation
 *
 * @see SSAResolution2
 */
public class SSA2Tests {

    @Test
    public void basicRedefinitions(){
        String program = "int a = 0; a = 3;";
        toSSA(program);
        process(program).val("a1", "3").run();
    }

    @Test
    public void basicVariableUsage(){
        String program = "int a = 0; int b = a; a = 3; int c = a;";
        toSSA(program);
        process(program).val("b", "0").val("c", "3").run();
    }

    @Test
    public void basicIf(){
        String program = "int a = 0; if (a == 0) { a = 1; } else {a = 0;}";
        toSSA(program);
        process(program).val("a1", "1").run();
    }

    @Test
    public void basicWhile(){
        String program = "int a = 0; while (a != 0) { a = 1; }";
        toSSA(program);
        process(program).val("a1", "0").run();
    }

    @Test
    public void append(){
        toSSA("bit_width 3; l append_only int abc; abc = abc @ 3");
    }

    /**
     <code>
     int r = 0;
     if (true) {
        r = 3;
     }
     if (true) {
        r = 1;
     }
     </code>
     should result in
     <code>
     int r = 0;
     if (1) {
        int r1 = 3;
     } else {

     }
     int r2 = ɸ[1](r1, r);
     if (1) {
        int r3 = 1;
     } else {

     }
     int r4 = ɸ[1](r3, r2);
     </code>
     with the focus on the last line
     */
    @Test
    public void testTwoIfsInARow(){
        assertEquals("r4 = phi(r3, r2);", toSSA("int r = 0;\n" +
                "     if (true) {\n" +
                "        r = 3;\n" +
                "     }\n" +
                "     if (true) {\n" +
                "        r = 1;\n" +
                "     }").globalBlock.getLastStatementOrNull().toPrettyString());
    }

    /**
     <code>
     int r = 0;
     while (true) {
        r = 3;
     }
     while (true) {
        r = 1;
     }
     </code>
     should result in
     <code>
     int r = 0;
     while (1) {
        int r11 = ɸ[1](r1, r);
        int r1 = 3;
     }
     int r2 = ɸ[1](r1, r);
     while (1) {
        int r31 = ɸ[1](r3, r2);
        int r3 = 1;
     }
     int r4 = ɸ[1](r3, r2);
     </code>
     with the focus on the last line
     */
    @Test
    public void testTwoWhilesInARow(){
        assertEquals("int a = r6;", toSSA("int r = 0;\n" +
                "     while (true) {\n" +
                "        r = 3;\n" +
                "     }\n" +
                "     while (true) {\n" +
                "        r = 1;\n" +
                "     }" +
                "     int a = r", true, false).globalBlock.getLastStatementOrNull().toPrettyString());
    }

    @Test
    public void testNestedBlocks(){
        assertTrue(toSSA("int a = 0; if (1 == 2){ { a = 1}} int b = a").globalBlock.toPrettyString().contains("phi("));
    }

   /* @Test
    public void testWhileSetsExpr(){
        List<Parser.StatementNode> stmts = ((Parser.WhileStatementNode)toSSA("h input int h = 0b0u;\n" +
                "int x = 0;\n" +
                "while (h == 0){\n" +
                "\tx = x | 0b11;\n" +
                "}\n" +
                "l output int o = x;").globalBlock.getLastStatementOrNull()).body.statementNodes;
        assertEquals(((Parser.VariableAssignmentNode)stmts.get(1)).definition, );
    }*/

    /**
     * <code>
     *     int _3_bla(int a) {
     *    int r = 1;
     *    while (a != 0){
     *       a = 0
     *    }
     *    return r;
     * }
     * h input int h = 0b00u;
     * l output int o = _3_bla(h);
     * </code>
     */
    @Test
    public void testWhileWithBasicAssignmentInLoop(){
        toSSA("int _3_bla(int a) {\n" +
                "   int r = 1;\n" +
                "   while (a != 0){\n" +
                "      a = 0\n" +
                "   }\n" +
                "   return r;\n" +
                "}\n" +
                "h input int h = 0b00u;\n" +
                "l output int o = _3_bla(h);");
    }

    @ParameterizedTest
    @ValueSource(strings = {"int a1 = 0; a1 = 1", "int a11 = 0; a11 = 1"})
    public void testDifferentVariableNames(String program){
        toSSA(program);
    }

    @Test
    public void testVariableNotDefinedOutsideOfWhile(){
        toSSA("while (1) {int a = 0; a = a + 1; }");
    }

    @Test
    public void testBasicNestedCalls(){
        toSSA("print(input())").globalBlock.children().stream()
                .filter(b -> b.toPrettyString().startsWith("print[")).forEach(b -> {
            Parser.MethodInvocationNode print = (Parser.MethodInvocationNode)b.children().get(0);
            Parser.MethodInvocationNode input = (Parser.MethodInvocationNode)print.arguments.get(0);
            assertEquals(input.globals.globalVarSSAVars.get("input").second,
                    print.globals.globalVarSSAVars.get("input").first);
        });
    }

    @Test
    public void testInputCallsInLoopWithoutLoopTransformation() {
        Parser.WhileStatementNode whileStatement =
                (Parser.WhileStatementNode) toSSA("while (1) { input() }", true, false).globalBlock.children().stream()
                        .filter(a -> a.toPrettyString().startsWith("while")).findAny().get();
        assertEquals(whileStatement.getPreCondVarAss().get(0).definition,
                ((Parser.MethodInvocationNode) ((Parser.ExpressionStatementNode)
                        whileStatement.body.statementNodes.get(0)).expression)
                        .globalDefs.values().iterator().next().first);
    }

    @Test
    public void testSSAInFunctionNamesOverlapping() {
        parse("use_sec basic;\n" +
                "bit_width 32;\n" +
                "int set(int a1, int a11){\n" +
                "  a1 = a11;\n" +
                "  return a11;\n" +
                "}\n" +
                "var x = set(0, 1);").val("x", 1).run();
    }

    public static Parser.ProgramNode toSSA(String program) {
        return toSSA(program, true);
    }

    public static Parser.ProgramNode toSSA(String program, boolean log) {
        return toSSA(program, log, true);
    }

    public static Parser.ProgramNode toSSA(String program, boolean log, boolean useLoopTransformer) {
        Context.LOG.setLevel(Level.FINE);
        Parser.MJNode.resetIdCounter();
        Lattices.Bit.resetNumberOfCreatedBits();
        Parser.ProgramNode programNode = (Parser.ProgramNode) generator.parse(program);
        if (log) {
            if (log) {
                System.out.println(programNode.toPrettyString());
                System.out.println("---");
            }
        }
        if (useLoopTransformer) {
            LoopTransformer.process(programNode);
            program = programNode.toPrettyString();
            if (log) {
                System.out.println(program);
                System.out.println("---");
            }
        }
        Parser.MJNode.resetIdCounter();
        Lattices.Bit.resetNumberOfCreatedBits();
        programNode = (Parser.ProgramNode) generator.parse(program);
        SSAResolution2.process(programNode);
        if (log) {
            System.out.println(programNode.toPrettyString());
        }
        Parser.ProgramNode resolvedProgram = (Parser.ProgramNode) Parser.generator.parse(programNode.toPrettyString());
        new NameResolution(resolvedProgram).resolve();
        //checkAndThrow(resolvedProgram);
        Parser.ProgramNode transformedProgram = new MetaOperatorTransformator(resolvedProgram.context.maxBitWidth, false).process(resolvedProgram);
        checkAndThrow(transformedProgram);
        if (log) {
            System.out.println("-- to --");
            System.out.println(transformedProgram.toPrettyString());
        }
        return transformedProgram;
    }

    public static ContextMatcher process(String program){
        return new ContextMatcher(Processor.process(toSSA(program, false), Context.Mode.LOOP,
                MethodInvocationHandler.parse("inlining"), false));
    }
}
