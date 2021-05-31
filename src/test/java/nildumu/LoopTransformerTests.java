package nildumu;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.logging.Level;

import static nildumu.Checks.checkAndThrow;
import static nildumu.FunctionTests.parse;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class LoopTransformerTests {


    /**
     * Just test, that it does not abort
     */
    @ParameterizedTest
    @CsvSource({
            "'int i = 0; while (i < 10){i = i + 1;} ', 1",
            "'int i = 0; int h = 1; int j = 0; while (i < 10 && j < 3){i = i + h; j = j + 1; }', 1",
            "'int i = 0; while (1){ if (i > 10) {break;} i = i + 1 }', 1",
            "'int i = 0; while (1){ if (i > 10) {continue;} i = i + 1 }', 1",
    })
    public void testBasicLoopTransform(String code, int numberOfFunctions) {
        assertEquals(numberOfFunctions, transform(code, true, true).getMethodNames().size());
    }

    @Test
    public void testCorrectBodies() {
        Parser.ProgramNode programNode = transform("int i = 0; while (i < i){ i = i; }", true, true);
        assertEquals(2, programNode.toPrettyString().split("\\(i\\)").length);
    }

    @Test
    public void testEmptyBody() {
        transform("int i = 10; while (i > 10) { }", true, true);
    }

    @Test
    public void testNestedLoops() {
        assertEquals(2, transform("int i = 0; while (i < 10) { int j = i; while (j > 0) { j = j - 1; } i = i + 1 }", true, true).getMethodNames().size());
    }

    /**
     * Wrapping an assignment in a code block alters the SSA form when it should not
     * @see IssueTests#testIssue3()
     */
    @Test
    public void testCodeBlock() {
        parse("bit_width 2;\n" +
                "h input int h = 0buu;\n" +
                "int a;\n" +
                "{ a = h; } \n" +
                "l output int o = a;").leaks(2).run();
    }

    public static Parser.ProgramNode transform(String program, boolean log, boolean ssa) {
        Context.LOG.setLevel(Level.FINE);
        Parser.MJNode.resetIdCounter();
        Lattices.Bit.resetNumberOfCreatedBits();
        Parser.ProgramNode programNode = Parser.parse(program);
        if (log) {
            System.out.println(programNode.toPrettyString());
        }
        LoopTransformer.process(programNode);
        if (ssa) {
            if (log) {
                System.out.println("-- to --");
                System.out.println(programNode.toPrettyString());
            }
            programNode = Parser.parse(programNode.toPrettyString());
            SSAResolution2.process(programNode);
        }
        System.out.println("----------\n" + programNode.toPrettyString());
        Parser.ProgramNode resolvedProgram = Parser.parse(programNode.toPrettyString());
        new NameResolution(resolvedProgram).resolve();
        //checkAndThrow(resolvedProgram);
        Parser.ProgramNode transformedProgram = new MetaOperatorTransformator(resolvedProgram.context.maxBitWidth, false).process(resolvedProgram);
        checkAndThrow(transformedProgram);
        if (log) {
            System.out.println("-- to --");
            System.out.println(transformedProgram.toPrettyString());
        }
        return transformedProgram;
    }

}
