package nildumu;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static nildumu.FunctionTests.parse;

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
public class ArrayTests {

    @ParameterizedTest
    @CsvSource({
            "'bit_width 3; int arr = 0b0{[2]}', '0b0{6}'",
            "'bit_width 3; l input int arr = 0bu{[2]}', '0bu{6}'"
    })
    public void testCreateArray(String program, String expectedValue) {
        parse(program).val("arr", expectedValue).run();
    }

    @Test
    public void testLengthFunction() {
        parse("bit_width 3; int arr = 0b0{[3]}; int x = length(arr)").val("x", 3).run();
    }

    @Test
    public void testBasicAccess() {
        parse("bit_width 3; int arr = 0b0{[3]}; arr[1] = 1; int x = arr[1]; int y = arr[0]")
                .val("x", 1).val("y", 0).run();
    }

    @Test
    public void testAssignmentAndArithmetic() {
        parse("bit_width 3; int arr = 0b0{[3]}; int brr = arr + 1; int x = arr[0]").val("x", 1).run();
    }

    @Test
    public void simpleLoop() {
        parse("bit_width 3; int arr = 0b0{[3]}; int i = 0; while (i < length(arr)) { arr[i] = 1; i += 1 }; int x = arr[1]")
                .val("x", 1).run();
    }
}
