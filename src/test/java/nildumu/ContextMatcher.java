package nildumu;

import com.kitfox.svg.A;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static nildumu.Lattices.*;
import static org.junit.jupiter.api.Assertions.*;

public class ContextMatcher {

    public static class TestBuilder {
        List<Executable> testers = new ArrayList<>();


        public TestBuilder add(Executable tester){
            testers.add(tester);
            return this;
        }

        public void run(){
            assertAll(testers.toArray(new Executable[0]));
        }
    }

    private final Context context;
    private final TestBuilder builder = new TestBuilder();
    private MinCut.Algo[] algos = MinCut.Algo.values();

    public ContextMatcher(Context context) {
        this.context = context;
    }

    public ContextMatcher use(MinCut.Algo algo) {
        algos = new MinCut.Algo[]{algo};
        return this;
    }

    public ContextMatcher useSingleMCAlgo() {
        return use(MinCut.usedAlgo);
    }

    public ContextMatcher val(String variable, int value){
        Lattices.Value actual = getValue(variable);
        builder.add(() -> assertTrue(actual.isConstant(),
                String.format("Variable %s should have an integer val, has %s: %s vs %s",
                variable, actual.toLiteralString(), value, actual.repr())));
        builder.add(() -> assertEquals(value, actual.asInt(),
                String.format("Variable %s should have integer val %d", variable, value)));
        return this;
    }

    public ContextMatcher val(String variable, String value){
        Lattices.Value expected = vl.parse(value);
        Lattices.Value actual = getValue(variable);
        builder.add(() -> assertEquals(expected.toLiteralString(), actual.toLiteralString(),
                String.format("Variable %s should have val %s, has val %s: %s vs %s", variable,
                        expected.toLiteralString(), actual.toLiteralString(), expected.repr(), actual.repr())));
        return this;
    }

    public ContextMatcher print(){
        builder.add(() -> {
            System.out.println(context);
        });
        return this;
    }

    private Lattices.Value getValue(String variable){
        return context.getVariableValue(variable);
    }

    public ContextMatcher hasInput(String variable){
        builder.add(() -> assertTrue(context.isInputValue(getValue(variable)),
                String.format("The val of %s is an input val", variable)));
        return this;
    }

    public ContextMatcher hasInputSecLevel(String variable, Lattices.Sec<?> expected){
        builder.add(() -> assertEquals(expected, context.getInputSecLevel(getValue(variable)), String.format("Variable %s should be an input variable with level %s", variable, expected)));
        return this;
    }

    public ContextMatcher hasOutput(String variable){
        builder.add(() -> assertTrue(context.output.contains(getValue(variable)),
                String.format("The val of %s is an output val", variable)));
        return this;
    }

    public ContextMatcher hasOutputSecLevel(String variable, Lattices.Sec<?> expected){
        builder.add(() -> assertEquals(expected, context.output.getSec(getValue(variable)), String.format("Variable %s should be an output variable with level %s", variable, expected)));
        return this;
    }

    public ContextMatcher leakage(Consumer<LeakageMatcher> leakageTests){
        leakageTests.accept(new LeakageMatcher());
        return this;
    }

    public class LeakageMatcher {

        private final MinCut.Algo[] algos;

        public LeakageMatcher() {
            this(ContextMatcher.this.algos);
        }

        public LeakageMatcher(MinCut.Algo[] algos) {
            this.algos = algos;
        }

        public LeakageMatcher leaks(Lattices.Sec<?> attackerSec, double leakage){
            for (MinCut.Algo algo : algos) {
                Executable inner = () -> {
                    MinCut.ComputationResult comp = MinCut.compute(context, attackerSec, algo);
                    assertEquals(leakage, comp.maxFlow, () -> {
                        return String.format("The calculated leakage for an attacker of level %s should be %f, leaking %s, using %s",
                                attackerSec, leakage, comp.minCut.stream().map(Lattices.Bit::toString).collect(Collectors.joining(", ")),
                                algo);
                    });
                };
                if (!algo.supportsAlternatives && context.recordsAlternatives()) {
                    builder.add(() -> {
                        boolean prev = context.recordsAlternatives();
                        try {
                            context.setRecordAlternatives(false);
                            inner.execute();
                        } finally {
                            context.setRecordAlternatives(prev);
                        }
                    });
                } else {
                    builder.add(inner);
                }
            }
            return this;
        }

        /**
         * Only uses leakage computation algorithms that support intervals.
         */
        public LeakageMatcher numberOfOutputs(Lattices.Sec<?> attackerSec, int outputs){
            for (MinCut.Algo algo : algos) {
                if (!algo.supportsIntervals()){
                    continue;
                }
                builder.add(() -> {
                    MinCut.ComputationResult comp = MinCut.compute(context, attackerSec, algo);
                    int actualOutputs = (int)Math.round(Math.pow(2, comp.maxFlow));
                    assertEquals(outputs, actualOutputs, () -> {
                        return String.format("The calculated number of outputs to an attacker of level %s should be %d, but is %d, using %s",
                                attackerSec, outputs, actualOutputs, algo);
                    });
                });
            }
            return this;
        }

        public LeakageMatcher leaks(String attackerSec, double leakage){
            return leaks(context.sl.parse(attackerSec), leakage);
        }

        public LeakageMatcher leaksAtLeast(Lattices.Sec sec, double leakage) {
            for (MinCut.Algo algo : algos) {
                builder.add(() -> {
                    MinCut.ComputationResult comp = MinCut.compute(context, sec, algo);
                    assertTrue(comp.maxFlow >= leakage,
                            String.format("The calculated leakage for an attacker of level %s should be at least %f, " +
                                    "leaking %f, using %s", sec, leakage, comp.maxFlow, algo));
                });
            }
            return this;
        }
    }

    public ContextMatcher val(String var, Consumer<ValueMatcher> test){
        test.accept(new ValueMatcher(context.getVariableValue(var)));
        return this;
    }

    public ContextMatcher value(String var, Consumer<Value> test){
        builder.add(() -> test.accept(context.getVariableValue(var)));
        return this;
    }

    public ContextMatcher leaks(String attackerSec, int leakage){
        return leakage(l -> l.leaks(attackerSec, leakage));
    }

    public ContextMatcher leaks(double leakage){
        return leakage(l -> l.leaks(context.sl.bot(), leakage));
    }

    /**
     * Only uses leakage computation algorithms that support intervals.
     */
    public ContextMatcher numberOfOutputs(int outputs){
        return leakage(l -> l.numberOfOutputs(context.sl.bot(), outputs));
    }

    /**
     * Supports "inf"
     */
    public ContextMatcher leaks(String leakage){
        return leakage(l -> l.leaks(context.sl.bot(), leakage.equals("inf") ? Context.INFTY : Double.parseDouble(leakage)));
    }

    public ContextMatcher benchLeakageComputationAlgorithms(int executionTimes) {
        for (MinCut.Algo algo : MinCut.Algo.values()) {
            if (!algo.supportsAlternatives) {
                continue;
            }
            builder.add(() -> {
                List<Integer> times = IntStream.range(0, executionTimes).mapToObj(i -> {
                    long start = System.currentTimeMillis();
                    MinCut.compute(context, context.sl.bot(), algo);
                    return (int) (System.currentTimeMillis() - start);
                }).collect(Collectors.toList());
                double mean = times.stream().mapToInt(i -> i).sum() * 1.0 / times.size();
                double std = Math.sqrt(1.0 / times.size() * times.stream().mapToInt(i -> i).mapToDouble(i -> (mean - i) * (mean - i)).sum());
                System.out.printf("Using %25s took %10.3fms +- %10.3fms\n", algo, mean, std);
            });
        }
        return this;
    }

    public ContextMatcher leaksAtLeast(int leakage){
        return leakage(l -> l.leaksAtLeast(context.sl.bot(), leakage));
    }

    public ContextMatcher bitWidth(int bitWidth){
        builder.add(() -> assertEquals(bitWidth, context.maxBitWidth, "Check of the used maximum bit width"));
        return this;
    }

    /**
     *
     * @param varAndIndex "var[1]"
     * @param val
     * @return
     */
    public ContextMatcher bit(String varAndIndex, String val){
        String var = varAndIndex.split("\\[")[0];
        int i = Integer.parseInt(varAndIndex.split("\\[")[1].split("\\]")[0]);
        builder.add(() -> assertEquals(bs.parse(val), context.getVariableValue(var).get(i).val(), String.format("%s should have the bit val %s", varAndIndex, val)));
        return this;
    }

    /**
     *
     * @param varIndexVals "var[1] = 1; a[3] = 1"
     */
    public ContextMatcher bits(String varIndexVals){
        if (!varIndexVals.contains("=")){
            return this;
        }
        Stream.of(varIndexVals.split(";")).forEach(str -> {
            String[] parts = str.split("=");
            bit(parts[0].trim(), parts[1].trim());
        });
        return this;
    }

    public class ValueMatcher {
        private final Lattices.Value value;

        public ValueMatcher(Lattices.Value value) {
            this.value = value;
        }

        public ValueMatcher bit(int i, Lattices.B val){
            builder.add(() -> assertEquals(val, value.get(i).val(), String.format("The %dth bit of %s should have the bit val %s", i, value, val)));
            return this;
        }

        public ValueMatcher lastBit(Lattices.B val){
            builder.add(() -> assertEquals(val, value.get(value.size()).val(), String.format("The last bit of %s should have the bit val %s", value, val)));
            return this;
        }

        public ValueMatcher endsWithStar(){
            builder.add(() -> assertTrue(value.endsWithStar(), String.format("%s ends with a s bit", value)));
            return this;
        }
    }

    public void run(){
        builder.run();
    }
}
