package nildumu;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static nildumu.FunctionTests.parse;
import static nildumu.SSA2Tests.toSSA;

public class AppendTests {

    @ParameterizedTest
    @ValueSource(strings = {
            "l append int abc",
            "bit_width 3; l append int abc; abc = abc @ 3"
    })
    public void testParseBasic(String str) {
        parse(str);
    }

    @Test
    public void testBasicAppend(){
        String program = "bit_width 3; l append int abc; abc = abc @ 3";
        parse(program).val("abc", "0b011").run();
    }

    @Test
    public void testBasicIf(){
        String program = "bit_width 3; l append int abc; l input int l = 0buuu; if (l == 3){abc = abc @ 3} abc = abc @ 3";
        System.out.println(toSSA(program, false));
        parse(program).val("abc", "0b011nnn").print().run();
    }

    @Test
    public void testSimpleMethod(){
        parse("bit_width 3; l append int abc; int print(){ abc = abc @ 3 } print()").val("abc", "0b011").run();
    }
}
