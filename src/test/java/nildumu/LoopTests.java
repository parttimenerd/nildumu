package nildumu;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.logging.Level;

import static java.time.Duration.ofMillis;
import static nildumu.Processor.process;
import static nildumu.util.Util.iter;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

public class LoopTests {

    /**
     h input int h = 0b0u;
     l output int o = h;
     */
    @Test
    public void testBasic(){
        parse("h input int h = 0b0u;\n" +
                "l output int o = h;").leakage(l -> l.leaks("l", 1)).run();
    }

    /**
     * <code>
       h input int h = 0b0u;
       int x = 0;
       while (h == 0){
            x = x + 1;
       }
       l output int o = x;
     * </code>
     */
    @Test
    public void testBasicLoop(){
        parse("h input int h = 0b0u;\n" +
                "int x = 0;\n" +
                "while (h == 0){\n" +
                "\tx = x + 1;\n" +
                "}\n" +
                "l output int o = x;")
                .leaks(1).run();
    }

    /**
     * <code>
     h input int h = 0b0u;
     int x = 0;
     while (h == 0){
        x = x | 1;
     }
     l output int o = x;
     * </code>
     */
    @Test
    public void testBasicLoop_condensed(){
        parse("h input int h = 0b0u;\n" +
                "int x = 0;\n" +
                "while (h == 0){\n" +
                "\tx = x | 0b11;\n" +
                "}\n" +
                "l output int o = x;")
                //.bit("x3[2]", "u")
                //.bit("x2[1]", "u")
                .bit("o[1]", "u")
                .leaks(1).run();
    }

    @Test
    public void testBasicLoop2(){
        parse("h input int h = 0b0u; int htmp = h;\n" +
                "int x = 0;\n" +
                "while (htmp){\n" +
                "\thtmp = htmp;\n" +
                "\tx = htmp;\n" +
                "}");
    }

    @Test
    public void testBasicLoop3(){
        parse("h input int h = 0bu;\n" +
                "while (h){\n" +
                "\th = h;\n" +
                "}\n" +
                "l output int o = h").leaks(1).val("o", "0bu").run();
    }

    @Test
    public void testBasicLoop3_condensed(){
        parse("h input int h = 0bu;\n" +
                "while (h){\n" +
                "\th = 1;\n" +
                "}\n" +
                "l output int o = h").leaks(1).run();
    }

    /**
     * This one didn't terminate
     * <code>
     *     h input int h = 0b0u;
     *     while (h == h){
     * 	        h + 1;
     *     }
     * </code>
     */
    @Test
    public void testBasicLoop4(){
        assertTimeoutPreemptively(ofMillis(1000), () -> {
            parse("h input int h = 0b0u;\n" +
                "while (h == h){\n" +
                "\th + 1;\n" +
                "}\n");
        });
    }

    /**
     * This one didn't terminate
     * <code>
     *     h input int h = 0b0u;
     *     while (h == h){
     * 	        h + 1;
     *     }
     * </code>
     */
    @Test
    public void testBasicLoop4_1(){
        assertTimeoutPreemptively(ofMillis(10000), () -> {
            parse("h input int h = 0b0u;\n" +
                    "while (h == h){\n" +
                    "\th = h + 1;\n" +
                    "}\n");
        });
    }

    /**
     * <code>
          bit_width 2;
          h input int h = 0b0u;
          l input int l = 0bu;
          while (l){
              h = [2](h[2] | h[1]);
          }
          l output int o = h;
     * </code>
     */
    @Test
    public void testBasicLoop4_condensed(){
        Lattices.Bit.toStringGivesBitNo = true;
        assertTimeoutPreemptively(ofMillis(1000000), () ->
                parse("bit_width 2;\n" +
                "h input int h = 0b0u;\n" +
                "l input int l = 0bu;\n" +
                "while (l){\n" +
                "  h = [2](h[2] | h[1]);\n" +
                "}\n" +
                "l output int o = h;").leaks(1).run());
    }

    /**
     <code>
     bit_width 2;
     h input int h = 0buu;
     l input int l = 0bu;
     while (l){
        h = [2](h[2] | h[1]);
     }
     l output int o = h;
     </code>
     */
    @ParameterizedTest
    @ValueSource(ints = {1, 2, 10})
    public void testBasicLoop4_condensed2(int secretSize){
        assertTimeoutPreemptively(ofMillis(1000000), () ->
                parse(String.format("bit_width %d;\n", secretSize) +
                        String.format("h input int h = 0b%s;\n", iter("u", secretSize)) +
                        "l input int l = 0bu;\n" +
                        "while (l){\n" +
                        "  h = [2](h[2] | h[1]);\n" +
                        "}\n" +
                        "l output int o = h;").leaks(secretSize).run());
    }

    /**
     * <code>
     bit_width 2;
     h input int h = 0b0u;
     l input int l = 0bu;
     while (l){
        while (l){
            h = [2](h[2] | h[1]);
        }
     }
     l output int o = h;
     * </code>
     */
    @Test
    public void testBasicLoopNested() {
        assertTimeoutPreemptively(ofMillis(1000000), () ->
                parse("     bit_width 2;\n" +
                        "     h input int h = 0b0u;\n" +
                        "     l input int l = 0bu;\n" +
                        "     while (l){\n" +
                        "        while (l){\n" +
                        "            h = [2](h[2] | h[1]);\n" +
                        "        }\n" +
                        "     }\n" +
                        "     l output int o = h;").leaks(1).run());
    }

    @Test
    public void testElectronicPurse(){
        parse("h input int h = 0buuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuu;\n" +
                "int z = 0;\n" +
                "while (h >= 1){\n" +
                "    h = h - 1;\n" +
                "    z = z + 1;\n" +
                "}\n" +
                "l output int o = z;").leaks(32).run();
    }

    @Test
    public void testElectronicPurseCondensed(){
        parse("h input int h = 0buu;\n" +
                "int z = 0;\n" +
                "while (h != 1){\n" +
                "    h = h + 1;\n" +
                "    z = z + 1;\n" +
                "}\n" +
                "l output int o = z;").leaks(2).run();
    }

    @Test
    public void testElectronicPurseCondensed2(){
        Context.LOG.setLevel(Level.FINE);
        parse("h input int h = 0buuu;\n" +
                "int z = 0;\n" +
                "while (h != 0){\n" +
                "    h = h - 1;\n" +
                "    z = h;\n" +
                "}\n" +
                "l output int o = z;").leaks(3).run();
        Context.LOG.setLevel(Level.INFO);
    }

    @Test
    public void testBinarySearch(){
        parse("h input int I = 0buuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuu;\n" +
                "\n" +
                "int BITS = 16;\n" +
                "\n" +
                "int O = 0;\n" +
                "\n" +
                "int m = 0;\n" +
                "int i = 0;\n" +
                "\n" +
                "while (i < BITS){\n" +
                "    m = 1<<(31-i);\n" +
                "    if (O + m <= I) {\n" +
                "        O = O + m;\n" +
                "    }\n" +
                "    i = i + 1;\n" +
                "}\n" +
                "l output int o = O;").leaks(32).run();
    }

    @Test
    public void testBinarySearch_condensed(){
        Context.LOG.setLevel(Level.FINE);
        parse("h input int I = 0buuuuuuu;\n" +
                "\n" +
                "int BITS = 3;\n" +
                "\n" +
                "int O = 0;\n" +
                "\n" +
                "int m = 0;\n" +
                "int i = 0;\n" +
                "\n" +
                "while (i < BITS){\n" +
                "    m = i;\n" +
                "    if (O + m <= I) {\n" +
                "        O = O + m;\n" +
                "    }\n" +
                "    i = i + 1;\n" +
                "}\n" +
                "l output int o = O;").leaks(32).run();
        Context.LOG.setLevel(Level.INFO);
    }

    ContextMatcher parse(String program){
        System.out.println(" ##SSA " + Parser.process(program).toPrettyString());
        return new ContextMatcher(process(program, Context.Mode.LOOP, MethodInvocationHandler.parse("summary"), false));
    }
}
