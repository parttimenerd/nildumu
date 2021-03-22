package nildumu;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TypingTests {

    @ParameterizedTest
    @CsvSource({
            "'(int, int) value = (1, 1)', 'use_sec basic;\nbit_width 2;\n(int, int) value = (1, 1);'",
            "'(int, (int, int)) value = (1, 1)', 'use_sec basic;\nbit_width 2;\n(int, (int, int)) value = (1, 1);'",
            "'int[2] value = {1, 1}', 'use_sec basic;\nbit_width 2;\nint[2] value = {1, 1};'",
            "'var value = {1, 1}', 'use_sec basic;\nbit_width 2;\nvar value = {1, 1};'",
            "'var value = {1, 1}; value, value = *value', 'use_sec basic;\nbit_width 2;\nvar value = {1, 1};\nvalue, value = *value;'",
            "'var value = {1, 1}; var val = (*value,)', 'use_sec basic;\nbit_width 2;\nvar value = {1, 1};\nvar val = (*value,);'",
            "'var value = {1, 1}; var val = (1, *value)', 'use_sec basic;\nbit_width 2;\nvar value = {1, 1};\nvar val = (1, *value);'",
            "'var value = {1, 1}; var val = (1, *value, 1)', 'use_sec basic;\nbit_width 2;\nvar value = {1, 1};\nvar val = (1, *value, 1);'",
            "'var value = {1, 1}; var val = (1, *value, 1, 2)', 'use_sec basic;\nbit_width 3;\nvar value = {1, 1};\nvar val = (1, *value, 1, 2);'",
            "'var value = {1, 1}; var val = {1, *value, 1, 2}', 'use_sec basic;\nbit_width 3;\nvar value = {1, 1};\nvar val = {1, *value, 1, 2};'"
    })
    public void testParseExpressions(String program, String expectedParsingResult) {
        assertEquals(expectedParsingResult, Parser.generator.parse(program).toPrettyString());
    }

    @ParameterizedTest
    @CsvSource({
            "'h input int[2] value = 0b0uu', 'int[2]'",
            "'var value = 1', 'int'",
            "'var value = (1, 1)', '(int, int)'",
            "'var value = (1, (1, 1))', '(int, (int, int))'",
            "'var val = (1, 1); var value = (1, *val)', '(int, int, int)'",
            "'(int) method() { return (1,); } int var_; var_ = *method(); var value = var_', 'int'",
            "'(int, int) method() { return (1, 1); } int var_; int var_2; var_, var_2 = *method(); var value = var_', 'int'"
    })
    public void testTypes(String code, String expectedType) {
        Parser.ProgramNode program = ProcessingPipeline.createTillBeforeTypeTransformation().process(code);
        NameResolution nameResolution = new NameResolution(program);
        nameResolution.resolve();
        assertEquals(expectedType, ((Parser.VariableDeclarationNode) program.globalBlock.statementNodes.get(program.globalBlock.statementNodes.size() - 1)).getVarType().toString());
    }
}
