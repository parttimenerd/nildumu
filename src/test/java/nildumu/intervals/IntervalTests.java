package nildumu.intervals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static nildumu.Lattices.vl;
import static org.junit.Assert.assertEquals;

public class IntervalTests {

    @ParameterizedTest
    @ValueSource(strings = {
            "11|15|1", "0|3|uu0u", "0|9|uu0u", "0|2|0", "0|3|0", "2|2|u1", "1|7|u1uuuu", "0|1|u1u",
            "1|1|0u", "0|0|uu", "1|2|uu1u", "2|2|u0u", "0|0|u0", "1|2|u0u", "2|2|u1u"
    })
    public void testSize(String val) {
        vl.bitWidth = 5;
        Intervals.ConstrainedInterval interval = Intervals.ConstrainedInterval.parse(val);
        System.out.println(interval);
        assertEquals(interval.size(), interval.approximateSize());
    }


}
