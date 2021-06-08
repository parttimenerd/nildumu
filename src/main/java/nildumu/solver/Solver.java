package nildumu.solver;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.List;
import java.util.Optional;

/** Abstract solver interface */
public abstract class Solver<V> {


    static class Result<V> {

        public final List<V> trueVariables;
        public final List<V> falseVariables;
        public final double weight;

        public Result(List<V> trueVariables, List<V> falseVariables, double weight) {
            this.trueVariables = trueVariables;
            this.falseVariables = falseVariables;
            this.weight = weight;
        }
    }

    final boolean maximize;

    protected Solver(boolean maximize) {
        this.maximize = maximize;
    }

    public abstract void addOrImplication(V a, V... oredVariables);

    public abstract void addAndImplication(V a, V... andedVariables);

    public abstract void addSingleClause(V a);

    public abstract void addWeight(V var, double weight);

    public abstract void addInfiniteWeight(V var);

    public abstract Optional<Result<V>> solve();

    public abstract void writeInHumanReadableFormat(OutputStreamWriter writer) throws IOException;

    public void printInHumanReadableFormat(){
        try {
            writeInHumanReadableFormat(new OutputStreamWriter(System.out));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
