package nildumu.intervals;

import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.generator.GeneratorConfiguration;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import nildumu.Lattices;
import nildumu.intervals.Intervals;
import org.junit.Before;
import org.junit.runner.RunWith;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static nildumu.Lattices.B.*;
import static nildumu.Lattices.B.U;
import static nildumu.Lattices.vl;
import static org.junit.Assert.assertEquals;

@RunWith(JUnitQuickcheck.class)
public class SizeTest {

    @Target({ PARAMETER, FIELD, ANNOTATION_TYPE, TYPE_USE })
    @Retention(RUNTIME)
    @GeneratorConfiguration
    public @interface IntervalConf {
        int min();
        int max();

        /**
         * Number of constraints
         */
        int minN();
        int maxN();

        int minNDigit() default 0;
        int maxNDigit() default -1;

        boolean excludeSignBit() default true;
    }

    public static class IntervalGenerator extends Generator<Intervals.ConstrainedInterval> {
        private IntervalConf conf;

        public IntervalGenerator(){
            super(Intervals.ConstrainedInterval.class);
        }

        public void configure(IntervalConf conf){
            this.conf = conf;
        }

        @Override
        public Intervals.ConstrainedInterval generate(SourceOfRandomness r, GenerationStatus g) {
            int width = (int)Math.ceil(Math.log(Math.max(Math.abs(conf.max()), Math.abs(conf.min())) / Math.log(2)));
            Map<Integer, Lattices.B> constraints = new HashMap<>();
            int n = r.nextInt(conf.minN(), conf.maxN());
            Optional<Lattices.B> signConstraint = Optional.empty();
            if (!conf.excludeSignBit() && r.nextBoolean()){
                n = n - 1;
                signConstraint = Optional.of(r.nextBoolean() ? ZERO : ONE);
            }
            int maxNDigit = conf.maxNDigit() == -1 ? width : Math.min(conf.maxNDigit() + 1, width);
            List<Lattices.B> middle = IntStream.range(0, maxNDigit - conf.minNDigit()).mapToObj(i -> r.nextBoolean() ? Lattices.B.ZERO : Lattices.B.ONE).collect(Collectors.toList());
            Collections.shuffle(middle, r.toJDKRandom());
            List<Lattices.B> ints = Stream.concat(Stream.concat(
                    IntStream.range(0, conf.minNDigit()).mapToObj(i -> U),
                    middle.stream()),
                    IntStream.range(0, width).mapToObj(i -> U)).collect(Collectors.toList());
            IntStream.range(0, ints.size()).forEach(i -> {
                if (ints.get(i) != U){
                    constraints.put(i, ints.get(i));
                }
            });
            signConstraint.ifPresent(b -> constraints.put(vl.bitWidth - 1, b));
            int start = r.nextInt(conf.min(), conf.max());
            int end = r.nextInt(start, conf.max());
            return new Intervals.ConstrainedInterval(new Intervals.Interval(start, end), constraints);
        }

        @Override
        public boolean canShrink(Object larger) {
            return larger instanceof Intervals.ConstrainedInterval && ((Intervals.ConstrainedInterval) larger).constraints.size() > 0;
        }

        @Override
        public List<Intervals.ConstrainedInterval> doShrink(SourceOfRandomness r, Intervals.ConstrainedInterval larger) {
            List<Map.Entry<Integer, Lattices.B>> constraintList = new ArrayList<>(larger.constraints.entrySet());
            return IntStream.range(0, larger.constraints.size()).mapToObj(i -> {
                Map<Integer, Lattices.B> constraints = new HashMap<>();
                for (int j = 0; j < constraintList.size(); j++){
                    if (i != j){
                        constraints.put(constraintList.get(j).getKey(), constraintList.get(j).getValue());
                    }
                }
                return new Intervals.ConstrainedInterval(larger.interval, constraints);
            }).collect(Collectors.toList());
        }
    }

    @Before
    public void setUp(){
        vl.bitWidth = 10;
    }

    @Property
    public void fixBit0(@From(IntervalGenerator.class) @IntervalConf(min=0, max=1, minN=1, maxN=1, maxNDigit=0)
                                 Intervals.ConstrainedInterval interval){
        assertIntervalSize(interval);
    }

    @Property
    public void fixBit1(@From(IntervalGenerator.class) @IntervalConf(min=0, max=2, minN=1, maxN=1, minNDigit=1, maxNDigit=1)
                                Intervals.ConstrainedInterval interval){
        assertIntervalSize(interval);
    }

    void assertIntervalSize(Intervals.ConstrainedInterval interval){
        int expected = interval.size();
        System.out.println(interval);
        assertEquals(String.format("Count elements in %s", interval), expected, interval.approximateSize());
    }
}
