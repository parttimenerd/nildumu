package nildumu;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.logging.Level;

import static nildumu.Checks.checkAndThrow;
import static nildumu.Parser.generator;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class LoopTransformerTests {


    /**
     * Just test, that it does not abort
     */
    @ParameterizedTest
    @CsvSource({
            "'int i = 0; while (i < 10){i = i + 1;} ', 1",
            "'int i = 0; int h = 1; int j = 0; while (i < 10 && j < 3){i = i + h; j = j + 1; }', 1",
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

    public static Parser.ProgramNode transform(String program, boolean log, boolean ssa) {
        Context.LOG.setLevel(Level.FINE);
        Parser.MJNode.resetIdCounter();
        Lattices.Bit.resetNumberOfCreatedBits();
        Parser.ProgramNode programNode = (Parser.ProgramNode) generator.parse(program);
        if (log) {
            System.out.println(programNode.toPrettyString());
        }
        LoopTransformer.process(programNode);
        if (ssa) {
            if (log) {
                System.out.println("-- to --");
                System.out.println(programNode.toPrettyString());
            }
            programNode = (Parser.ProgramNode) generator.parse(programNode.toPrettyString());
            SSAResolution2.process(programNode);
        }
        Parser.ProgramNode resolvedProgram = (Parser.ProgramNode) Parser.generator.parse(programNode.toPrettyString());
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
