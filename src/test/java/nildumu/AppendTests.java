package nildumu;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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
}
