package nildumu;

import nildumu.solver.PMSATSolverImpl;
import nildumu.solver.SolverBasedLeakageAlgorithm;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static nildumu.Context.INFTY;

public abstract class LeakageAlgorithm {

    public static Algo usedAlgo = Algo.OPENWBO_GLUCOSE;
    protected final SourcesAndSinks sourcesAndSinks;
    protected final Function<Lattices.Bit, Double> weights;

    protected LeakageAlgorithm(SourcesAndSinks sourcesAndSinks, Function<Lattices.Bit, Double> weights) {
        this.sourcesAndSinks = sourcesAndSinks;
        this.weights = weights;
    }

    public abstract ComputationResult compute();

    /** Factory */
    public enum Algo {
        GRAPHT_PP("JGraphT Preflow-Push", "JGT", 0, MinCut.GraphTPP::new),
        OPENWBO_GLUCOSE("Open-WBO GL PMSAT", "OWG", "Open-WBO/bin/open-wbo-g", ""),
        OPENWBO_MERGESAT("Open-WBO MS PMSAT", "OWM", "Open-WBO/bin/open-wbo-ms", ""),
        UWRMAXSAT("UWrMaxSat PMSAT", "UWr", "UWrMaxSat-1.1w/bin/uwrmaxsat", "-m");

        public static final int SUPPORTS_INTERVALS    = 0b0001;
        public static final int SUPPORTS_ALTERNATIVES = 0b0010;
        public static final int SUPPORTS_OUTPUT       = 0b0100;

        public final String description;
        public final String shortName;
        public final int capabilities;
        public final BiFunction<SourcesAndSinks, Function<Lattices.Bit, Double>, LeakageAlgorithm> creator;

        Algo(String description, String shortName, int capabilities, BiFunction<SourcesAndSinks, Function<Lattices.Bit, Double>, LeakageAlgorithm> creator) {
            this.description = description;
            this.shortName = shortName;
            this.capabilities = capabilities;
            this.creator = creator;
        }

        /** Helper for PMSAT based algorithms */
        Algo(String description, String shortName, String binaryPath, String options) {
            this(description, shortName, SUPPORTS_INTERVALS | SUPPORTS_ALTERNATIVES | SUPPORTS_OUTPUT,
                    (ss, weights) -> new SolverBasedLeakageAlgorithm(ss, weights,
                            () -> new PMSATSolverImpl<>(binaryPath, options, false)));
        }

        @Override
        public String toString() {
            return description;
        }

        public boolean capability(int capability) {
            return (capabilities & capability) != 0;
        }

        public <T> T use(Supplier<T> func) {
            Algo prev = usedAlgo;
            usedAlgo = this;
            T t;
            try {
                t = func.get();
            } finally {
                usedAlgo = prev;
            }
            return t;
        }

        public void use(Runnable func) {
            use(() -> {
                func.run();
                return null;
            });
        }

        public static Algo from(String s) {
            try {
                return valueOf(s.toUpperCase());
            } catch (IllegalArgumentException ex){
                throw new IllegalArgumentException(String.format("%s is not a valid algorithm, use one of %s",
                        s, Arrays.stream(values()).map(LeakageAlgorithm.Algo::name).collect(Collectors.joining(", "))));
            }
        }

        public boolean hasRequiredCapabilities(Context context) {
            return (!context.hasAppendOnlyVariables() || capability(SUPPORTS_ALTERNATIVES)) &&
                    (!context.inIntervalMode() || capability(SUPPORTS_INTERVALS)) &&
                    (!context.recordsAlternatives() || capability(SUPPORTS_ALTERNATIVES));
        }

        public ComputationResult compute(SourcesAndSinks sourcesAndSinks, Function<Lattices.Bit, Double> weights){
            con = sourcesAndSinks.context;
            if (!hasRequiredCapabilities(sourcesAndSinks.context)) {
                throw new NildumuError("Algorithm does not have required capabilities");
            }
            return creator.apply(sourcesAndSinks, weights).compute();
        }

        public ComputationResult compute(Context context, Lattices.Sec<?> sec){
            con = context;
            if (sec == context.sl.top()){
                return new ComputationResult(Collections.emptySet(), 0);
            }
            return compute(context.sourcesAndSinks(sec), context::weight);
        }

        public Map<Lattices.Sec<?>, ComputationResult> compute(Context context){
            return context.sl.elements().stream()
                    .collect(Collectors.toMap(s -> s, s -> s == context.sl.top() ?
                            new ComputationResult(Collections.emptySet(), 0) :
                            compute(context, s)));
        }

        public static List<Algo> supported(Context context) {
            return Arrays.stream(values()).filter(a -> a.hasRequiredCapabilities(context)).collect(Collectors.toList());
        }
    }

    private static Context con;

    public static class ComputationResult {
        public final Set<Lattices.Bit> minCut;
        public final double maxFlow;

        public ComputationResult(Set<Lattices.Bit> minCut, double maxFlow) {
            this.minCut = minCut;
            if (maxFlow > INFTY){
                this.maxFlow = INFTY;
            } else {
                this.maxFlow = maxFlow;
            }
            if (minCut.size() > maxFlow) {
                System.err.println("#min cut > max flow");
            }
        }
    }

    public static class SourcesAndSinks {

        final double sourceWeight;
        public final Set<Lattices.Bit> sources;
        final double sinkWeight;
        public final Set<Lattices.Bit> sinks;
        public final Context context;

        public SourcesAndSinks(double sourceWeight, Set<Lattices.Bit> sources, double sinkWeight, Set<Lattices.Bit> sinks, Context context){
            this.sourceWeight = sourceWeight;
            this.sources = sources;
            this.sinkWeight = sinkWeight;
            this.sinks = sinks;
            this.context = context;
        }
    }
}
