package nildumu.intervals;

import nildumu.Context;
import nildumu.ContextMatcher;
import nildumu.Parser;
import nildumu.Processor;
import nildumu.mih.MethodInvocationHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static nildumu.Lattices.vl;
import static nildumu.Processor.USE_REPLACEMENTS;
import static org.junit.Assert.assertEquals;

public class IntervalTests {

    @ParameterizedTest
    @ValueSource(strings = {
            "11|15|1", "0|3|uu0u", "0|9|uu0u", "0|2|0", "0|3|0", "2|2|u1", "1|7|u1uuuu", "0|1|u1u",
            "1|1|0u", "0|0|uu", "1|2|uu1u", "2|2|u0u", "0|0|u0", "1|2|u0u", "2|2|u1u",
            "-1|1|0u", "-1|-1|uuuu1", "-1|0|uuuu1"
    })
    public void testSize(String val) {
        vl.bitWidth = 5;
        Intervals.ConstrainedInterval interval = Intervals.ConstrainedInterval.parse(val);
        System.out.println(interval);
        assertEquals(interval.size(), interval.size(), Intervals.countPattern(interval.start, interval.end,
                new Intervals.ListConstraints(interval.constraints)));
    }

    @Test
    public void testForBitwidth(){
        assertEquals(4, Interval.forBitWidth(2).size());
    }

    @Test
    public void testBasicIf(){
        new ContextMatcher(Processor.process(Parser.process("bit_width 2; h input int h = 0buu;\n" +
                "int x = 0;\n" +
                "if (h < 1){\n" +
                "    x = h;\n" +
                "}" +
                "l output int o = x;"), Context.Mode.INTERVAL, MethodInvocationHandler.createDefault(), true, true, true)).numberOfOutputs(3).run();
    }
}
