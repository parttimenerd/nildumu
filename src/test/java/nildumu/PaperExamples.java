package nildumu;

import org.junit.jupiter.api.Test;

import static nildumu.FunctionTests.parse;

/**
 * Examples used in the paper
 */
public class PaperExamples {

    @Test
    public void testSimpleIntroExample() {
        parse("input int h; output int o := h = 1").leaks(1).run();
    }

    @Test
    public void testLaunderingAttack() {
        parse("input int h; int z := 0; while (z != h) { z := z + 1; } output int o := z").leaks(32).run();
    }

    @Test
    public void testTransformedLoop() {
        parse("input int h; int z = 1; int f(int z, int h){ if (z != h) { z := z + 1; z := f(z, h)} return z } z = f(z, h); output int o = z").leaks(32).run();
    }

    @Test
    public void testPropagatedPredicatesExample() {
        parse("input int x;\n" +
                "int z = 1;\n" +
                "int y = 1;\n" +
                "if (x = y) {\n" +
                "    z := x;\n" +
                "}\n" +
                "output int o = z;").leaks(0).run();
    }

    @Test
    public void testSummaryExample() {
        parse("bit_width 3;\n" +
                "\n" +
                "int f(int x){\n" +
                "    int r := 0;\n" +
                "    if(x * 3 = 1){\n" +
                "        r := 0b111\n" +
                "    }\n" +
                "    return r\n" +
                "}\n" +
                "input int h;\n" +
                "output int o = f(h);").leaks(1).run();
    }
}
