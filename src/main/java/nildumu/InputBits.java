package nildumu;

import nildumu.util.DefaultMap;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static nildumu.Lattices.*;
import static nildumu.util.Util.enumerate;

public class InputBits {

    /** "Location" of an input integer literal, there might be multiple per method */
    public static class InputIntegerPath {
        public final Context.CallPath callPath;
        public final Parser.ExpressionNode node;
        private final int hash;

        public InputIntegerPath(Context.CallPath callPath, Parser.ExpressionNode node) {
            this.callPath = callPath;
            this.node = node;
            this.hash = Objects.hash(callPath, node);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof InputIntegerPath)) return false;
            InputIntegerPath that = (InputIntegerPath) o;
            return hash == that.hash && Objects.equals(callPath, that.callPath) && Objects.equals(node, that.node);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public String toString() {
            return callPath + "·" + node.location;
        }
    }

    /**
     * Bit with its "location" and number in the input value, only bits that are at least unknown
     * and have no dependencies can be input bits
     */
    public static class InputBit {
        public final InputIntegerPath location;
        public final int number;
        public final Bit bit;

        public InputBit(InputIntegerPath location, int number, Bit bit) {
            assert !bit.hasDependencies() && bit.isAtLeastUnknown();
            this.location = location;
            this.number = number;
            this.bit = bit;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof InputBit)) return false;
            InputBit inputBit = (InputBit) o;
            return number == inputBit.number && Objects.equals(location, inputBit.location);
        }

        @Override
        public int hashCode() {
            return Objects.hash(location, number);
        }

        public Bit getBit() {
            return bit;
        }

        @Override
        public String toString() {
            return location + "->" + number;
        }
    }

    private final DefaultMap<Sec<?>, Map<InputBit, Bit>> bitsPerSec = new DefaultMap<>((m, s) -> new HashMap<>());
    private final Set<InputBit> bits = new HashSet<>();
    private final Context context;

    /**
     * set the weight of an input bit to infinity if the if the {@link InputIntegerPath} + number is already present,
     * only with another bit
     */
    public static final boolean WEIGH_ON_DUPLICATION = true;

    public InputBits(Context context) {
        this.context = context;
    }


    public Set<Bit> get(Sec<?> sec){
        return new HashSet<>(bitsPerSec.get(sec).values());
    }

    private void put(Sec<?> sec, InputBit inputBit, boolean weighOnDuplication){
        if (weighOnDuplication) {
            Map<InputBit, Bit> bitForSec = bitsPerSec.get(sec);
            if (bitForSec.containsKey(inputBit)) {
                Bit oldBit = bitForSec.get(inputBit);
                if (!oldBit.equals(inputBit.bit)) {
                    context.weight(oldBit, Context.INFTY);
                    context.weight(inputBit.bit, Context.INFTY);
                }
            }
        }
        bitsPerSec.get(sec).put(inputBit, inputBit.bit);
        bits.add(inputBit);
    }

    /** infinite weight on duplication */
    public void put(Sec<?> sec, InputIntegerPath location, Value value){
        put(sec, location, value, WEIGH_ON_DUPLICATION);
    }

    public void put(Sec<?> sec, InputIntegerPath location, Value value, boolean weighOnDuplication){
        enumerate(value.iterator(), (i, b) -> {
            if (b.isAtLeastUnknown()) {
                put(sec, new InputBit(location, i + 1, b), weighOnDuplication);
            }
        });
    }

    /** infinite weight on duplication */
    public void putAll(InputBits other){
        other.bitsPerSec.forEach((sec, map) -> putAll(sec, map.keySet()));
    }

    /** infinite weight on duplication */
    private void putAll(Sec<?> sec, Set<InputBit> bits){
        bits.forEach(b -> put(sec, b, WEIGH_ON_DUPLICATION));
    }

    public Set<Bit> getBits(){
        return bits.stream().map(InputBit::getBit).collect(Collectors.toSet());
    }

    public InputBits map(Function<Bit, Bit> transformer){
        InputBits inputBits = new InputBits(context);
        bitsPerSec.forEach((k, v) -> inputBits.putAll(k, v.keySet().stream()
                .map(b -> new InputBit(b.location, b.number,
                        context.weight(transformer.apply(b.bit), context.weight(b.bit))))
                .collect(Collectors.toSet())));
        return inputBits;
    }

    @Override
    public String toString() {
        return String.format("InputBits(%s)", bitsPerSec.entrySet());
    }
}
