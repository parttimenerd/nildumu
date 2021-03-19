package nildumu;

import org.junit.Test;
import org.junit.jupiter.params.ParameterizedTest;

import static nildumu.Lattices.B.ZERO;
import static nildumu.Lattices.vl;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ValueTests {

    @Test
    public void testHighBitIndicesWOSign() {
        assertEquals(0, vl.parse("0b0uu").highBitIndicesWOSign(ZERO, 0).toArray().length);
    }

}
