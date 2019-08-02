package nildumu.solver;

import nildumu.Context;
import nildumu.Lattices;
import nildumu.MinCut;
import nildumu.intervals.Interval;
import nildumu.util.Util;
import swp.Config;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

import static nildumu.Lattices.bl;

/**
 * Solver based leakage calculation, uses a minimizing solver
 */
public class LeakageAlgorithm extends MinCut.Algorithm {

    private static enum Type {
        IS_INTERVAL("inter"),
        INTERVAL("i"),
        DEPENDENCIES("r"),
        BIT("c"),
        EITHER("d");

        private final String abbr;

        Type(String abbr){
            this.abbr = abbr;
        }

        @Override
        public String toString() {
            return abbr;
        }
    }

    public static class Variable {
        final Lattices.Bit bit;
        final Type type;

        private Variable(Lattices.Bit bit, Type type) {
            this.bit = bit;
            this.type = type;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Variable variable = (Variable) o;
            return bit.equals(variable.bit) &&
                    type == variable.type;
        }

        @Override
        public int hashCode() {
            return Objects.hash(bit, type);
        }

        @Override
        public String toString() {
            return bit + "[" + type + "]";
        }
    }

    private final Supplier<Solver<Variable>> solverSupplier;

    public LeakageAlgorithm(Context.SourcesAndSinks sourcesAndSinks,
                            Function<Lattices.Bit, Double> weights,
                            Supplier<Solver<Variable>> solverSupplier) {
        super(sourcesAndSinks, weights);
        this.solverSupplier = solverSupplier;
    }

    @Override
    public MinCut.ComputationResult compute() {
        Solver<Variable> solver = solverSupplier.get();
        assert !solver.maximize;
        Set<Lattices.Bit> alreadyVisited = new HashSet<>();
        Set<Lattices.Value> values = new HashSet<>();

        Map<Interval, Variable> interToVar = new HashMap<>();
        Map<Variable, Interval> varToInter = new HashMap<>();
        Map<Interval, Double> interToWeight = new HashMap<>();

        for (Lattices.Bit bit : sourcesAndSinks.sources){
            bl.walkBits(bit, b -> {
                List<Variable> vars = new ArrayList<>(3);
                //if (weights.apply(b) != Context.INFTY || bit.hasDependencies()){
                    vars.add(v(b, Type.BIT));
                    if (b.isConstant()){
                        return;
                    }
                    if (b.hasDependencies()) {
                        vars.add(v(b, Type.DEPENDENCIES));
                    } else if (!sourcesAndSinks.sinks.contains(b)){
                        return;
                    }
                //}
                if (b.value() != null && b.value().hasInterval()){
                    Interval interval = b.value().getInterval();
                    if (!interToVar.containsKey(interval)){
                        Lattices.Bit x = bl.forceCreateXBit();
                        x.value(new Lattices.Value().description(interval.toString())).valueIndex(1);
                        Variable var = v(x, Type.IS_INTERVAL);
                        interToVar.put(interval, var);
                        varToInter.put(var, interval);
                        interToWeight.put(interval, b.value().entropy());
                    }
                    vars.add(interToVar.get(interval));
                }
                solver.addOrImplication(v(b, Type.EITHER), vars.toArray(new Variable[0]));
                for (Lattices.Bit dep : b.deps()) {
                    solver.addOrImplication(v(b, Type.DEPENDENCIES), v(dep, Type.EITHER));
                }
                if (b.value() != null){
                    values.add(b.value());
                }
            }, b -> false, alreadyVisited);
        }
        for (Lattices.Bit bit : sourcesAndSinks.sources){
            solver.addSingleClause(v(bit, Type.EITHER));
        }
        alreadyVisited.addAll(sourcesAndSinks.sinks);
        alreadyVisited.addAll(sourcesAndSinks.sources);
        for (Lattices.Bit bit : alreadyVisited){
            double weight = weights.apply(bit);
            if (weight == Context.INFTY){
                solver.addInfiniteWeight(v(bit, Type.BIT));
            } else {
                solver.addWeight(v(bit, Type.BIT), weight);
            }
        }
        for (Map.Entry<Interval, Variable> entry : interToVar.entrySet()) {
            solver.addWeight(entry.getValue(), interToWeight.get(entry.getKey()));
        }

        Solver.Result<Variable> result = solver.solve().get();

        Set<Lattices.Bit> consideredBits = new HashSet<>();
        Set<Interval> consideredIntervals = new HashSet<>();
        double weight = 0;
        for (Variable trueVariable : result.trueVariables) {
            switch (trueVariable.type){
                case IS_INTERVAL:
                    Interval interval = varToInter.get(trueVariable);
                    weight += interToWeight.get(interval);
                    consideredIntervals.add(interval);
                    break;
                case BIT:
                    weight += weights.apply(trueVariable.bit);
                    consideredBits.add(trueVariable.bit);
            }
        }
        return new MinCut.ComputationResult(consideredBits, weight);
    }

    private static Variable v(Lattices.Bit bit, Type type){
        return new Variable(bit, type);
    }
}
