package nildumu;

import org.junit.jupiter.api.Test;

import static nildumu.LoopTests.parse;

/** these tests are exactly the tests used during evaluation (but with less inlinings) */
public class EvaluationTests {

    @Test
    public void testElectronicPurse(){
        parse("h input int H = 0bu{32};\n" +
                "int O = 0;\n" +
                "while (H >= 5 && H < 20){\n" +
                "    H = H - 5;\n" +
                "    O = O + 1;\n" +
                "}\n" +
                "l output int o = O;", 32).useSingleMCAlgo().leaks(32).run();
    }

    @Test
    public void testElectronicPurseBinary(){
        parse("h input int H = 0bu{32};\n" +
                "int O = 0;\n" +
                "while (H >= 4 && H < 16){\n" +
                "    H = H - 4;\n" +
                "    O = O + 1;\n" +
                "}\n" +
                "l output int o = O;", 32).useSingleMCAlgo().leaks(32).run();
    }

    public static void main(String[] args) {
        new EvaluationTests().testElectronicPurseBinary();
    }
}
