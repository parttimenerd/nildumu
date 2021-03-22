package nildumu;

import nildumu.mih.MethodInvocationHandler;
import nildumu.typing.TypeTransformer;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.logging.Level;

import static nildumu.FunctionTests.parse;
import static nildumu.Processor.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for the support of constant size arrays.
 * The implementation is a hack, but it is needed for some test cases.
 * The tests check that the basic functionality works (assigning, accessing and length)
 *
 *
 *
 * Idea: replace array with set of variables in preprocessor
 * Get: array_get is a function
 * Set: array_set is a function
 * Advantage: array support without altering code besides the parser
 */
public class TypeTransformerTests {

    @Test
    public void testLengthFunction() {
        assertEquals("use_sec basic;\n" +
                        "bit_width 3;\n" +
                        "int x = 3;\n" +
                        "int y = 3;\n" +
                        "int z = 2;",
                parseToTransformed("bit_width 3; int x = length({1, 2, 3}); int y = length((1, 2, 3)); int z = length({(1,), (2,)})"));
    }

    @Test
    public void testArrayDefinition() {
        assertEquals("use_sec basic;\n" +
                        "bit_width 3;\nint __bl_a_0; int __bl_a_1;\n" +
                        "__bl_a_0, __bl_a_1 = *(1, 2);",
                parseToTransformed("bit_width 3; int[2] a = {1, 2}"));

    }

    @Test
    public void testTupleAssignment() {
        assertEquals("use_sec basic;\n" +
                        "bit_width 3;\n" +
                        "int __bl_a_0; int __bl_a_1; int __bl_a_2;\n" +
                        "__bl_a_0, __bl_a_1, __bl_a_2 = *(1, 2, 3);",
                parseToTransformed("bit_width 3; var a = (1, (2, 3))"));
    }

    @Test
    public void testTupleToTupleAssignment() {
        assertEquals("use_sec basic;\n" +
                        "bit_width 3;\n" +
                        "int __bl_a_0; int __bl_a_1; int __bl_a_2;\n" +
                        "__bl_a_0, __bl_a_1, __bl_a_2 = *(1, 2, 3);\n" +
                        "int __bl_b_0;\n" +
                        "int __bl_b_1;\n" +
                        "int __bl_b_2;\n" +
                        "__bl_b_0, __bl_b_1, __bl_b_2 = *(__bl_a_0, __bl_a_1, __bl_a_2);",
                parseToTransformed("bit_width 3; var a = (1, (2, 3)); var b = a;"));
    }

    @Test
    public void testTupleToTupleAssignment2() {
        assertEquals("use_sec basic;\n" +
                        "bit_width 3;\n" +
                        "int __bl_a_0; int __bl_a_1; int __bl_a_2;\n" +
                        "__bl_a_0, __bl_a_1, __bl_a_2 = *(1, 2, 3);\n" +
                        "int __bl_b_0;\n" +
                        "int __bl_b_1;\n" +
                        "__bl_b_0, __bl_b_1 = *(__bl_a_1, __bl_a_2);",
                parseToTransformed("bit_width 3; var a = (1, (2, 3)); var b = a[1];"));
    }

    @Test
    public void testArrayToArrayAssignment2() {
        assertEquals("use_sec basic;\n" +
                        "bit_width 4;\n" +
                        "int __bl_a_0; int __bl_a_1; int __bl_a_2; int __bl_a_3;\n" +
                        "__bl_a_0, __bl_a_1, __bl_a_2, __bl_a_3 = *(1, 2, 3, 4);\n" +
                        "int __bl_b_0;\n" +
                        "int __bl_b_1;\n" +
                        "__bl_b_0, __bl_b_1 = *(__bl_a_2, __bl_a_3);",
                parseToTransformed("bit_width 4; var a = {{1,2}, {3, 4}}; var b = a[1];"));
    }

    @Test
    public void testTupleAccess() {
        parse("bit_width 3; var a = (1, 2); var b = a[1]; int c = b;").val("c", 2).run();
    }

    @Test
    public void testTupleAccess2() {
        parse("bit_width 3; var a = (1, (2, 3)); var b = a[1][0];").val("b", 2).run();
    }

    @Test
    public void testArrayUnknownIndexGet() {
        System.out.println(parseToTransformed("bit_width 4; l input int l = 0buuuu; var x = {{1, 2}, {3, 4}}[l]"));
    }

    @Test
    public void testArrayUnknownIndexGet2() {
        System.out.println(parseToTransformed("bit_width 4; l input int l = 0buuuu; var x = {{1, 2}, {3, 4}}[l]"));
        parse("bit_width 5; l input int l = 0buuuu; var x = {{1, 1}, {1, 1}}[l]; var c = x[1];").val("c", 1).run();
    }

    @Test
    public void testArrayUnknownIndexGet3() {
        parse("bit_width 5; l input int l = 0buu; var x = {{1, 1}, {1, 1}}[l]; var c = x[1];").val("c", 1).run();
    }

    private String parseToTransformed(String program) {
        Parser.ProgramNode programNode = ProcessingPipeline.createTillBeforeTypeTransformation().process(program);
        programNode = TypeTransformer.process(programNode);
        return programNode.toPrettyString();
    }

    private ContextMatcher parse(String program) {
        return parse(program, "handler=inlining;maxrec=3;bot=basic");
    }

    private ContextMatcher parse(String program, String handler) {
        Context.LOG.setLevel(Level.WARNING);
        String transformed = parseToTransformed(program);
        System.out.println(transformed);
        System.out.println(ProcessingPipeline.create().process(program));
        return new ContextMatcher(process(program, Context.Mode.LOOP, MethodInvocationHandler.parse(handler), RECORD_ALTERNATIVES));
    }

    @Test
    public void simpleLoop() {
        parse("bit_width 3; int[2] arr = {0, 0}; int i = 0; while (i < length(arr)) { arr[i] = 1; i = i + 1 } int x = arr[1]")
                .val("x", 1).run();
    }

    @ParameterizedTest
    @CsvSource({
            "'handler=inlining;maxrec=1;bot=basic', '0buuu'",
            "'basic', '0buuu'",
            "'summary', '0buuu'"
    })
    public void simpleLoopReduced(String handler, String expectedValue) {
        parse("use_sec basic;\n" +
                "bit_width 3;\n" +
                "(int[2], int) loop[[]](int[2] arr, int i){\n" +
                "  arr[i] = 1;\n" +
                "  i = (i + 1);\n" +
                "  arr, i = *loop[[]](arr, i);\n" +
                "  return (arr, i);\n" +
                "}\n" +
                "int[2] arr = {0, 0};\n" +
                "int i = 0;\n" +
                "arr, i = *loop[[]](arr, i);\n" +
                "int x = arr[1];", handler)
                .val("x", expectedValue).run();
    }

    @ParameterizedTest
    @CsvSource({
            "'handler=inlining;maxrec=1;bot=basic', '0buuu'",
            "'basic', '0buuu'",
            "'summary', '0buuu'"
    })
    public void simpleLoopReduced2(String handler, String expectedValue) {
        parse("use_sec basic;\n" +
                "bit_width 3;\n" +
                "(int[1], int) loop[[]](int[1] arr, int i){\n" +
                "  arr, i = *loop[[]](arr, i);\n" +
                "  return (arr, 0);\n" +
                "}\n" +
                "int[1] arr;\n" +
                "int i = 0;\n" +
                "int x = loop[[]]({0,}, i)[0][0];\n", handler)
                .val("x", expectedValue).run();
    }

    @Test
    public void testArrayOfLength1() {
        parse("use_sec basic;\n" +
                "bit_width 3;\n" +
                "int[1] loop(int[1] arr){\n" +
                "  return loop(arr);\n" +
                "}\n" +
                "int[1] arr = {0,};\n" +
                "int i = 0;\n" +
                "arr = loop[[]](arr);\n" +
                "int x = arr[0];")
                .val("x", "0buuu").run();
    }

    @Test
    public void simpleLoopReduced2() {
        parse("use_sec basic;\n" +
                "bit_width 3; (int[2], int) loop(int[2] arr, int i){ \n" +
                " return (arr, i); \n" +
                "} \n" +
                "int[2] arr = {0, 0};\n" +
                "int i = 0;\n" +
                "arr, i = *loop(arr, i);\n" +
                "int x = arr[1];")
                .val("x", 0).run();
    }

    @Test
    public void simpleLoopReduced3() {
        parse("use_sec basic;\n" +
                "bit_width 3; (int[2], int) loop(int[2] arr, int i){ \n" +
                " arr[1] = 1;\n" +
                " return (arr, i); \n" +
                "} \n" +
                "int[2] arr = {0, 0};\n" +
                "int i = 0;\n" +
                "arr, i = *loop(arr, i);\n" +
                "int x = arr[1];")
                .val("x", 1).run();
    }

    @Test
    public void simpleLoopReduced4() {
        parse("use_sec basic;\n" +
                "bit_width 3; (int[2], int) loop(int[2] arr, int i){ \n" +
                "return (*loop(arr, 1),); \n" +
                "} \n" +
                "int[2] arr = {0, 0};\n" +
                "int i = 0;\n" +
                "arr, i = *loop[[]](arr, i);\n" +
                "int x = arr[1];", "handler=basic")
                .val("x", "0buuu").run();
    }

    @Test
    public void testArrayInput() {
        parse("h input int[2] arr = 0buu; l output int[2] o = arr").leaks(4).run();
    }

    @Test
    public void testArrayInput2() {
        parse("h input int[2] arr = 0bu1u; int o = arr[1]").val("o", "0bu1u").run();
    }

    @Test
    @Disabled
    public void testMid() {
        parse("int mid((int, int) pos) { return (pos[0] + pos[1]) / 2}").run();
    }

    @Test
    public void testMid2() {
        parse("int mid((int, int) pos) { return ((pos[0]) + (pos[1])) / 2}");
    }

    @Test
    public void testPassArrayAsArgument() {
        parse("int[2] arr; int sum(int[2] arr) { return arr[0]; }");
    }

    @Test
    public void testArray() {
        parse("int[1] berths; while (0 < length(berths)) {}");
    }

    @Test
    public void testArray2() {
        parse("int[3] berths;\n" +
                "while (length(berths)) {\n" +
                "\tberths[0] = 0;\n" +
                "}");
    }

    @Test
    public void testArray3() {
        parse("(int,int)[1] berths;\n" +
                "int i = 0;\n" +
                "while (i < length(berths)) {\n" +
                "\tberths[i] = (0, 0);\n" +
                "}");
    }

    @Test
    public void testArray4() {
        parse("int is_solution((int,int)[1] berths) {\n" +
                "\tint sum = 0;\n" +
                "\tint i = 0;\n" +
                "\twhile (i < 2) {\n" +
                "\t\tsum = berths[i][0];\n" +
                "\t}\n" +
                "}");
    }

    @Test
    public void testUseArrayElementAsInt() {
        parse("int[2][1] berths;0 + ((berths[0])[0]);");
    }

    @Test
    public void testUninitializedArray() {
        parse("h input int secrets = 0bu{5}; int[1] vote; vote[0]=secrets; l output int out = vote[0];").leaks(5).run();
    }

    @Test
    public void testUninitializedArray2() {
        parse("use_sec basic;\n" +
                "bit_width 5;\n" +
                "int __blasted_set_1_1_1(int a0, int a1, int a2){\n" +
                "  int a12; int a11;\n" +
                "  if ((a0 == 0))\n" +
                "    {\n" +
                "      a11 = a2;\n" +
                "    }\n" +
                "  a12 = phi(a11, a1);\n" +
                "  return a12;\n" +
                "}\n" +
                "int b = __blasted_set_1_1_1(0, b, 1);\n").val("b", 1).run();
    }
}
