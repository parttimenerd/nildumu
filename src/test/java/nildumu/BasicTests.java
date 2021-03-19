package nildumu;

import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static nildumu.Lattices.*;
import static nildumu.Processor.process;

public class BasicTests {

    @Test
    public void testParser() {
        process("h input int l = 0b0u; l output int o = l;");
    }

    @Test
    public void testParsingRepeatedBitNotation() {
        parse("h input int l = 0b0u{2};").val("l", "0b0uu").run();
    }

    @Test
    public void testParsingRepeatedBitNotation2() {
        parse("int l = 0b0{2};").val("l", "0b00").run();
    }

    @Test
    public void testParser2() {
        process("if (1) {}");
    }

    @Test
    public void testParser3() {
        process("if (1) {} if (1) {}");
    }


    @Test
    public void testSimpleAssignment(){
        parse("int x = 1").val("x", 1).run();
        parse("int x = -10").val("x", -10).run();
    }

    @Test
    public void testBitSelect() {
        parse("int x = 0b001; int y = x[1]").val("y", -1).run();
    }

    @Test
    public void testInputAssigment(){
        parse("l input int l = 0b0u").hasInput("l").val("l", "0b0u").run();
    }

    @Test
    public void testChangingSecLattice(){
        parse("use_sec diamond; n input int l = 0b0u").val("l", "0b0u").hasInputSecLevel("l", DiamondSecLattice.MID2).run();
    }

    @Test
    public void testBasicOutputAssignment(){
        parse("h output int o = 0").hasOutput("o").val("o", 0).hasOutputSecLevel("o", BasicSecLattice.HIGH).run();
   }

    @Test
    public void testBasicOutputAssignment2(){
       parse("l input int l = 0b0u; h output int o = l;").val("o", "0b0u").run();
    }

    @Test
    public void testBasicProgramLeakage(){
        parse("h output int o = 0").leakage(l -> l.leaks("h", 0)).run();
    }

    @Test
    public void testBasicProgramLeakage2(){
        parse("h input int h = 0b0u; l output int o = h;").leakage(l -> l.leaks("l", 1)).run();
    }

    @Test
    public void testBasicIf(){
        parse("int x = 0; if (1) { x = 1 }").val("x1", 1).run();
    }

    @ParameterizedTest
    @CsvSource({
            "'int x = 0 | 1','1'",
            "'int x = 0b00 | 0b11','0b11'",
            "'l input int l = 0b0u; int x = l | 0b11','0b11'",
            "'l input int l = 0b0u; int x = l & 0b11','0b0u'"
    })
    public void testBitwiseOps(String program, String xVal) {
        parse(program).val("x", xVal).run();
    }

    @Test
    public void testBitwiseOps2() {
        parse("h input int l = 0b0u;\n" +
                "int x = 2 & l;").val("x", "0b00").run();
    }

    @Test
    public void testLessThanWithConstants() {
        parse("int x = 1 < 2;").val("x", "1").run();
    }

    @Test
    public void testPlusOperator() {
        parse("int x = 1 + 0").val("x", "1").run();
    }

    /**
     h input int l = 0b0u;
     int x = 0;
     if (l){
     x = 1;
     } else {
     x = 0;
     }
     l output int o = x;
     */
    @Test
    public void testIf(){
        parse("h input int l = 0b0u; \n" +
                "int x = 0;\n" +
                "if (l){\n" +
                "\tx = 1;\n" +
                "} else {\n" +
                "\tx = 0;\n" +
                "}\n" +
                "l output int o = x;").val("o", vm -> vm.bit(1, B.U)).leakage(l -> l.leaks("l", 1)).run();
    }

    @Test
    public void testIfWithNewLines(){
        parse("h input int l = 0b0u; \n" +
                "int x = 0;\n" +
                "if (l){\n" +
                "\tx = 1;\n" +
                "} else {\n\n" +
                "\tx = 0;\n" +
                "}\n\n" +
                "l output int o = x;").val("o", vm -> vm.bit(1, B.U)).leakage(l -> l.leaks("l", 1)).run();
    }

    @ParameterizedTest
    @ValueSource(strings = {"high input int h = 0b0u;\n" +
            "    if (h[1]) {\n" +
            "    }",
            "high input int h = 0b0u;\n" +
                    "    if (h[1] == 1) {\n" +
                    "    }",
            "high input int h = 0b0u;\n" +
            "    if (h[1] == h[1]) {\n" +
            "    }"})
    public void testBitSelectInConditionDoesntThrowExceptions(String program){
        parse(program);
    }

    @Test
    public void testMasking(){
        String program = "int O = 0;\n" +
                "h input int S = 0buu;\n" +
                "S = S & 0b00;\n" +
                "if (S <= 1) {\n" +
                "\tO = S;\n" +
                "}\n" +
                "l output int o = O;";
        parse(program).leaks(0).run();
    }

    @ParameterizedTest
    @CsvSource({"'int x = 0b0', 1", "'int x = 0b00', 2", "'int x = 0b000', 3", "'l input int x = 0b0u', 2", "'int x = 1', 2"})
    public void testBitWidthDetection(String program, int expectedBitWidth){
        parse(program).bitWidth(expectedBitWidth).run();
    }

    @Test
    public void testParsePhi(){
        Assert.assertEquals(Parser.PhiNode.class,
                ((Parser.VariableAssignmentNode)((Parser.ProgramNode)Parser.generator.parse("int a = phi(z, l)"))
                .globalBlock.statementNodes.get(0)).expression.getClass());
    }

    @ParameterizedTest
    @ValueSource(strings = {"while (a == h){}", "while [[]] (a == h) {}", "while [[a = phi(a, b)]] (a == h) {}"})
    public void testWhileParsing(String program){
        Parser.generator.parse(program);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "while (1) {int a; a = a + 1;}",
            "int a; while (1) {int a; a = a + 1;}"
    })
    public void testNameResolution(String program){
        Parser.generator.parse(program);
    }

    @Test
    public void testAnd() {
        parse("int x = 0 && 1").val("x", 0).run();
        parse("int x = 1 && 1").val("x", 1).run();
    }

    @Test
    public void testOr() {
        parse("int x = 1 || 2").val("x", 1).run();
    }

    public ContextMatcher parse(String program){
        return new ContextMatcher(process(program));
    }

}
