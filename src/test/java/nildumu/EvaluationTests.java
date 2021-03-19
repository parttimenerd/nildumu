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
    public void testElectronicPurse2(){ // modified
        check("h input int H = 0bu{32};\n" +
                "int O = 0;\n" +
                "while (H >= 5 && H < 20){\n" +
                "    H = H - 1;\n" +
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
                "while (i < 16){\n" +
                "    int m = 1<<(31-i);\n" +
                "    if (z + m <= I) {\n" +
                "        z = z + m;\n" +
                "    }\n" +
                "    i = i + 1;\n" +
                "}\n" +
                "l output int o = z;", 16, 32);
    }

    @Test
    public void testIllustrativeExample() {
        check("/* Z. Meng and G. Smith, Calculating Bounds on Information Leakage Using Two-bit Patterns, in Proceedings of the ACM SIGPLAN 6th Workshop on Programming Languages and Analysis for Security, 2011, p. 1:1--1:12. */\n" +
                "/* Should leak log 17 = 4.087 bits */\n" +
                "int O;\n" +
                "h input int h = 0bu{32};\n" +
                "int z = h & 0b01110111011101110111011101110111; /* 0x77777777 */\n" +
                "if (z <= 64) {\n" +
                "    O = h;\n" +
                "} else {\n" +
                "    O = 0;\n" +
                "}\n" +
                "if (O % 2 == 0) {\n" +
                "    O = O + 1;\n" +
                "}\n" +
                "l output int o = O;", 14, 14);
    }

    @Test
    public void testImplicitFlow() {
        check("/* Toy program from paper of Meng et. al: \"Calculating bounds on information leakage using two-bit patterns\" */\n" +
                "/* https://github.com/qif/jpf-qif/blob/master/src/examples/plas/ImplicitFlow.java */\n" +
                "/* Should leak log 7 = 2.8074 */\n" +
                "\n" +
                "h input int S = 0buuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuu;\n" +
                "int O;\n" +
                "if (S == 0) {\n" +
                "    O = 0;\n" +
                "} else {\n" +
                "    if (S == 1) {\n" +
                "       O = 1;\n" +
                "    } else {\n" +
                "        if (S == 2) {\n" +
                "            O = 2;\n" +
                "        } else {\n" +
                "            if (S == 3) {\n" +
                "                O = 3;\n" +
                "            } else {\n" +
                "                if (S == 4) {\n" +
                "                    O = 4;\n" +
                "                } else {\n" +
                "                    if (S == 5) {\n" +
                "                        O = 5;\n" +
                "                    } else {\n" +
                "                        if (S == 6) {\n" +
                "                            O = 6;\n" +
                "                        } else {\n" +
                "                            O = 0;\n" +
                "                        }\n" +
                "                    }\n" +
                "                }\n" +
                "            }\n" +
                "        }\n" +
                "    }\n" +
                "}\n" +
                "l output int o = O;", 3, 3);
    }

    @Test
    public void testMaskedCopy() {
        check("/* Z. Meng and G. Smith, Calculating Bounds on Information Leakage Using Two-bit Patterns, in Proceedings of the ACM SIGPLAN 6th Workshop on Programming Languages and Analysis for Security, 2011, p. 1:1--1:12. */\n" +
                "/* Should leak 16 bits */\n" +
                "h input int S = 0buuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuu;\n" +
                "l output int o = S & 0b11111111111111110000000000000000; /*0xffff0000*/", 16, 16);
    }

    @Test
    public void testMixDup() {
        check("/*[1] J. Newsome, S. McCamant, and D. Song, Measuring Channel Capacity to Distinguish Undue Influence, in Proceedings of the ACM SIGPLAN Fourth Workshop on Programming Languages and Analysis for Security, 2009, pp. 73-85.*/\n" +
                "/* Should leak 16 bits */\n" +
                "h input int x = 0buuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuu;\n" +
                "int y = ( ( x >> 16 ) ^ x ) & 0b00000000000000001111111111111111;\n" +
                "int O = y | ( y << 16 );\n" +
                "l output int o = O;", 16, 16);
    }

    @Test
    public void testPopulationCount() {
        check("/* J. Newsome, S. McCamant, and D. Song, Measuring Channel Capacity to Distinguish Undue Influence, in Proceedings of the ACM SIGPLAN Fourth Workshop on Programming Languages and Analysis for Security, 2009, pp. 73-85.*/\n" +
                "/* Should leak log(33) = 5.0444 bits */\n" +
                "h input int h = 0b0uuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuu;\n" +
                "int i = (h & 0b001010101010101010101010101010101) + ((h >> 1) & 0b001010101010101010101010101010101);\n" +
                "i = (i & 0b000110011001100110011001100110011) + ((i >> 2) & 0b000110011001100110011001100110011);\n" +
                "i = (i & 0b000001111000011110000111100001111) + ((i >> 4) & 0b00001111000011110000111100001111);\n" +
                "i = (i & 0b000000000111111110000000011111111) + ((i >> 8) & 0b000000000111111110000000011111111);\n" +
                "l output int o = (i + (i >> 16)) & 0b1111111111111111;\n", 5, 5);
    }

    @Test
    public void testPasswordChecker() {
        check("/* Typical password checker adaption, that compares with \"1\", as not all tools support */\n" +
                "/* low inputs */\n" +
                "h input int h = 0bu{32};\n" +
                "l input int l = 0;\n" +
                "if (h == l){\n" +
                "    l = 1;\n" +
                "} else {\n" +
                "    l = 0;\n" +
                "}\n" +
                "l output int o = l;", 1, 1);
    }

    @Test
    public void testSanityCheck() {
        check("use_sec basic;\n" +
                "bit_width 32;\n" +
                "h input int S = 0bu{32};\n" +
                "int O = 0;\n" +
                "int base = 0b01111{24}1010; /* 0x7ffffffa; */\n" +
                "if ((S < 16))\n" +
                "  {\n" +
                "    O = (base + S);\n" +
                "  } \n" +
                "else\n" +
                "  {\n" +
                "    O = base;\n" +
                "  }\n" +
                "l output int o = O;\n", 32, 32);
    }

    @Test
    public void testSanityCheck2() {
        check("use_sec basic;\n" +
                "bit_width 32;\n" +
                "h input int S = 0bu{32};\n" +
                "int O = 0;\n" +
                "int base = 0b0{16}00010{12};\n" +
                "if ((S < 16))\n" +
                "  {\n" +
                "    O = (base + S);\n" +
                "  } \n" +
                "else\n" +
                "  {\n" +
                "    O = base;\n" +
                "  }\n" +
                "l output int o = O;\n", 32, 32);
    }

    @Test
    public void testSum() {
        check("/* Toy program from paper of Backes et. al: \"Automatic */\n" +
                "/* discovery and quantification of information leaks\" */\n" +
                "/* Should leak n */\n" +
                "h input int x = 0buuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuu;\n" +
                "h input int y = 0buuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuu;\n" +
                "h input int z = 0buuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuu;\n" +
                "l output int o = x + y + z;", 32, 32);
    }

    @Test
    public void testLaunderingAttack() {
        check("h input int h = 0bu{32}; int O = 0;\n" +
                "while (O != (h | 0b01)) {\n" +
                "O = O + 1;\n" +
                "} l output int o = O;", 31, 31);
    }

    @Test
    public void testFibonnacci() {
        check("/* Should leak five bit */\n" +
                "int fib(int num){\n" +
                "    int r = 1;\n" +
                "    if (num > 2){\n" +
                "        r = fib(num - 1) + fib(num - 2);\n" +
                "    }\n" +
                "    return r;\n" +
                "}\n" +
                "\n" +
                "h input int h = 0bu{32};\n" +
                "int z = fib(h);\n" +
                "l output int o = z;", -1, 32);
    }

    public void check(String program, int shouldLeakAt32, int shouldLeakAt5) {
        if (shouldLeakAt32 != -1) {
            parse(program, 32).useSingleMCAlgo().leaks(shouldLeakAt32).run();
        }
        parse(program, 5).useSingleMCAlgo().leaks(shouldLeakAt5).run();
    }


    /*public static void main(String[] args) {
        new EvaluationTests().testElectronicPurse();
    }*/
}
