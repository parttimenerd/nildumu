package nildumu;

import nildumu.mih.BitGraph;
import nildumu.util.Lazy;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static nildumu.Lattices.*;
import static nildumu.util.Lazy.l;

/**
 * Contains the classes to work with repeated print statements and their reduction
 */
@SuppressWarnings("ALL")
public class PrintHistory {

    /** State of the global variables at a specific point in time (after a specific number of executions) */
    public static class HistoryEntry {
        public final Optional<HistoryEntry> prev;
        public final Map<Variable, HistoryPerGlobalEntry> map;

        HistoryEntry(Optional<HistoryEntry> prev, Map<Variable, HistoryPerGlobalEntry> map) {
            this.prev = prev;
            this.map = map;
        }

        public static HistoryEntry create(BitGraph graph, Optional<HistoryEntry> prev) {
            return create(graph.methodReturnValue.globals, prev, val -> val.stream()
                    .flatMap(b -> graph.calcReachableInputAndParameterBits(b).stream()).collect(Collectors.toSet()));
        }


        static HistoryEntry create(Map<Variable, Lattices.AppendOnlyValue> map, Optional<HistoryEntry> prev, Function<Lattices.Value, Set<Lattices.Bit>> reachabilityCalculator){
            return new HistoryEntry(prev, map.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                    e -> new HistoryPerGlobalEntry(prev.map(p -> p.map.get(e.getKey())), e.getKey(), e.getValue(), reachabilityCalculator))));
        }
        static HistoryEntry create(Map<Variable, Lattices.AppendOnlyValue> map, Optional<HistoryEntry> prev, HistoryEntry base){
            return new HistoryEntry(prev, map.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                    e -> new HistoryPerGlobalEntry(prev.map(p -> p.map.get(e.getKey())), e.getKey(), e.getValue().deepClone(),
                            base.map.get(e.getKey()).reachableBitsForDiff.get(), base.map.get(e.getKey()).reachableBits.get()))));
        }

    }

    /** State of a single global variable */
    public static class HistoryPerGlobalEntry {

        final Optional<HistoryPerGlobalEntry> prev;
        final Variable name;
        final Lattices.AppendOnlyValue value;
        final Lattices.AppendOnlyValue difference;

        final Lazy<Set<Bit>> reachableBitsForDiff;
        final Lazy<Set<Lattices.Bit>> reachableBits;

        HistoryPerGlobalEntry(Optional<HistoryPerGlobalEntry> prev, Variable name, AppendOnlyValue value, Set<Bit> reachableBitsForDiff, Set<Bit> reachableBits) {
            this.prev = prev;
            this.name = name;
            this.value = value;
            this.reachableBits = l(reachableBits);
            this.difference = prev.map(h -> value.difference(h.value)).orElse(value);
            this.reachableBitsForDiff = l(reachableBitsForDiff);
        }

        HistoryPerGlobalEntry(Optional<HistoryPerGlobalEntry> prev, Variable name, Lattices.AppendOnlyValue value, Function<Lattices.Value, Set<Lattices.Bit>> reachabilityCalculator) {
            this.prev = prev;
            this.name = name;
            this.value = value;
            this.difference = prev.map(h -> value.difference(h.value)).orElse(value);
            this.reachableBits = l(reachabilityCalculator.apply(value));
            this.reachableBitsForDiff = l(reachabilityCalculator.apply(difference));
        }

        public ReduceResult<Lattices.AppendOnlyValue> reduceAppendOnly(BiConsumer<Bit, Double> weighter) {
            HistoryPerGlobalEntry currentHist = this;
            ReduceResult<Lattices.AppendOnlyValue> current = new ReduceResult<>(currentHist.value.clone(), false, true);

            if (!prev.isPresent() || prev.get().value.sizeWithoutEs() == 0 || // its the first round
                    value.sizeWithoutEs() == 0 // or the value is just empty
            ) {
                return current;
            }

            HistoryPerGlobalEntry previousHist = prev.get();
            // if the previous and the current are the same length, nothing changed too
            if (previousHist.value.sizeWithoutEs() == currentHist.value.sizeWithoutEs()) {
                return current;
            }
            // if more bits are added than in the last round, the value changed
            if (currentHist.difference.sizeWithoutEs() > previousHist.difference.sizeWithoutEs() && previousHist.difference.sizeWithoutEs() > 0){
                return current;
            }
            // if the values did not change...
            if (previousHist.value.valueEquals(currentHist.value)){
                return current;
            }
            // and if the bit values changed to higher levels
            if (vl.mapBits(previousHist.difference, currentHist.difference,
                    (a, b) -> !bs.greaterEqualsThan(a.val(), b.val())).stream().anyMatch(Boolean::booleanValue)
                    && previousHist.difference.sizeWithoutEs() > 0){
                return current;
            }
            // if it got longer and added new dependencies, then we don't have anything to do either
            if (currentHist.reachableBits.get().size() != previousHist.reachableBits.get().size()){
                return current;
            }
            // if it got longer and added parameter dependencies compared to the last version,
            // then we can ignore it too
            if (currentHist.reachableBitsForDiff.get().size() > previousHist.reachableBitsForDiff.get().size()){
                return current;
            }
            // Idea: end this recursion by using an "s" bit that depends to all bits that the difference depends on
            Lattices.Bit s = bl.create(B.S, ds.create(currentHist.reachableBitsForDiff.get()));
            // if the previous value did already ends with the star bits that we want to add
            // return the previous value
            if ((previousHist.difference.size() > 0 && previousHist.difference.get(previousHist.difference.size()).valueGreaterEquals(s)) ||
                previousHist.value.stream().anyMatch(b -> b.valueGreaterEquals(s))){
                return new ReduceResult<>(previousHist.value.cloneWithoutEs(), true, false);
            }
            // last case: it got longer exactly the same as before and did not add any new dependencies
            // => merging

            // if the current variable is also related to input, then we know, that `s` may leak infinitely many bytes
            if (name.isAppendableInput()) {
                s.deps().forEach(b -> weighter.accept(b, (double) Context.INFTY));
            }

            // TODO: improve
            // the resulting output bits all only depend on the "s" bit
            return new ReduceResult<>(currentHist.value.append(new Value(s)), true, false);
        }
    }

    public static class ReduceResult<T> {
        public final T value;
        public final boolean addedAStarBit;
        public final boolean somethingChanged;

        public ReduceResult(T value, boolean addedAStarBit, boolean somethingChanged) {
            this.value = value;
            this.addedAStarBit = addedAStarBit;
            this.somethingChanged = somethingChanged;
        }

        public ReduceResult(T value) {
            this(value, false, true);
        }

        public static ReduceResult<Map<Variable, AppendOnlyValue>> create(Map<Variable, ReduceResult<AppendOnlyValue>> val) {
            return new ReduceResult<>((val.entrySet().stream().collect(Collectors.toMap(
                    Map.Entry::getKey,
                    e -> e.getValue().value
            ))), val.values().stream().map(v -> v.addedAStarBit).reduce((f, s) -> f && s).orElse(false),
                    val.values().stream().map(v -> v.somethingChanged).reduce((f, s) -> f && s).orElse(false));
        }

        boolean finished(){
            return !somethingChanged || addedAStarBit;
        }
    }
}
