package nildumu;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ReturnTransformerTests {

    @ParameterizedTest
    @CsvSource({
            "'int func(){ return 1; }', 'int func(){ return 1; }'",
            "'int func(){ if (1){ return 1 } }', 'int func(){ int ___return_taken = 0; int ___return_val; if (1) { ___return_taken = 1; ___return_val = 1; } return ___return_val; }'",
            "'int func(){ if (1){ return 1 } return 2; }', 'int func(){ int ___return_taken = 0; int ___return_val; if (1) { ___return_taken = 1; ___return_val = 1; } if (!___return_taken) { ___return_taken = 1; ___return_val = 2; } return ___return_val; }'"
    })
    public void testBasicTransformation(String in, String out) {
        assertEquals("use_sec basic; bit_width 32; " + out, process("bit_width 32; " + in).trim());
    }

    @Test
    public void testEvaluation() {
        assertEquals(2, evaluate("int f(int a, int b) { if (a == 1) { return 1; } return 2; }", "f(2, 2)").asLong());
    }

    public String process(String program) {
        Parser.ProgramNode programNode = Parser.parse(program);
        ReturnTransformer.process(programNode);
        return programNode.toPrettyString("", "").replace("\n", " ");
    }

    public Lattices.Value evaluate(String intFunction, String call) {
        return Processor.process(intFunction + " int res = " + call).getVariableValue("res");
    }
}
