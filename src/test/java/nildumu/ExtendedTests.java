package nildumu;

import org.junit.jupiter.api.Test;

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

    public ContextMatcher parse(String program){
        return new ContextMatcher(process(program, Context.Mode.EXTENDED));
    }
}
