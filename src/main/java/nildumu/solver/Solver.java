package nildumu.solver;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public abstract class Solver<V, E> {

    /**
     * Wrapps a variable and its modifier, like "require BIT"
     * @param <V>
     * @param <E>
     */
    static class Variable<V, E> {
        public final V instance;
        public final E mod;

        public Variable(V instance, E mod) {
            this.instance = instance;
            this.mod = mod;
        }

        @Override
        public int hashCode() {
            return Objects.hash(instance, mod);
        }

        @Override
        public boolean equals(Object obj) {
            return obj.getClass() == Variable.class && ((Variable<V, E>)obj).instance.equals(instance)
                    && ((Variable<V, E>)obj).mod.equals(instance);
        }

        static <V, E> Variable<V, E> var(V v, E e){
            return new Variable<>(v, e);
        }
    }


    static class Result<V, E> {

        public final List<Variable<V, E>> trueVariables;
        public final List<Variable<V, E>> falseVariables;
        public final double weight;

        public Result(List<Variable<V, E>> trueVariables, List<Variable<V, E>> falseVariables, double weight) {
            this.trueVariables = trueVariables;
            this.falseVariables = falseVariables;
            this.weight = weight;
        }
    }

    final boolean maximize;

    protected Solver(boolean maximize) {
        this.maximize = maximize;
    }

    public abstract void addOrImplication(Variable<V, E> a, Variable<V, E>... oredVariables);

    public abstract void addAndImplication(Variable<V, E> a, Variable<V, E>... andedVariables);

    public abstract void addWeight(Variable<V, E> var, double weight);

    public abstract void addInfiniteWeight(Variable<V, E> var);

    public abstract Optional<Result<V, E>> solve();

    public static <V, E> Solver<V, E> getDefaultSolver(boolean maximize){
        return new RC2Solver<>(maximize);
    }
}
