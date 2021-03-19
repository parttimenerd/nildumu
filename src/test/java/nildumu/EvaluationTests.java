package nildumu;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static nildumu.LoopTests.parse;

/** these tests are exactly the tests used during evaluation (but with less inlinings) */
public class EvaluationTests {

    @BeforeAll
    public static void load() { parse("int i = 0"); }

    @Test
    public void testElectronicPurse(){
        check("h input int H = 0bu{32};\n" +
                "int O = 0;\n" +
                "while (H >= 5 && H < 20){\n" +
                "    H = H - 5;\n" +
                "    O = O + 1;\n" +
                "}\n" +
                "l output int o = O;", 5, 5);
    }

    @Test
    public void testBinarySearch() {
        check("h input int I = 0b0u{32};\n" +
                "\n" +
                "int BITS = 16;\n" +
                "\n" +
                "int z = 0;\n" +
                "\n" +
                "int i = 0;\n" +
                "\n" +
                "while (i < BITS){\n" +
                "    int m = 1<<(30-i);\n" +
                "    if (z + m <= I) {\n" +
                "        z = z + m;\n" +
                "    }\n" +
                "    i = i + 1;\n" +
                "}\n" +
                "l output int o = z;", 16, 32);
    }

    public void check(String program, int shouldLeakAt32, int shouldLeakAt5) {
        parse(program, 32).useSingleMCAlgo().leaks(shouldLeakAt32).run();
        parse(program, 5).useSingleMCAlgo().leaks(shouldLeakAt5).run();
    }


    /*public static void main(String[] args) {
        new EvaluationTests().testElectronicPurse();
    }*/
}
