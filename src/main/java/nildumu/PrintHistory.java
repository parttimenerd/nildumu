package nildumu;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static nildumu.Lattices.*;

/**
 * Contains the classes to work with repeated print statements and their reduction
 */
public class PrintHistory {

    static class HistoryEntry {
        final Optional<HistoryEntry> prev;
        final Map<Variable, HistoryPerGlobalEntry> map;

        HistoryEntry(Optional<HistoryEntry> prev, Map<Variable, HistoryPerGlobalEntry> map) {
            this.prev = prev;
            this.map = map;
        }

        static HistoryEntry create(MethodInvocationHandler.BitGraph graph, Optional<HistoryEntry> prev){
            return create(graph.methodReturnValue.globals, prev, val -> val.stream()
                    .flatMap(b -> graph.calcReachableParamBits(b).stream()).collect(Collectors.toSet()));
        }


        static HistoryEntry create(Map<Variable, Lattices.AppendOnlyValue> map, Optional<HistoryEntry> prev, Function<Lattices.Value, Set<Lattices.Bit>> reachabilityCalculator){
            return new HistoryEntry(prev, map.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                    e -> new HistoryPerGlobalEntry(prev.map(p -> p.map.get(e.getKey())), e.getKey(), e.getValue(), reachabilityCalculator))));
        }
        static HistoryEntry create(Map<Variable, Lattices.AppendOnlyValue> map, Optional<HistoryEntry> prev, HistoryEntry base){
            return new HistoryEntry(prev, map.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                    e -> new HistoryPerGlobalEntry(prev.map(p -> p.map.get(e.getKey())), e.getKey(), e.getValue().deepClone(),
                            base.map.get(e.getKey()).reachableBitNum,
                            base.map.get(e.getKey()).reachableBitsForDiff, base.map.get(e.getKey()).reachableBits))));
        }

    }

    static class HistoryPerGlobalEntry {

        final Optional<HistoryPerGlobalEntry> prev;
        final Variable name;
        final Lattices.AppendOnlyValue value;
        final Lattices.AppendOnlyValue difference;


        final long reachableBitNum;
        final long reachableBitNumForDiff;
        final Set<Lattices.Bit> reachableBitsForDiff;
        final Set<Lattices.Bit> reachableBits;

        HistoryPerGlobalEntry(Optional<HistoryPerGlobalEntry> prev, Variable name, AppendOnlyValue value, long reachableBitNum, Set<Bit> reachableBitsForDiff, Set<Bit> reachableBits) {
            this.prev = prev;
            this.name = name;
            this.value = value;
            this.reachableBits = reachableBits;
            this.difference = prev.map(h -> value.difference(h.value)).orElse(value);
            this.reachableBitNum = reachableBitNum;
            this.reachableBitNumForDiff = reachableBitsForDiff.size();
            this.reachableBitsForDiff = reachableBitsForDiff;
        }

        HistoryPerGlobalEntry(Optional<HistoryPerGlobalEntry> prev, Variable name, Lattices.AppendOnlyValue value, Function<Lattices.Value, Set<Lattices.Bit>> reachabilityCalculator) {
            this.prev = prev;
            this.name = name;
            this.value = value;
            this.difference = prev.map(h -> value.difference(h.value)).orElse(value);
            this.reachableBits = reachabilityCalculator.apply(value);
            this.reachableBitNum = reachableBits.size();
            this.reachableBitsForDiff = reachabilityCalculator.apply(difference);
            this.reachableBitNumForDiff = reachableBitsForDiff.size();
        }

        ReduceResult<Lattices.AppendOnlyValue> reduceAppendOnly(BiConsumer<Bit, Integer> weighter){
            HistoryPerGlobalEntry currentHist = this;
            System.out.println("Reduce");
            ReduceResult<Lattices.AppendOnlyValue> current = new ReduceResult<>(currentHist.value.clone(), false, true);
            if (!prev.isPresent()){ // its the first round
                return current;
            }
            if (value.sizeWithoutEs() == 0) {
                return current;
            }
            HistoryPerGlobalEntry previousHist = prev.get();
            // if the previous and the current are the same length, nothing changed too
            /*if (previousHist.value.sizeWithoutEs() == currentHist.value.sizeWithoutEs()) {
                return current;
            }*/
            // if it got longer and added new dependencies, then we don't have anything to do either
            if (currentHist.reachableBitNum != previousHist.reachableBitNum){
                return current;
            }
            // if it got longer and added parameter dependencies compared to the last version,
            // then we can ignore it too
            if (currentHist.reachableBitNumForDiff > previousHist.reachableBitNumForDiff){
                return current;
            }
            // same for the size
            if (currentHist.difference.sizeWithoutEs() > previousHist.difference.sizeWithoutEs() && previousHist.difference.sizeWithoutEs() > 0){
                return current;
            }
            // and if the bit values changed to higher levels
            if (vl.mapBits(previousHist.difference, currentHist.difference,
                    (a, b) -> !bs.greaterEqualsThan(a.val(), b.val())).stream().anyMatch(Boolean::booleanValue)
                    && previousHist.difference.sizeWithoutEs() > 0){
                return current;
            }
            System.out.println("Did not change");
            // Idea: end this recursion by using an "s" bit that depends to all bits that the difference depends on
            Lattices.Bit s = bl.create(B.S, ds.create(currentHist.reachableBitsForDiff));
            // if the previous value did already ends with the star bits that we want to add
            // return the previous value
            System.out.println(previousHist.difference);
            if (previousHist.difference.get(previousHist.difference.size()).valueGreaterEquals(s) ||
                previousHist.value.stream().anyMatch(b -> b.valueGreaterEquals(s))){
                return new ReduceResult<>(previousHist.value.cloneWithoutEs(), true, false);
            }
            // last case: it got longer exactly the same as before and did not add any new dependencies
            // => merging

            // if the current variable is also related to input, then we know, that `s` may leak infinitely many bytes
            // HACK
            if (name.name.contains("input")) {
                s.deps().stream().forEach(b -> weighter.accept(b, Context.INFTY));
            }

            // TODO: improve
            // the resulting output bits all only depend on the "s" bit
            return new ReduceResult<>(currentHist.value.append(new Value(s)), true, false);
        }
    }

    static class ReduceResult<T> {
        final T value;
        final boolean addedAStarBit;
        final boolean somethingChanged;

        ReduceResult(T value, boolean addedAStarBit, boolean somethingChanged) {
            this.value = value;
            this.addedAStarBit = addedAStarBit;
            this.somethingChanged = somethingChanged;
        }

        ReduceResult(T value){
            this(value, false, true);
        }

        static ReduceResult<Map<Variable, AppendOnlyValue>> create(Map<Variable, ReduceResult<AppendOnlyValue>> val){
            return new ReduceResult<>((val.entrySet().stream().collect(Collectors.toMap(
                    e -> e.getKey(),
                    e -> e.getValue().value
            ))), val.values().stream().map(v -> v.addedAStarBit).reduce((f, s) -> f && s).orElse(false),
            val.values().stream().map(v -> v.somethingChanged).reduce((f, s) -> f && s).orElse(false));
        }

        boolean finished(){
            return !somethingChanged || addedAStarBit;
        }
    }
}
