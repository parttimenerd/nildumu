package nildumu;

import org.junit.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import java.util.logging.Level;
import java.util.stream.Stream;

import static java.time.Duration.ofSeconds;
import static nildumu.Parser.*;
import static nildumu.Processor.process;
import static org.junit.jupiter.api.Assertions.*;

public class FunctionTests {

    @BeforeClass
    public void setUp(){
        Processor.transformPlus = true;
    }

    @AfterClass
    public void tearDown(){
        Processor.transformPlus = false;
    }

    @ParameterizedTest
    @ValueSource(strings = { "int bla(){}", "int bla1(int blub){}", "int bla1r(int blub){ return blub }" })
    public void testFunctionDefinition(String program){
        parse(program);
    }

    @ParameterizedTest
    @ValueSource(strings = {"int bla(){} bla()", "int bla(int i){} bla(1)", "int bla(int i, int j){} bla(1,2)"})
    public void testBasicFunctionCall(String program){
        parse(program);
    }

    @ParameterizedTest
    @CsvSource({
            "'int bla(){return 1} int x = bla()', '1'",
            "'int bla(int a){return a} int x = bla(1)', '1'",
            "'int bla(int a){return a | 1} int x = bla(1)', '1'",
            "'bit_width 3; int bla(int a){return a + 1} int x = bla(1)', '2'"
    })
    public void testBasicFunctionCalls(String program, String expectedValue){
        Context.LOG.setLevel(Level.FINE);
        parse(program).val("x", expectedValue).run();
    }

    @ParameterizedTest
    @MethodSource("handlers")
    public void testTrivialRecursionTerminates(String handler){
        assertTimeoutPreemptively(ofSeconds(100), () -> parse("int bla(){ return bla() }", handler));
    }
    
    /**
     <code>
     bit_width 2;
h input int h = 0b0u;
int fib(int a){
	int r = 1;
	if (a > 1){
		r = fib(a - 1) + fib(a - 2);
	}
	return r;
}
l output int o = fib(h);
     </code>
     */
    @ParameterizedTest
    @MethodSource("handlers")
    public void testFibonacci(String handler){
        assertTimeoutPreemptively(ofSeconds(5), () -> parse("bit_width 2;\n" +
"h input int h = 0b0u;\n" +
"int fib(int a){\n" +
"	int r = 1;\n" +
"	if (a > 1){\n" +
"		r = fib(a - 1) + fib(a - 2);\n" +
"	}\n" +
"	return r;\n" +
"}\n" +
"l output int o = fib(h);", handler)).leaks(1).run();
    }

    @ParameterizedTest
    @MethodSource("handlers")
    public void testFibonacci2(String handler){
        assertTimeoutPreemptively(ofSeconds(10), () -> parse(//"bit_width 2;\n" +
                "h input int h = 0b0uuuuuu;\n" +
                "int fib(int a){\n" +
                "	int r = 1;\n" +
                "	if (a > 1){\n" +
                "		r = fib(a - 1);\n" +
                "	}\n" +
                "	return r;\n" +
                "}\n" +
                "l output int o = fib(h);", handler)).leaks(6).run();
    }


    /**
     <code>
     bit_width 2;
     h input int h = 0b0u;
     int f(int a){
        return f(a) | 1;
     }
     l output int o = f(h);
     </code>
     */
    @ParameterizedTest
    @CsvSource({
            "'handler=basic', 'o[1]=u;o[2]=u;h[1]=u;h[2]=0'",
            "'handler=call_string', 'o[1]=1'",
            "'handler=call_string;maxrec=3', 'o[1]=1'"
    })
    public void testDepsOnFunctionResult(String handler, String bitComp){
        Lattices.Bit.toStringGivesBitNo = true;
        assertTimeoutPreemptively(ofSeconds(10000), () -> parse("bit_width 2;\n" +
                "     h input int h = 0b0u;\n" +
                "     int f(int a){\n" +
                "        return f(a) | 1;\n" +
                "     }\n" +
                "     l output int o = f(h);", handler).bits(bitComp).leaks(1).run());
    }


    /**
     <code>
     bit_width 2;
     h input int h = 0b0u;
     l input int l = 0b0u;
     int res = 0;
     int fib(int a){
         int r = 1;
         while (a > 0){
             if (a > 1){
                r = r + fib(a - 1);
             }
         }
         return r;
     }
     while (l) {
        res = res + fib(h);
     }
     l output int o = fib(h);
     </code>
     */
    @ParameterizedTest
    @MethodSource("handlers")
    public void testWeirdFibonacciTermination(String handler){
        Context.LOG.setLevel(Level.INFO);
        assertTimeoutPreemptively(ofSeconds(10000), () -> parse(
                "     h input int h = 0b0u;\n" +
                "     l input int l = 0b0u;\n" +
                "     int res = 0;\n" +
                "     int fib(int a){\n" +
                "         int r = 1;\n" +
                "         while (a > 0){\n" +
                "             if (a > 1){\n" +
                "                r = r + fib(a - 1);\n" +
                "             }\n" +
                "         }\n" +
                "         return r;\n" +
                "     }\n" +
                "     while (l) {\n" +
                "        res = res + fib(h);\n" +
                "     }\n" +
                "     l output int o = fib(h);", handler)).leaks(1).run();
    }

    /**
     <code>
     int f(int x) {
        return g(x);
     }
     int g(int x) {
        return h(x);
     }
     int h(int x) {
        return x;
     }
     high input int h = 0buu;
     low output int o = f(h);
     </code>
     Should lead to a leakage of at least 2 bits
     */
    @ParameterizedTest
    @MethodSource("handlers")
    //@ValueSource(strings = {"handler=summary;mode=ind;dot=tmp2"})
    public void testNestedMethodCalls(String handler){
        parse("int f(int x) {\n" +
                "\t    return g(x);\n" +
                "    }\n" +
                "    int g(int x) {\n" +
                "\t    return h(x);\n" +
                "    }\n" +
                "    int h(int x) {\n" +
                "\t    return x;\n" +
                "    }\n" +
                "    high input int h = 0buu;\n" +
                "    low output int o = f(h);", handler).leaksAtLeast(2).run();
    }

    @ParameterizedTest
    @MethodSource("handlers")
    //@ValueSource(strings = {"handler=summary;mode=ind;dot=tmp2"})
    public void testNestedMethodCalls_smaller(String handler){
        parse("int f(int x) {\n" +
                "\t    return x;\n" +
                "    }\n" +
                "    high input int h = 0buu;\n" +
                "    low output int o = f(h);", handler).leaksAtLeast(2).run();
    }

    @ParameterizedTest
    @MethodSource("handlers")
    //@ValueSource(strings = {"handler=summary;mode=ind;dot=tmp2"})
    public void testNestedMethodCalls_smaller2(String handler){
        parse("int f(int x) {\n" +
                "\t    return h(x);\n" +
                "    } " +
                "int h(int x){ return x }\n" +
                "    high input int h = 0buu;\n" +
                "    low output int o = f(h);", handler).leaksAtLeast(2).run();
    }

    @ParameterizedTest
    @ValueSource(strings = {"basic", "handler=summary;reduction=basic;bot=basic;dot=tmp2"})
    public void testNestedMethodCalls_smaller3(String handler){
        Context.LOG.setLevel(Level.FINE);
        parse("int f(int x) {\n" +
                "\t    return h(x);\n" +
                "    } " +
                "int h(int x){ return x }\n" +
                "    high input int h = 0buu;\n" +
                "    low output int o = f(h);", handler).leaksAtLeast(2).run();
        Context.LOG.setLevel(Level.INFO);
    }

    /**
     <code>
     bit_width 3;
     int f(int x, int y, int z, int w, int v, int l) {
         int r = 0;
         if (l == 0) {
            r = v;
         } else {
            r = f(0, x, y, z, w, l+0b111);
         }
         return r;
     }
     high input int h = 0buuu;
     low output int o = f(h, 0, 0, 0, 0, 4);
     </code>
     should leak 3 bits
     */
    @ParameterizedTest
    @MethodSource("handlers")
    public void testConditionalRecursion(String handler){
        parse("bit_width 3;\n" +
                "    int f(int x, int y, int z, int w, int v, int l) {\n" +
                "\t    int r = 0;\n" +
                "\t    if (l == 0) {\n" +
                "\t\t    r = v;\n" +
                "\t    } else {\n" +
                "\t\t    r = f(0, x, y, z, w, l+0b111);\n" +
                "\t    }\n" +
                "\t    return r;\n" +
                "    }\n" +
                "    high input int h = 0buuu;\n" +
                "    low output int o = f(h, 0, 0, 0, 0, 4);", handler).leaksAtLeast(3).run();
    }

    @Test
    public void testCallGraphGeneration(){
        ProgramNode program = Parser.process("int bla(){return bla()} bla()");
        MethodNode blaMethod = program.getMethod("bla");
        CallGraph g = new CallGraph(program);
        //g.writeDotGraph(Paths.get("tmp"), "call_graph");
        assertAll(
                () -> assertEquals(1, g.loopDepth(blaMethod), "Wrong loop depth for bla()"),
                () -> assertTrue(g.dominators(blaMethod).contains(blaMethod), "bla() dominates itself")
        );
    }

    @Test
    public void testMoreComplexCallGraphGeneration(){
        ProgramNode program = Parser.process("int f() { g(); z ()} int g(){ h(); g(); f() } int h(){ g() } int z(){} f()");
        MethodNode blaMethod = program.getMethod("bla");
        CallGraph g = new CallGraph(program);
        //g.writeDotGraph(Paths.get("tmp"), "call_graph2");
        assertAll(
                () -> assertEquals(2, g.loopDepth(program.getMethod("h")), "Wrong loop depth for h"),
                () -> assertTrue(g.dominators(program.getMethod("h")).contains(program.getMethod("f")), "f() dominates h()")
        );
    }

    public static void main(String[] args){
        Context.LOG.setLevel(Level.INFO);
        String program = "     h input int h = 0b0uuuuuu;\n" +
                "     l input int l = 0b0u;\n" +
                "     int res = 0;\n" +
                "     int fib(int a){\n" +
                "         int r = 1;\n" +
                "         while (a > 0){\n" +
                "             if (a > 1){\n" +
                "                r = r + fib(a - 1);\n" +
                "             }\n" +
                "         }\n" +
                "         return r;\n" +
                "     }\n" +
                "     while (l) {\n" +
                "        res = res + fib(h);\n" +
                "     }\n" +
                "     l output int o = fib(h); ";
        System.err.println(Parser.process(program, false).toPrettyString());
       parse(program, MethodInvocationHandler.parse("handler=call_string;maxrec=1;bot=summary_mc"));
    }

    @Test
    public void bla(){
        String program = "bit_width 2;\n" +
                "int f(int a){\n" +
                "	return a + 1;\n" +
                "} f(1)" ;
        System.err.println(Parser.process(program).toPrettyString());
        parse(program, MethodInvocationHandler.parse("handler=summary;maxiter=2;bot=basic;dot=dots23.dot"));
    }

    @RepeatedTest(value = 3)
    public void testFunctionChain(){
        parse("h input int h = 0b0u;\n" +
                "int f1(int a){\n" +
                "\treturn a;\n" +
                "}\n" +
                "int f2(int a){\n" +
                "\treturn f1(a) & f1(a) & f1(a);\n" +
                "}\n" +
                "int f3(int a){\n" +
                "\treturn f2(a) & f2(a) & f2(a);\n" +
                "}\n" +
                "int f4(int a){\n" +
                "\treturn f3(a) & f3(a) & f3(a);\n" +
                "}\n" +
                "l output int o = f4(h);", "summary").leaks(1).run();
    }

    @Test
    public void testFunctionChain_1(){
        String program ="h input int h = 0bu;\n" +
                "int f1(int a){\n" +
                "\treturn a;\n" +
                "}\n" +
                "int f2(int a){\n" +
                "\treturn f1(a);\n" +
                "}\n" +
                "l output int o = f2(h);";
        ProgramNode node = Parser.process(program);
        Context.LOG.setLevel(Level.FINE);
        Context c1 = Processor.process(node, Context.Mode.LOOP, MethodInvocationHandler.parse("handler=summary;dot=dots1"));
        Context.LOG.setLevel(Level.INFO);
        //node = Parser.process(program);
        //Context c2 = Processor.process(node, MethodInvocationHandler.parse("handler=summary"));
        //new Thread(() -> JungPanel.show(c1.getJungGraphForVisu(c1.sl.bot()))).start();

        //JungPanel.show(c2.getJungGraphForVisu(c1.sl.bot()));
        new ContextMatcher(c1).leaks(1).run();
        //new ContextMatcher(c2).leaks(1).run();
    }

    static ContextMatcher parse(String program){
        return parse(program, MethodInvocationHandler.createDefault());
    }

    static ContextMatcher parse(String program, String handler){
        return new ContextMatcher(process(program, Context.Mode.LOOP, MethodInvocationHandler.parse(handler)));
    }

    static ContextMatcher parse(String program, MethodInvocationHandler handler) {
        return new ContextMatcher(process(program, Context.Mode.LOOP, handler));
    }

    static ContextMatcher parse(String program, String handler, int bitWidth) {
        if (bitWidth > 1){
            Context.LOG.setLevel(Level.INFO);
        }
        return parse(String.format("bit_width %d;\n%s", bitWidth, program), handler);
    }

    static Stream<String> handlers(){
        return Stream.concat(Stream.of("handler=basic", "handler=call_string;maxrec=1;bot=basic", "handler=call_string;maxrec=2;bot=basic", "summary","handler=summary;mode=ind"), MethodInvocationHandler.getExamplePropLines().stream());
    }

    static Stream<Arguments> handlersWBitWidth(){
        return handlers().flatMap(s -> Stream.of(2, 3).map(i -> Arguments.of(s, i)));
    }
}
