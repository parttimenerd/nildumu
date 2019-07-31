package nildumu.solver;

import nildumu.util.Util;
import swp.util.Pair;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.*;

/**
 * Allows to create partial max sat formulas and solve them, creates output in the WDIMACS format
 */
public abstract class PMSAT<V, E> extends Solver<V, E> {

    /**
     * Variables to int with weights (default is 0)
     */
    static class Variables<T> {
        private final Map<T, Integer> valToVar;
        private final List<T> varToVal;
        private final Map<T, Double> weights;
        private final Set<T> infiniteWeightVars;

        private double weightSum;

        public Variables() {
            varToVal = new ArrayList<>();
            weights = new HashMap<>();
            valToVar = new HashMap<>();
            weightSum = 0;
            infiniteWeightVars = new HashSet<>();
            varToVal.add(null);
        }

        public int val(T var){
            if (!valToVar.containsKey(var)){
                valToVar.put(var, valToVar.size() + 1);
                varToVal.add(var);
            }
            return valToVar.get(var);
        }

        public T var(int val){
            return varToVal.get(val);
        }

        public void weight(T var, double weight){
            assert weight > 0;
            if (weights.containsKey(var)){
                throw new UnsupportedOperationException(String.format("Setting weight of %s twice", var));
            }
            weights.put(var, weight);
            weightSum += weight;
        }

        public double weight(T var){
            return weights.getOrDefault(var, 0.0);
        }

        public double getWeightSum(){
            return weightSum;
        }

        public int size() {
            return varToVal.size();
        }

        public void addInfinetlyWeighted(T var){
            infiniteWeightVars.add(var);
        }

        public double getTopWeight(double multiplier){
            return (infiniteWeightVars.size() + 1) * (weightSum + 1) * multiplier + 1;
        }
    }

    /**
     * Round the weights up, for solvers that do only support integer weights
     */
    private final boolean roundUp;

    private final List<int[]> clauses;

    private final Variables<Variable<V, E>> variables;

    /**
     * Creates a new instance
     *
     * @param maximize maximize the weight?
     * @param roundUp round up the weight (for solvers that do only allow integer weights, by default
     *                multiplies the weights with ⌈1 / (log2(2^32) - log2((2^32) - 1))⌉ = 24)
     */
    public PMSAT(boolean maximize, boolean roundUp) {
        super(maximize);
        this.roundUp = roundUp;
        clauses = new ArrayList<>();
        variables = new Variables<>();
    }

    @Override
    public void addOrImplication(Variable<V, E> a, Variable<V, E>... oredVariables) {
        int[] clause = new int[oredVariables.length + 1];
        clause[0] = -variables.val(a);
        for (int i = 0; i < oredVariables.length; i++) {
            clause[i] = variables.val(oredVariables[i]);
        }
        clauses.add(clause);
    }

    @Override
    public void addAndImplication(Variable<V, E> a, Variable<V, E>... andedVariables) {
        for (Variable<V, E> andedVariable : andedVariables) {
            addOrImplication(a, andedVariables);
        }
    }

    @Override
    public void addWeight(Variable<V, E> var, double weight) {
        variables.weight(var, weight);
    }

    @Override
    public void addInfiniteWeight(Variable<V, E> var) {
        variables.addInfinetlyWeighted(var);
    }

    long getInfiteWeight(double multiplier){
        return (long)Math.ceil(variables.getWeightSum() * multiplier) + 1;
    }

    void writeInWDIMACSFormat(OutputStreamWriter writer) throws IOException {
        double multiplier = roundUp ? calculateWeightMultiplier() : 1;
        writer.write(String.format("p wcnf %d %d %d\n",
                variables.size(), clauses.size() + variables.weights.size(), formatWeight(variables.getTopWeight(multiplier))));
        for (int[] clause : clauses) {
            writer.write((long) Math.ceil(variables.getTopWeight(multiplier)) + "");
            for (int i : clause) {
                writer.write(" " + i);
            }
            writer.write(" 0\n");
        }
        for (Map.Entry<Variable<V,E>, Double> weightPair : variables.weights.entrySet()) {
            writer.write(String.format("%s %d\n", formatWeight(weightPair.getValue() * multiplier), variables.val(weightPair.getKey())));
        }
        for (Variable<V, E> var : variables.infiniteWeightVars) {
            writer.write(String.format("%d %d", getInfiteWeight(multiplier),
                    maximize ? variables.val(var) : -variables.val(var)));
        }
    }

    private String formatWeight(double weight){
        if (roundUp){
            return Long.toString((long)Math.ceil(weight));
        }
        return Double.toString(weight);
    }


    public Optional<Result<V, E>> parse(InputStreamReader reader){
        BufferedReader buf = new BufferedReader(reader);
        List<Variable<V, E>> trueVariables = new ArrayList<>();
        List<Variable<V, E>> falseVariables = new ArrayList<>();
        String line;
        try {
            while ((line = buf.readLine()) != null) {
                if (line.startsWith("s UNKNOWN")){
                    return Optional.empty();
                }
                if (line.startsWith("v")){
                    for (String str : line.substring(2).split(" ")){
                        int val = Integer.parseInt(str);
                        if (val > 0 && maximize){
                            trueVariables.add(variables.var(val));
                        } else {
                            falseVariables.add(variables.var(val));
                        }
                    }
                }
            }
        } catch (IOException ex){
            return Optional.empty();
        }
        double multiplier = roundUp ? calculateWeightMultiplier() : 1;
        double weight = trueVariables.stream().mapToDouble(variables::weight).map(i -> i / multiplier).sum();
        return Optional.of(new Result<>(trueVariables, falseVariables,
                                        weight > variables.getWeightSum() * multiplier ? Double.POSITIVE_INFINITY : weight));
    }

    @Override
    public Optional<Result<V, E>> solve() {
        return parse(solveAndRead());
    }

    public abstract InputStreamReader solveAndRead();

    /**
     * Calculate the number with which the weights are multiplied before being passed to the solver.
     * Only used if the solver rounds up, by default uses ⌈1 / (log2(2^32) - log2((2^32) - 1))⌉ = 24
     */
    public double calculateWeightMultiplier(){
        return Math.ceil(1 / (Util.log2(Math.pow(2, 32)) - Util.log2(Math.pow(2, 32) - 1)));
    }
}
