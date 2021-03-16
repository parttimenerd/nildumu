package nildumu;

import nildumu.mih.MethodInvocationHandler;
import nildumu.typing.TypeTransformer;
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
                        "bit_width 3;\nint __blasted_a_0; int __blasted_a_1;\n" +
                        "__blasted_a_0, __blasted_a_1 = *(1, 2);",
                parseToTransformed("bit_width 3; int[2] a = {1, 2}"));

    }

    @Test
    public void testTupleAssignment() {
        assertEquals("use_sec basic;\n" +
                        "bit_width 3;\n" +
                        "int __blasted_a_0; int __blasted_a_1; int __blasted_a_2;\n" +
                        "__blasted_a_0, __blasted_a_1, __blasted_a_2 = *(1, 2, 3);",
                parseToTransformed("bit_width 3; var a = (1, (2, 3))"));
    }

    @Test
    public void testTupleToTupleAssignment() {
        assertEquals("use_sec basic;\n" +
                        "bit_width 3;\n" +
                        "int __blasted_a_0; int __blasted_a_1; int __blasted_a_2;\n" +
                        "__blasted_a_0, __blasted_a_1, __blasted_a_2 = *(1, 2, 3);\n" +
                        "int __blasted_b_0;\n" +
                        "int __blasted_b_1;\n" +
                        "int __blasted_b_2;\n" +
                        "__blasted_b_0, __blasted_b_1, __blasted_b_2 = *(__blasted_a_0, __blasted_a_1, __blasted_a_2);",
                parseToTransformed("bit_width 3; var a = (1, (2, 3)); var b = a;"));
    }

    @Test
    public void testTupleToTupleAssignment2() {
        assertEquals("use_sec basic;\n" +
                        "bit_width 3;\n" +
                        "int __blasted_a_0; int __blasted_a_1; int __blasted_a_2;\n" +
                        "__blasted_a_0, __blasted_a_1, __blasted_a_2 = *(1, 2, 3);\n" +
                        "int __blasted_b_0;\n" +
                        "int __blasted_b_1;\n" +
                        "__blasted_b_0, __blasted_b_1 = *(__blasted_a_1, __blasted_a_2);",
                parseToTransformed("bit_width 3; var a = (1, (2, 3)); var b = a[1];"));
    }

    @Test
    public void testArrayToArrayAssignment2() {
        assertEquals("use_sec basic;\n" +
                        "bit_width 4;\n" +
                        "int __blasted_a_0; int __blasted_a_1; int __blasted_a_2; int __blasted_a_3;\n" +
                        "__blasted_a_0, __blasted_a_1, __blasted_a_2, __blasted_a_3 = *(1, 2, 3, 4);\n" +
                        "int __blasted_b_0;\n" +
                        "int __blasted_b_1;\n" +
                        "__blasted_b_0, __blasted_b_1 = *(__blasted_a_2, __blasted_a_3);",
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
}
