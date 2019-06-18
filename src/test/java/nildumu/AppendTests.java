package nildumu;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

import static java.time.Duration.ofSeconds;
import static nildumu.FunctionTests.parse;
import static nildumu.SSA2Tests.toSSA;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

public class AppendTests {

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
        parse("bit_width 2; h input int h = 0buu; if (h){print()}").leaks(1).run();
    }

    @ParameterizedTest
    @CsvSource({
            "'while (h == 0) { print() }', 3",
            "'while (h == 0) { print(h) }', 3"
    })
    public void testBasicPrintLoop(String program, double leakage){
//        assertTimeoutPreemptively(ofSeconds(1), () -> {
        String runProgram = "bit_width 3; h input int h = 0buuu; " + program;
        System.out.println(toSSA(runProgram, false).toPrettyString());
            parse(runProgram).leaks(leakage).run();
 //       });
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
        assertTimeoutPreemptively(ofSeconds(1), () -> {
            String runProgram = "bit_width 2; h input int h = 0buu; " + program;
            parse(runProgram).leaks(leakage).run();
        });
    }

    @ParameterizedTest
    @CsvSource({
            "'int func(int a, int b) {print(a + b)} func(h, 0)', 'basic', '0bss', 2",
            "'int func(int a, int b) {print(a + b)} func(h, 0)', 'handler=inlining;maxrec=1;bot=basic', '0buu', 2",
            "'int func(int a){ if (a > 0){print(); func(a - 1)}} func(h)', basic, '0bss', 2"
    })
    public void testPrintInFunction(String program, String handler, String expectedPrintValue, double leakage){
        String runProgram = "bit_width 2; h input int h = 0buu; " + program;
        System.out.println(toSSA(runProgram, false).toPrettyString());
        parse(runProgram, handler).leaks(leakage).val(Parser.L_PRINT_VAR, expectedPrintValue).run();
    }

    @ParameterizedTest
    @CsvSource({
            "'int func() {print(1)} func()', summary, '0b0" +
                    "1', 0",
            "'int func(int a) {print(a)} func(h)', summary, '0buu', 2",
            "'int func(int a, int b) {print(a + b)} func(h, 0)', summary, '0buu', 2",
            "'int func(int a){ if (a > 0){print(); func(a - 1)}} func(h)', summary, '', 2",
            "'int func(int a){ print(); func(a - 1) } func(h)', summary, '', 0"
    })
    public void testPrintInFunctionWithSummaryHandler(String program, String handler, String expectedPrintValue, double leakage){
        String runProgram = "bit_width 2; h input int h = 0buu; " + program;
        System.out.println(toSSA(runProgram, false).toPrettyString());
        ContextMatcher matcher = parse(runProgram, handler).leaks(leakage);
        if (expectedPrintValue.isEmpty()){
            matcher.val(Parser.L_PRINT_VAR, val -> val.lastBit(Lattices.B.S));
        } else {
            matcher.val(Parser.L_PRINT_VAR, expectedPrintValue);
        }
        matcher.run();
    }

    @ParameterizedTest
    @CsvSource({
            "'bit_width 2; int a; a = input(); print(a)', 2",
            "'bit_width 2; input_prints; int a; a = input(); print(a)', 2"
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
            "'', 0",
            "'input_prints;', 1"
    })
    public void testBasicInputIfAndInputPrints(String insertAfterBitWidth, double leakage){
        String program = String.format("bit_width 3; %s h input int h = 0buuu; int a = 0; " +
                "if (h == 3){ a = input(); } ", insertAfterBitWidth);
        System.out.println(toSSA(program, false));
        parse(program).leaks(leakage).run();
    }

    @ParameterizedTest
    @CsvSource({
            "'int a; a = input(); print(a); a = input(); print(a)', 6",
            "'int a; a = input(); print(a); int i = 0; while (a != i) { print(i); i = i + 1 }', 3"
    })
    public void testMoreComplexInputs(String program, double leakage){
        String runProgram = "bit_width 3; l input int l = 0buuu; " + program;
        parse(runProgram).leaks(leakage).run();
    }

    @ParameterizedTest
    @CsvSource({
            "'int a = 0; while (l){ a = input(); }', 0",
            "'int a = 0; while (l){ a = input(); } print(a)', 3"
    })
    public void testLoopWithInputs(String program, String leakage){
        String runProgram = "bit_width 3; l input int l = 0buuu; " + program;
        parse(runProgram).leaks(leakage).run();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "while (l){ int a; a = input(); print(a) }"
    })
    public void testInfiniteLeakage(String program){
        String runProgram = "bit_width 3; l input int l = 0buuu; " + program;
        System.out.println(toSSA(runProgram, true));
        parse(runProgram).leaks("inf").run();
    }
}
