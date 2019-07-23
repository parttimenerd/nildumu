package nildumu.intervals;

import nildumu.Lattices;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static nildumu.Lattices.B.*;
import static nildumu.Lattices.bs;
import static nildumu.Lattices.vl;

public class Intervals {

    /**
     * Interval with inclusive boundaries
     */
    public static class Interval {

        public final int start;
        public final int end;

        public Interval(int start, int end) {
            this.start = start;
            this.end = end;
        }

        @Override
        public String toString() {
            return String.format("[%d, %d]", start, end);
        }

        public int bitWidth(){
            return (int)Math.ceil(Math.log(end - start) / Math.log(2));
        }

        public int size(){
            return end - start + 1;
        }

        public boolean contains(int i){
            return i >= start && i <= end;
        }
    }

    public static class ConstrainedInterval extends Interval {

        public final Interval interval;

        public final Map<Integer, Lattices.B> constraints;

        public ConstrainedInterval(Interval interval, Map<Integer, Lattices.B> constraints) {
            super(interval.start, interval.end);
            this.interval = interval;
            this.constraints = constraints;
        }

        public ConstrainedInterval(Interval interval){
            this(interval, new HashMap<>());
        }

        public void constrain(int index, Lattices.B b){
            constraints.put(index, b);
        }

        public Lattices.B constrain(int index){
            return constraints.getOrDefault(index, U);
        }

        @Override
        public String toString() {
            return String.format("[%d, %d, {%s}]", start, end, IntStream.range(0, vl.bitWidth)
                    .mapToObj(i -> constrain(vl.bitWidth - i - 1).toString()).collect(Collectors.joining("")));
        }

        public Lattices.B signConstraint(){
            return constrain(vl.bitWidth - 1);
        }

        public static ConstrainedInterval parse(String str){
            String[] parts = str.split("\\|");
            Map<Integer, Lattices.B> constraints = new HashMap<>();
            if (parts.length == 3) {
                String reversed = new StringBuilder(parts[2]).reverse().toString();
                IntStream.range(0, vl.bitWidth - 1).forEach(i -> {
                    if (i < reversed.length()){
                        constraints.put(i, bs.parse(reversed.substring(i, i + 1)));
                    }
                });
            }
            return new ConstrainedInterval(new Interval(Integer.parseInt(parts[0]), Integer.parseInt(parts[1])), constraints);
        }

        public static class Constraints {
            private final List<Lattices.B> constraints;
            public final int size;

            Constraints(Map<Integer, Lattices.B> constraints) {
                this(IntStream.range(0, vl.bitWidth).mapToObj(i -> constraints.getOrDefault(i, U))
                        .collect(Collectors.toList()));
            }

            Constraints(List<Lattices.B> constraints){
                this(constraints, (int)constraints.stream().filter(b -> b.isConstant()).count());
            }

            Constraints(List<Lattices.B> constraints, int size){
                this.constraints = constraints;
                this.size = size;
            }

            public int firstFixedBit(){
                assert size > 0;
                return IntStream.range(0, constraints.size()).filter(i -> constraints.get(i) != U).findFirst().getAsInt();
            }

            public Constraints removeFirst(){
                if (size == 0){
                    return this;
                }
                List<Lattices.B> cons = new ArrayList<>(constraints);
                cons.set(firstFixedBit(), U);
                return new Constraints(cons, size - 1);
            }

            public int lastFixedBit(){
                assert size > 0;
                return constraints.size() - IntStream.range(0, constraints.size()).filter(i -> constraints.get(constraints.size() - i - 1) != U).findFirst().getAsInt() - 1;
            }

            public Constraints removeLast(){
                if (size == 0){
                    return this;
                }
                List<Lattices.B> cons = new ArrayList<>(constraints);
                cons.set(lastFixedBit(), U);
                return new Constraints(cons, size - 1);
            }

            public Constraints rightShift(int k){
                return new Constraints(constraints.subList(k, constraints.size()));
            }

            public Constraints negate(){
                return new Constraints(constraints.stream().map(c -> c != U ? (c == ONE ? ZERO : ONE) : U).collect(Collectors.toList()), size);
            }

            public Lattices.B get(int i){
                return constraints.get(i);
            }

            @Override
            public String toString() {
                return IntStream.range(0, constraints.size()).mapToObj(i -> constraints.get(constraints.size() - i - 1).toString()).collect(Collectors.joining());
            }

            public boolean match(int val){
                for (int i = 0; i < constraints.size(); i++){
                    Lattices.B b = constraints.get(i);
                    if (b != U && bitVal(val, i) != b.value.get()){
                        return false;
                    }
                }
                return true;
            }

            public int lowInstantiation(){
                int ret = 0;
                int mul = 1;
                for (int i = 0; i < constraints.size(); i++){
                    Lattices.B b = constraints.get(i);
                    if (b == ONE){
                        ret += mul;
                    }
                    mul = mul * 2;
                }
                return ret;
            }

            public int highInstantiation(){
                int ret = 0;
                int mul = 1;
                for (int i = 0; i < constraints.size(); i++){
                    Lattices.B b = constraints.get(i);
                    if (b != ZERO){
                        ret += mul;
                    }
                    mul = mul * 2;
                }
                return ret;
            }

            public boolean canNeverMatch(int start, int end){
                return lowInstantiation() > end || highInstantiation() < start;
            }

            /**
             * Fill all unknown bits that have an index lower than lastFixedBit with zeros
             *
             * @return new constraint without inner Us
             */
            public Constraints low(){
                List<Lattices.B> cons = new ArrayList<>();
                for (int i = 0; i < lastFixedBit(); i++) {
                    cons.add(get(i) == U ? ZERO : get(i));
                }
                return new Constraints(cons);
            }
        }


        public int size(){
            return (int) IntStream.range(start, end + 1).filter(this::contains).count();
        }

        public static class PatternCounter {

            public int countPattern(ConstrainedInterval interval){
                assert interval.end >= 0 && interval.start >= 0;
                Constraints constraints = new Constraints(interval.constraints);
                if (constraints.canNeverMatch(interval.start, interval.end)){
                    return 0;
                }
                return countPattern(interval.start, interval.end, constraints);
            }

            public int countPattern(int a, int b, Constraints constraints){
                return countPatternRaw(a, b, constraints);
            }

            int countPatternRaw(int a, int b, Constraints constraints){
                return (int)IntStream.range(a, b + 1).filter(constraints::match).count();
            }

        }

        public static class PatternCounterA extends PatternCounter {

            @Override
            public int countPattern(int start, int end, Constraints constraints){
                assert constraints.size <= 1;
                if (constraints.size == 0){
                    return end - start + 1;
                }
                int lastIndex = constraints.lastFixedBit();
                int lastVal = constraints.get(lastIndex).value.get();
                int ret = countBasePattern(start, end, lastIndex, lastVal);
                if (constraints.size == 1){
                    return ret;
                }
                return ret - countPattern(start >> (lastIndex + 1), end >> (lastIndex + 1), constraints.rightShift(lastIndex + 1).negate());
            }

            int countBasePattern(int a, int b, int i, int v){
                int ar = (a & -((1 << i)));
                int br = (b | ((1 << i) - 1));
                int xi_a = ar - a;
                int xi_b = b - br;
                int as = a >> i;
                int bs = b >> i;
                double diff = Math.max(((br * 1.0) - ar) / 2, 0);
                if (as % 2 == v && bs % 2 == v){
                    if (b - a < (2 << i)){
                        return 1;
                    }
                    return (((int)diff) + (as != bs? 1 : 0)) - xi_a - xi_b;
                }
                if (as % 2 != bs % 2){
                    if (as % 2 == v){
                        return (int)(Math.ceil(diff)) + xi_a;
                    }
                    return ((int)Math.ceil(diff)) + xi_b;
                }
                return (int)Math.floor(diff) * (i + 1);
            }
        }

        public class PatternCounterM extends PatternCounter {

            @Override
            public int countPattern(int a, int b, Constraints constraints) {
                int base = countPatternRaw(a, b, constraints.low());
                // add all the omitted
                for (int i = 0; i < constraints.lastFixedBit(); i++) {
                    if (constraints.get(i) == U) {
                        base += countPatternRaw(a, b, )
                    }
                }
            }
        }

        static int bitVal(int val, int index){
            return (val & (1 << index)) >> index;
        }
    }
}
