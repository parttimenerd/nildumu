package nildumu.util;

import swp.util.Pair;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Serializable;
import java.time.temporal.ValueRange;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static nildumu.Lattices.Bit;
import static nildumu.Lattices.vl;

public class Util {

    public static <T> List<Pair<T, T>> permutatePair(T x, T y){
        return Arrays.asList(new Pair<>(x, y), new Pair<>(y, x));
    }

    @SafeVarargs
    public static <T> Set<T> set(T... ts){
        return new HashSet<>(Arrays.asList(ts));
    }

    public static <S, T> Pair<S, T> p(S s, T t){
        return new Pair<>(s,t);
    }

    static Stream<Bit> stream(Bit x, Bit y) {
        return Stream.of(x, y);
    }

    static Stream<Bit> stream(List<Pair<Bit, Bit>> bits) {
        return bits.stream().flatMap(p -> Stream.of(p.first, p.second));
    }

    public static <T> ArrayList<T> asArrayList(T... args) {
        return new ArrayList<>(Arrays.asList(args));
    }

    public static <T> ArrayList<T> concatAsArrayList(Collection<? extends T>... args) {
        ArrayList<T> ret = new ArrayList<>();
        for (Collection<? extends T> arg : args) {
            ret.addAll(arg);
        }
        return ret;
    }

    public static IntStream stream(ValueRange range) {
        return IntStream.range((int) range.getMinimum(), (int) range.getMaximum());
    }

    /**
     * Allows to modify values in lambdas
     */
    public static class Box<T> implements Serializable {

        public T val;

        public Box(T val) {
            this.val = val;
        }

        @Override
        public String toString() {
            return val.toString();
        }
    }

    public static String toBinaryString(int num){
        List<Boolean> vals = new ArrayList<>();
        int numAbs = num;
        if (num < 0){
            numAbs = Math.abs(num) - 1;
        }
        boolean one = num >= 0;
        while (numAbs > 0){
            if (numAbs % 2 == 0){
                vals.add(!one);
            } else {
                vals.add(one);
            }
            numAbs = numAbs / 2;
        }
        if (vals.isEmpty()){
            vals.add(0, !one);
        }
        vals.add(!one);
        while (vals.size() < vl.bitWidth) {
            vals.add(!one);
        }
        Collections.reverse(vals);
        return vals.stream().map(b -> b ? "1" : "0").collect(Collectors.joining(""));
    }

    public static String iter(String str, int number){
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < number; i++) {
            builder.append(str);
        }
        return builder.toString();
    }

    public static double log2(double val){
        return Math.log(val) / Math.log(2);
    }

    public static void disableSysOut() {
        System.setOut(new PrintStream(new OutputStream() {
            @Override
            public void write(int i) throws IOException {

            }
        }));
    }

    public static <S, T, R> List<R> zip(List<S> a, List<T> b, BiFunction<S, T, R> consumer) {
        assert a.size() == b.size();
        List<R> ret = new ArrayList<>();
        for (int i = 0; i < a.size(); i++) {
            ret.add(consumer.apply(a.get(i), b.get(i)));
        }
        return Collections.unmodifiableList(ret);
    }

    public static <S, T> boolean zipAnyMatch(List<S> a, List<T> b, BiPredicate<S, T> predicate) {
        assert a.size() == b.size();
        for (int i = 0; i < a.size(); i++) {
            if (predicate.test(a.get(i), b.get(i))) {
                return true;
            }
        }
        return false;
    }

    public static <S, R> List<R> enumerate(List<S> a, BiFunction<Integer, S, R> consumer) {
        return IntStream.range(0, a.size()).mapToObj(i -> consumer.apply(i, a.get(i))).collect(Collectors.toList());
    }
}
