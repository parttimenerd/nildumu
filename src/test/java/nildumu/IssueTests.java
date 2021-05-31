package nildumu;

import org.junit.jupiter.api.Test;

import static nildumu.FunctionTests.parse;

/**
 * Tests related to problems found by other (and submitted via e.g. GitHub issues)
 */
public class IssueTests {

    @Test
    public void testIssue3() {
        parse("use_sec basic;\n" +
                "bit_width 3;\n" +
                "\n" +
                "(int[3]) loop_method7_0(int h) {\n" +
                "  int[3] a = {h, h, h};\n" +
                "  return (a,);\n" +
                "}\n" +
                "\n" +
                "h input int h = 0buuu;\n" +
                "int[3] b;\n" +
                "\n" +
                "b= *loop_method7_0(h);\n" +
                "int l; /* declared then assigned */\n" +
                "l = (b[0]);\n" +
                "\n" +
                "l output int o = l;").leaks(3).run();
    }

    @Test
    public void testIssue3Min() {
        parse("bit_width 2;\n" +
                "\n" +
                "h input int h = 0buu;\n" +
                "int[1] b;\n" +
                "\n" +
                "b= *({h},);\n" +
                "int l; /* declared then assigned */\n" +
                "l = b[0];\n" +
                "\n" +
                "int k = b[0]; /* declared and directly assigned */\n" +
                "\n" +
                "l output int o = l | 0b01;\n" +
                "l output int p = k | 0b10;").leaks(2).run();
    }

    @Test
    public void testIssue3Min2() {
        parse("bit_width 2;\n" +
                "\n" +
                "h input int h = 0buu;\n" +
                "int[1] b;\n" +
                "\n" +
                "b= *({h},);\n" +
                "int l; /* declared then assigned */\n" +
                "l = b[0];\n" +
                "\n" +
                "l output int o = l;").leaks(2).run();
    }
}
