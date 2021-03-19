package nildumu;

import nildumu.mih.MethodInvocationHandler;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.logging.Level;

import static java.time.Duration.ofMillis;
import static nildumu.Processor.RECORD_ALTERNATIVES;
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
    public void testBasicLoop() {
        String program = "h input int h = 0b0u;\n" +
                "int x = 0;\n" +
                "while (h == 0){\n" +
                "\tx = x + 1;\n" +
                "}\n" +
                "l output int o = x;";
        parse(program).leaks(1).run();
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
    public void testBasicLoop_condensed() {
        String program = "h input int h = 0b0u;\n" +
                "int x = 0;\n" +
                "while (h == 0){\n" +
                "\tx = x | 0b11;\n" +
                "}\n" +
                "l output int o = x;";
        parse(program).bit("o[1]", "u").leaks(1).run();
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
    public void testBasicLoop3() {
        String program = "h input int h = 0bu;\n" +
                "while (h){\n" +
                "\th = h;\n" +
                "}\n" +
                "l output int o = h";
        parse(program).leaks(1).val("o", "0bu").run();
    }

    @Test
    public void testBasicLoop3_condensed() {
        String program = "h input int h = 0bu;\n" +
                "while (h){\n" +
                "\th = 1;\n" +
                "}\n" +
                "l output int o = h";
        parse(program).leaks(1).run();
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
        assertTimeoutPreemptively(ofMillis(100000), () -> {
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
                    "}\n l output int o = h").leaks(1).run();
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
    public void testBasicLoop4_condensed() {
        Lattices.Bit.toStringGivesBitNo = true;
        assertTimeoutPreemptively(ofMillis(1000000), () -> {
            String program = "bit_width 3;\n" +
                    "h input int h = 0b0u;\n" +
                    "l input int l = 0bu;\n" +
                    "while (l){\n" +
                    "  h = [2](h[2] | h[1]);\n" +
                    "}\n" +
                    "l output int o = h;";
            parse(program).leaks(1).run();
        });
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
    @ValueSource(ints = {3, 4, 10})
    public void testBasicLoop4_condensed2(int secretSize) {
        assertTimeoutPreemptively(ofMillis(1000000), () -> {
            String program = String.format("bit_width %d;\n", secretSize) +
                    String.format("h input int h = 0b%s;\n", iter("u", secretSize)) +
                    "l input int l = 0bu;\n" +
                    "while (l){\n" +
                    "  h = [2](h[2] | h[1]);\n" +
                    "}\n" +
                    "l output int o = h;";
            parse(program).leaks(secretSize).run();
        });
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
        assertTimeoutPreemptively(ofMillis(1000000), () -> {
            String program = "     bit_width 2;\n" +
                    "     h input int h = 0b0u;\n" +
                    "     l input int l = 0bu;\n" +
                    "     while (l){\n" +
                    "        while (l){\n" +
                    "            h = [2](h[2] | h[1]);\n" +
                    "        }\n" +
                    "     }\n" +
                    "     l output int o = h;";
            parse(program).leaks(1).run();
        });
    }

    /*@Test
    public void testElectronicPurse(){
        parse("h input int h = 0buuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuu;\n" +
                "int z = 0;\n" +
                "while (h >= 1){\n" +
                "    h = h - 1;\n" +
                "    z = z + 1;\n" +
                "}\n" +
                "l output int o = z;").leaks(32).run();
    }*/

    @Test
    public void testElectronicPurseCondensed() {
        String program = "h input int h = 0buu;\n" +
                "int z = 0;\n" +
                "while (h != 1){\n" +
                "    h = h + 1;\n" +
                "    z = z + 1;\n" +
                "}\n" +
                "l output int o = z;";
        parse(program).leaks(2).run();
    }

    @Test
    public void testElectronicPurseCondensed2() {
        Context.LOG.setLevel(Level.FINE);
        String program = "h input int h = 0buuu;\n" +
                "int z = 0;\n" +
                "while (h != 0){\n" +
                "    h = h - 1;\n" +
                "    z = h;\n" +
                "}\n" +
                "l output int o = z;";
        parse(program).leaks(3).run();
        Context.LOG.setLevel(Level.INFO);
    }

    @Test
    public void testBinarySearch() {
        String program = "h input int I = 0bu{32};\n" +
                "\n" +
                "int BITS = 16;\n" +
                "\n" +
                "int O = 0;\n" +
                "\n" +
                "int m = 0;\n" +
                "int i = 0;\n" +
                "\n" +
                "while (i < BITS){\n" +
                "    m = 1<<(30-i);\n" +
                "    if (O + m <= I) {\n" +
                "        O = O + m;\n" +
                "    }\n" +
                "    i = i + 1;\n" +
                "}\n" +
                "l output int o = O;";
        parse(program).leaks(32).run(); // over approximate
        parse(program, 10).leaks(32).run(); // over approximate
        parse(program, 40).leaks(16).run(); // enough unroll
    }

    @Test
    @Disabled("just the plain old loop mode")
    public void testBinarySearchWithBasicLoopMode() {
        String program = "h input int I = 0bu{5};\n" +
                "int O = 0;\n" +
                "while (O != I){\n" +
                "    if (O != I) {\n" +
                "        O = O + 1;\n" +
                "    }\n" +
                "}\n" +
                "l output int o = O;";
        parse(program).val("o", "0bu{5}").leaks(5).run();
    }

    @Test
    public void testBinarySearch_condensedABitLarger2() {
        String program = "h input int I = 0bu{4};\n" +
                "int BITS = 2;\n" +
                "int O = 0;\n" +
                "int m = 0;\n" +
                "int i = 0;\n" +
                "while (i < 2){\n" +
                "    m = 1<<(2-i);\n" +
                "    if (O + m <= I) {\n" +
                "        O = O + m;\n" +
                "    }\n" +
                "    i = i + 1;\n" +
                "}\n" +
                "l output int o = O;";
        parse(program).leaks(2).run();
    }

    @Test
    @Timeout(3)
    public void testBinarySearch_condensedWithoutLoop() {
        String program = "h input int I = 0buuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuu;\n" +
                "int O = 0;\n" +
                "\n" +
                "int m = 0;\n" +
                "int i = 0;\n" +
                "\n" +
                "    m = 1<<(30-i);\n" +
                "    if (O + m <= I) {\n" +
                "        O = O + m;\n" +
                "    }\n" +
                "    i = i + 1;\n" +
                "l output int o = O;";
        parse(program).leaks(1).run();
    }

    /**
     * Should leak unrolls - 1 bits of information if unrolls < inlining (here 10) else 32
     */
    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 20, 100})
    public void testBinarySearch_condensed(int unrolls) {
        final int inlining = 10;
        parse("  bit_width 32; " +
                "h input int I = 0bu{32};\n" +
                "\n" +
                "int BITS = " + unrolls + ";\n" +
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
                "l output int o = O;", inlining).leaks(unrolls + 1 > inlining ? 32 : unrolls - 1).run();
    }

    @Test
    public void testBinarySearch_condensed2() {
        String program = "bit_width 3; " +
                "int o = 0;\n" +
                "int m = 0;\n" +
                "int i = 0;\n" +
                "\n" +
                "while (i != 1){\n" +
                "    m = i;\n" +
                "       o = o + m;\n" +
                "    i = i + 1;\n" +
                "}\nint x = i;";
        parse(program).val("x", 1).run();
        //parse(program).val("x", "0buuu").run();
        Context.LOG.setLevel(Level.INFO);
    }

    static ContextMatcher parse(String program) {
        return parse(program, "handler=inlining;maxrec=5;bot=summary");
    }

    static ContextMatcher parse(String program, int inlining) {
        return parse(program, String.format("handler=inlining;maxrec=%d;bot=summary", inlining));
    }

    static ContextMatcher parse(String program, String mih) {
        Context.LOG.setLevel(Level.WARNING);
        //System.out.println(" ##SSA " + Parser.process(program, false, transformLoops).toPrettyString());
        return new ContextMatcher(process(program, Context.Mode.LOOP, MethodInvocationHandler.parse(mih), RECORD_ALTERNATIVES));
    }


    @Test
    public void testTransformedLoop() {
        parse("use_sec basic;\n" +
                "bit_width 4;\n" +
                "int i = 0;\n" +
                "while (i != 1){ i = i + 1; }\n" +
                "int x = i;").val("x", "1").run();
    }

    @Test
    public void testTransformedLoop2() {
        parse("use_sec basic;\n" +
                "bit_width 4;\n" +
                "int i = 0;\n" +
                "while (i < 1){ i = i + 1; }\n" +
                "int x = i;").val("x", "1").run();
    }
}
