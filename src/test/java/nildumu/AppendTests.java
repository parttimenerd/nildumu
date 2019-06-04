package nildumu;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static nildumu.FunctionTests.parse;
import static nildumu.SSA2Tests.toSSA;

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
        parse("bit_width 3; h input int h = 0buuu; " + program).leaks(leakage).run();
    }

    @ParameterizedTest
    @CsvSource({
            "'int i = 0; while (h != i) { print(i); i = h }', 2",
            "'int i = 0; while (h != i) { print(i); i = i + 1 }', 2"
    })
    public void testComplexPrintLoop(String program, double leakage){
        parse("bit_width 2; h input int h = 0bu; " + program).leaks(leakage).run();
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
}
