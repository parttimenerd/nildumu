package nildumu;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.logging.Level;

import static com.google.common.truth.Truth.assertThat;
import static nildumu.Context.LOG;
import static nildumu.FunctionTests.parse;
import static nildumu.SSA2Tests.toSSA;
import static org.junit.jupiter.api.Assertions.*;

@Disabled
public class AppendTests {

    /** Use only solvers that support the appending (the network flow based does not) */
    @Test
    public void testSelectionOfSolvers(){
        String runProgram = "bit_width 3; h input int h = 0buuu; while (h == 0) { print(0) }";
        parse(runProgram).leaks(1).run();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "l append_only int abc",
            "bit_width 3; l append_only int abc; abc = abc @ 3"
    })
    public void testParseBasic(String str) {
        parse(str);
    }

    @Test
    public void testBasicAppend(){
        String program = "bit_width 3; l append_only int abc; abc = abc @ 3";
        parse(program).val("abc", "0b011").print().run();
    }

    @Test
    public void testBasicIf(){
        String program = "bit_width 3; l append_only int abc; l input int l = 0buuu; if (l == 3){abc = abc @ 3} abc = abc @ 3";
        System.out.println(toSSA(program, false));
        parse(program).val("abc", "0b011nnn").print().run();
    }

    @Test
    public void testSimpleMethod(){
        parse("bit_width 3; l append_only int abc; int print_(){ abc = abc @ 3 } print_()").val("abc", "0b011").run();
    }

    @Test
    public void testSimpleMethodBranched(){
        parse("bit_width 2; l append_only int abc; l input int l = 0buu; if (l){abc = l}").val("abc", "0bnn").print().run();
    }

    @Test
    public void testBasicLeakage(){
        parse("bit_width 2; l append_only int abc; h input int h = 0buu; if (h){abc = 1}").leaks(1).run();
    }

    @Test
    public void testPrint(){
        parse("bit_width 2; h input int h = 0buu; if (h){print(0)}").leaks(1).run();
    }

    @ParameterizedTest
    @CsvSource({
            "'while (1) { print(0) }', 0",
            "'while (h == 0) { print(0) }', 1",
            "'while (h == 0) { print(h) }', 1",
            "'while (h > 0) { print(h) }', 3"
    })
    public void testBasicPrintLoop(String program, double leakage){
        String runProgram = "bit_width 3; h input int h = 0buuu; " + program;
        System.out.println(toSSA(runProgram, false).toPrettyString());
            parse(runProgram).leaks(leakage).run();
    }

    @ParameterizedTest
    @CsvSource({
            "'int i = 0; while (h != i) { print(0); i = 0 }', 2",
            "'int i = 0; while (h != i) { print(0); i = h }', 2",
            "'int i = 0; while (h != i) { print(0); i = i + 1 }', 2",
            "'int i = 0; while (h != i) { print(i); i = h }', 2",
            "'int i = 0; while (h != i) { print(i); i = i + 1 }', 2"
    })
    public void testComplexPrintLoop(String program, double leakage){
        String runProgram = "bit_width 2; h input int h = 0buu; " + program;
        parse(runProgram).leaks(leakage).run();
    }

    @ParameterizedTest
    @CsvSource({
            "'int func(int a, int b) {print(a + b)} func(h, 0)', 'basic', '0bss', 2",
            "'int func(int a, int b) {print(a + b)} func(h, 0)', 'handler=inlining;maxrec=1;bot=basic', '0buu', 2",
            "'int func(int a){ if (a > 0){print(0); func(a - 1)}} func(h)', basic, '0bss', 2"
    })
    public void testPrintInFunction(String program, String handler, String expectedPrintValue, double leakage){
        String runProgram = "bit_width 2; h input int h = 0buu; " + program;
        System.out.println(toSSA(runProgram, false).toPrettyString());
        parse(runProgram, handler).leaks(leakage).val(Parser.ProgramNode.printName(Lattices.BasicSecLattice.LOW), expectedPrintValue).run();
    }

    @ParameterizedTest
    @CsvSource({
            "'int func() {print(1)} func()', summary, '0b01', 0",
            "'int func(int a) {print(a)} func(h)', summary, '0buu', 2",
            "'int func(int a, int b) {print(a + b)} func(h, 0)', summary, '0buu', 2",
            "'int func(int a){ if (a > 0){print(0); func(a - 1)}} func(h)', summary, '', 2",
            "'int func(int a){ print(0); func(a - 1) } func(h)', summary, '', 0"
    })
    public void testPrintInFunctionWithSummaryHandler(String program, String handler, String expectedPrintValue, double leakage){
        String runProgram = "bit_width 2; h input int h = 0buu; " + program;
        System.out.println(toSSA(runProgram, false).toPrettyString());
        ContextMatcher matcher = parse(runProgram, handler).leaks(leakage);
        if (expectedPrintValue.isEmpty()){
            matcher.val(Parser.ProgramNode.printName(Lattices.BasicSecLattice.LOW), val -> val.lastBit(Lattices.B.S));
        } else {
            matcher.val(Parser.ProgramNode.printName(Lattices.BasicSecLattice.LOW), expectedPrintValue);
        }
        matcher.run();
    }

    @ParameterizedTest
    @CsvSource({
            "'bit_width 2; int a; a = input(); print(a)', 2"
    })
    public void basicInputTest(String program, double leakage){
        parse(program).leaks(leakage).run();
    }

    @Test
    public void testBasicInputIf(){
        String program = "bit_width 3; l input int l = 0buuu; int a = 0; if (l == 3){ a = input(); a = a | 0b001 } print(a)";
        System.out.println(toSSA(program, false));
        parse(program).leaks(2).run();
    }

    @ParameterizedTest
    @CsvSource({
            "'', 0"
    })
    public void testBasicInputIfAndInputPrints(String insertAfterBitWidth, double leakage){
        String program = String.format("bit_width 3; %s h input int h = 0buuu; int a = 0; " +
                "if (h == 3){ a = input(); } ", insertAfterBitWidth);
        System.out.println(toSSA(program, false));
        parse(program).leaks(leakage).run();
    }

    @ParameterizedTest
    @CsvSource({
            "'int a; a = input(); print(a); int i = 0; while (a != i) { print(i); i = i + 1 }', 3"
    })
    public void testMoreComplexInputs(String program, double leakage){
        String runProgram = "bit_width 3; l input int l = 0buuu; " + program;
        parse(runProgram).leaks(leakage).run();
    }

    @Test
    public void testTwoInputAndPrintsInARow() {
        String program = "bit_width 2; print(input()); print(input());";
        System.out.println(toSSA(program));
        parse(program).leaks(4).run();
    }

    @Disabled
    @ParameterizedTest
    @CsvSource({
            "'int a = 0; while (l){ a = input(); }', 0",
            "'int a = 0; while (l){ a = input(); } print(a)', 3"
    })
    @Timeout(1)
    public void testLoopWithInputs(String program, String leakage){
        String runProgram = "bit_width 3; l input int l = 0buuu; " + program;
        parse(runProgram).leaks(leakage).run();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "while (1){ print(input()) }",
            "while (1){ int a; a = input(); print(a) }"
    })
    @Timeout(3)
    public void testInfiniteLeakage(String program){
        String runProgram = "bit_width 3; " + program;
        System.out.println(toSSA(runProgram, true));
        parse(runProgram).leaks("inf").run();
    }

    @ParameterizedTest
    @CsvSource({
            "'int func() {return input();} int a = func()', 1",
            "'int func() {print(input());} int a = func()', 1"
    })
    public void testGetTmpInputVariableDeclarationsFromAll(String program, int count){
        Parser.ProgramNode parsedProgram = Parser.process(program);
        assertEquals(count, parsedProgram.globalBlock.getTmpInputVariableDeclarationsFromAll().size());
    }

    @ParameterizedTest
    @CsvSource({
            "'int a = input()', 0buu",
            "'int func() {return input();} int a = func()', 0buu"
    })
    public void testBasicHandlerWithInputs(String program, String valueOfA){
        String runProgram = "bit_width 2; " + program;
        System.out.println(toSSA(runProgram, false).toPrettyString());
        parse(runProgram, "basic").val("a", valueOfA).run();
    }

    @ParameterizedTest
    @CsvSource({
            "'int func() {int a; a = input(); print(a); func()} func()', basic, inf",
            "'int func(int h) {int a; a = input(); print(a); func(0)} func(h)', basic, inf",
            "'int func() {print(input());} func()', inlining, 2",
            "'int func() {return input();} print(func())', basic, inf",
            "'int func() {print(input());} func()', basic, inf",
            "'int func() {return input();} print(func())', basic, inf",
            "'int func() {int a; a = input(); return a;} l output int o = func()', 'handler=summary', 2",
            "'int func() {int a; a = input(); print(a);} func()', 'handler=summary', 2",
    })
    public void testPrintInFunctionWithSummaryHandler(String program, String handler, String leakage){
        String runProgram = "bit_width 2; h input int h = 0buu; " + program;
        System.out.println(toSSA(runProgram, false).toPrettyString());
        parse(runProgram, handler).leaks(leakage).run();
    }

    @ParameterizedTest
    @CsvSource({
            "'int func() {int a; a = input(); print(a); func()} func()', basic, inf",
            "'int func() {int a; a = input(); print(a); func()} func()', inlining, inf",
            "'int func(int h) {int a; a = input(); print(a); func(0)} func(h)', inlining, inf",
            "'int func() {int a; a = input(); print(a); func()} func()', 'handler=summary', inf",
            "'int func(int h) {int a; a = input(); print(a); func(0)} func(h)', summary, inf",
            "'int func() {int a; a = input(); print(a); func()} func()', 'handler=summary;reduction=mincut', inf",
            "'int func(int h) {int a; a = input(); print(a); func(0)} func(h)', 'handler=summary;reduction=mincut', inf",
    })
    @Timeout(1)
    public void testPrintInFunctionWithSummaryHandlerRecursive(String program, String handler, String leakage){
        String runProgram = "bit_width 2; h input int h = 0buu; " + program;
        System.out.println(toSSA(runProgram, false).toPrettyString());
        parse(runProgram, handler).leaks(leakage).run();
    }

    @Test
    public void testInfiniteLeakage() {
        String runProgram = "bit_width 2; int func() { input(); func()} func()";
        System.out.println(toSSA(runProgram, false).toPrettyString());
        parse(runProgram, "summary").val("input", v -> {
            v.numberOfDependenciesOfLastBit(4);
        }).leaks("inf").run();
    }

    @Test
    public void testInfiniteLeakage2() {
        String runProgram = "bit_width 2; h append_only int input; int func() { h tmp_input int tmp = 0buu; input = input @ tmp; func()} func()";
        System.out.println(toSSA(runProgram, false).toPrettyString());
        parse(runProgram, "summary").val("input", v -> {
            v.numberOfDependenciesOfLastBit(2);
            v.endsWithStar();
        }).run();
    }

    @Test
    public void testInfiniteLeakage3() {
        String runProgram = "bit_width 2; int func() { print(input()); func()} func()";
        System.out.println(toSSA(runProgram, false).toPrettyString());
        parse(runProgram, "summary").val("input", v -> {
            System.out.println("ping");
        }).leaks("inf").run();
    }

    @Test
    public void testCallGraphContainsInputFuncProperly(){
        Parser.ProgramNode program = Parser.process("int func() {print(input()); func2()} int func2() {func()} func()");
        CallGraph graph = new CallGraph(program);
        assertThat(graph.mainNode.calledCallNodesAndSelfInPostOrder()).contains(graph.methodToNode.get(program.getMethod("input")));
    }

    @ParameterizedTest
    @CsvSource({
            "'int func() {print(0); func2()} int func2() {func()} func()', print",
            "'int func() {input(); func2()} int func2() {func()} func()', input",
            "'int func() {print(0); input(); func2()} int func2() {func()} func()', print",
            "'int bla(){ h tmp_input int a = 0buu; return a;} int func() {print(bla()); func()} int func2() {func()} func()', print",
            "'int bla(){ h tmp_input int a = 0buu; return a;} int func() {print(bla()); func2()} int func2() {func()} func()', print",
            "'int func() {int a = input(); print(a); func2()} int func2() {func()} func()', print",
            "'int func() {print(input()); func2()} int func2() {func()} func()', print",
            "'int func() {print(input()); func2()} int func2() {func()} func()', input"
    })
    //@Timeout(5)
    public void testPrintTwoLevelRecursion(String program, String variable){
        String runProgram = "bit_width 2; h input int h = 0buu; " + program;
        System.out.println(toSSA(runProgram, false).toPrettyString());
        parse(runProgram, "summary").val(variable, ContextMatcher.ValueMatcher::endsWithStar).run();
    }

    @ParameterizedTest
    @CsvSource({
            "'int func() {print(input()); func2()}" +
                    " int func2() {func()} func()', basic, inf",
            "'int func() {print(input()); func2()}" +
                    " int func2() {func()} func()', inlining, inf",
            "'int func() {print(input()); func()} func()', summary, inf",
            "'int func(int h) {int a; a = input(); print(a); func2(0)}" +
                    " int func2(int h) {int a; a = input(); print(a); func(0)} func(h)', summary, inf"
    })
    //@Timeout(5)
    public void testPrintInFunctionWithSummaryHandlerTwoLevelRecursion(String program, String handler, String leakage){
        String runProgram = "bit_width 2; h input int h = 0buu; " + program;
        System.out.println(toSSA(runProgram, false).toPrettyString());
        parse(runProgram, handler).leaks(leakage).run();
    }

    @ParameterizedTest
    @CsvSource({
            "'int a = 0; if (input()) { a = l_input();} print(a)', 1"
    })
    public void testLowInputs(String program, String leakage){
        String runProgram = "bit_width 2; h input int h = 0buu; " + program;
        System.out.println(toSSA(runProgram, false).toPrettyString());
        parse(runProgram).leaks(leakage).run();
    }

    @Disabled
    @ParameterizedTest
    @CsvSource({
            "'int a = 0; while (l_input()){ a = input(); }', 0",
            "'int a = 0; while (l_input()){ a = input(); } print(a)', 32",
            "'int a = 0; while (l_input()){ print(input()); }', inf"
    })
    public void testWithLargeInputs(String program, String leakage){
        String runProgram = "bit_width 32; " + program;
        parse(runProgram).leaks(leakage).run();
    }
}
