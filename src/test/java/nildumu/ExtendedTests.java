package nildumu;

import nildumu.mih.MethodInvocationHandler;
import org.junit.jupiter.api.Test;

import static nildumu.LoopTests.parse;
import static nildumu.Processor.RECORD_ALTERNATIVES;
import static nildumu.Processor.process;

/**
 * SSA bug:
 *
 h input int h = 0b0u;
 int x = 1;
 if (h){
 int x = h;

 =>
 h input int h = 0b0u;
 int x = 1;
 if (h) {
 int x = h;
 } else {

 }
 }


 */

public class ExtendedTests {

    /**
     h input int h = 0b0u;
     int x = 0b01;
     if (h){
        x = h;
     }
     */
    @Test
    public void testBasicIfWithMods() {
        parse("h input int h = 0b0u;\n" +
                "int x = 1;\n" +
                "if (h){\n" +
                "    x = h;\n" +
                "}").val("x2", "0b01").run();
    }

    @Test
    public void testImplicitFlowWithVariableEquals() {
        parse("h input int h = 0bu{5};\n" +
                "int x = h * 2;\n" +
                "if (h == x){\n" +
                "    x = h;\n" +
                "} else {x = 0; } l output int o = x").leaks(5).run();
    }

    /**
     bit_width 32;
     int O4; int O3; int O2; int O1;
     h input int S = 0buu;
     int O = 0;
     if ((S == 0)) {
     O1 = 0;
     } else {
     O3 = 1;
     }
     O4 = phi(O1, O3);
     l output int o = O4;
     */
    @Test
    public void testImplicitFlowCondensed(){
        parse("bit_width 32;\n" +
                "int O4; int O3; int O2; int O1;\n" +
                "h input int S = 0buu;\n" +
                "int O = 0;\n" +
                "if ((S == 0)) {\n" +
                "\tO1 = 0;\n" +
                "} else {\n" +
                "\tO3 = 1;\n" +
                "}\n" +
                "O4 = phi(O1, O3);\n" +
                "l output int o = O4;").leaks(1).run();
    }


    /**
     use_sec basic;
     bit_width 2;
     int O4; int O3; int O2; int O1;
     h input int S = 0buu;
     int O = 0;
     if ((S == 0)) {
         O1 = 0;
     } else {
         if ((S == 1)) {
            O2 = 1;
         } else {

         }
         O3 = phi(O2, O);
     }
     O4 = phi(O1, O3);
     l output int o = O4;
     */
    @Test
    public void testImplicitFlowCondensed2(){
        parse("h input int S = 0buu;\n" +
                "int O = 0;\n" +
                "if (S == 0) {\n" +
                "    O = 0;\n" +
                "} else {\n" +
                "    if (S == 1) {\n" +
                "       O = 1;\n" +
                "    }\n" +
                "}\n" +
                "l output int o = O;").leaks(1).run();
    }

    @Test
    public void testImplicitFlowCondensed4(){
        parse("h input int S = 0buu;\n" +
                "int O = 0;\n" +
                "if (S == 0) {\n" +
                "    O = 0;\n" +
                "} else {\n" +
                "    O = 1" +
                "}\n" +
                "l output int o = O;").leaks(1).run();
    }

    @Test
    public void testImplicitFlow(){
        parse("h input int S = 0buuuuuu;\n" +
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
                "l output int o = O;").leaks(3).run();
    }

    @Test
    public void testImplicitFlow3(){
        parse("h input int S = 0bu;\n" +
                "l output int o = 0;").leaks(0).run();
    }

    @Test
    public void testLowerPowerOfTwo() {
        parse("h input int h = 0buuuuu; int x = 0; if (2 < h) { x = h; } int y = x").val("y", "0b0uuuu").run();
    }

    @Test
    public void testLowerOnes() {
        parse("h input int h = 0buuuuu; int x = 0; if (3 < h) { x = h; } int y = x").val("y", "0b0uuuu").run();
    }

    @Test
    public void testLower() {
        parse("h input int h = 0buuuuu; int x = 0; if (h > 0) { x = h; } int y = x").val("y", "0b0uuuu").run();
    }

    @Test
    public void testLowerCombination() {
        parse("h input int h = 0buuuuuuu; int x = 0; if ((((4 < h) || (4 == h)) && (h < 16))) { x = h; } int y = x").val("y", "0b000uuuu").run();
    }

    @Test
    public void testLowerCombination2() {
        parse("h input int h = 0buuuuuuu; int x = 0; if (h < 16) { x = h; } int y = x").val("y", "0buuuuuuu").run();
    }

    @Test
    public void testLowerCombination3() {
        parse("h input int h = 0buuuuuuu; int x = 0; if ((4 < h) || (4 == h)) { x = h; } int y = x").val("y", "0b0uuuuuu").run();
    }

    @Test
    public void testLowerCombination4() {
        parse("h input int h = 0buuuu; int x = 0; if ((0 < h) && (h < 4)) { x = h; } int y = x").val("y", "0b00uu").run();
    }

    @Test
    public void testLowerCombination5() {
        parse("h input int h = 0buuuu; int x = 0; if ((0 < h || h == 0) && (h < 4)) { x = h; } int y = x").val("y", "0b00uu").run();
    }

    @Test
    public void testLowerCombination6() {
        parse("h input int h = 0buuuuuuu; int x = 0; if ((2 < h) || (2 == h)) { x = h; } int y = x").val("y", "0b0uuuuuu").run();
    }

    @Test
    public void testLowerCombination7() {
        parse("h input int h = 0bu{8}; int x = 0; if (4 < h && h < 16) { x = h; } int y = x").val("y", "0b0000uuuu").run();
    }

    @Test
    public void testLowerCombination8() {
        parse("h input int h = 0bu{6}; int x = 0; if (0 < h && h < 2) { x = h; } int y = x").val("y", "0b00000u").run();
    }

    @Test
    public void testShortCircuitPropagation() {
        parse("h input int x = 0buu; h input int y = 0buu; int z = 0; if (x == 0 && y == x) { z = y; } int a = z").val("a", "0b00").run();
    }

    @Test
    public void testShortCircuitPropagation2() {
        parse("h input int x = 0buu; h input int y = 0buu; int z = 0; if ((x != 0) && (x != 0 || y == x)) { z = y; } int a = z").val("a", "0buu").run();
    }

    @Test
    public void testShortCircuitPropagation3() {
        parse("h input int x = 0buu; h input int y = 0b0u; int z = 0; if ((x > 0) || (x == 0 || y == x)) { z = y; } int a = z").val("a", "0b0u").run();
    }

    @Test
    public void testElectronicPurseBinary(){
        parse("h input int H = 0bu{32};\n" +
                "int Z = 0;\n" +
                "if (4 < H && H < 16){\n" +
                "    Z = H; "+
                "}\n" +
                "int z = Z;").val("z", "0b0{28}u{4}").run();
    }

    @Test
    public void testIllustrativeExampleConsended() {
        parse("int O = 0;\n" +
                "h input int h = 0bu{8};\n" +
                "int z = h & 0b01111111; int x_ = 0; \n" +
                "if (z < 64) {\n" +
                "    O = h;\n" +
                "}" +
                "l output int o = O; int x = x_;").val("o", "0bu0uuuuuu").run();
    }

    @Test
    public void testIllustrativeExampleConsended2() {
        parse("int O = 0;\n" +
                "h input int h = 0bu{8};\n" +
                "int z = h & 0b01110111; int x_ = 0; \n" +
                "if (z < 64) {\n" +
                "    O = h;\n" +
                "x_ = z;" +
                "}" +
                "l output int o = O; int x = x_;").val("o", "0bu0uuuuuu").val("x", "0b00uu0uuu").run();
    }

    @Test
    public void testModulo() {
        parse("h input int O = 0b0u{2};\n int z = 3;" +
                "if (O % 2 == 0) {\n" +
                "    O = O + 1; z = O;\n" +
                "}\n" +
                "l output int o = O; int z_ = z;").val("z_", "0b0u1").val("o", "0b0u1").run();
    }

    public ContextMatcher parse(String program){
        return new ContextMatcher(process(program, Context.Mode.EXTENDED, MethodInvocationHandler.createDefault(), RECORD_ALTERNATIVES));
    }
}
