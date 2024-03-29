package nildumu;

import nildumu.intervals.Interval;
import nildumu.mih.MethodInvocationHandler;
import nildumu.typing.Type;
import nildumu.util.Util;
import swp.util.Pair;

import java.util.*;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static nildumu.Context.v;
import static nildumu.Lattices.*;
import static nildumu.Lattices.B.*;
import static nildumu.Parser.LexerTerminal.INVERT;
import static nildumu.Parser.LexerTerminal.PLUS;
import static nildumu.util.Util.*;

public interface Operator {

    class WrongArgumentNumber extends NildumuError {
        WrongArgumentNumber(String op, int actualNumber, int expectedNumber){
            super(String.format("%s, expected %d, but got %d argument(s)", op, expectedNumber, actualNumber));
        }
    }

    static Bit wrapBit(Context c, Bit source) {
        Bit wrap = bl.create(source.val(), ds.create(source));
        c.repl(wrap, ((con, b, a) -> {
            Bit choose = con.choose(a, b);
            return new Mods(con.notChosen(a, b), choose).add(c.repl(source).apply(con, source, choose));
        }));
        return wrap;
    }

    static Interval wrapInterval(Context c, Interval source) {
        Interval wrap = new Interval(source.start, source.end);
        wrap.bits.addAll(source.bits);
        return wrap;
    }

    class ParameterAccess implements Operator {

        private final Variable variable;

        public ParameterAccess(Variable variable) {
            this.variable = variable;
        }

        @Override
        public Value compute(Context c, List<Value> arguments) {
            checkArguments(arguments);
            return c.getVariableValue(variable);
        }

        @Override
        public String toString(List<Value> arguments) {
            checkArguments(arguments);
            return variable.toString();
        }

        void checkArguments(List<Value> arguments) {
            if (arguments.size() != 0) {
                throw new WrongArgumentNumber(variable.toString(), arguments.size(), 0);
            }
        }
    }

    class LiteralOperator implements Operator {
        private final Value literal;

        public LiteralOperator(Value literal) {
            this.literal = literal;
        }

        @Override
        public Value compute(Context c, List<Value> arguments) {
            return c.replace(literal);
        }

        @Override
        public String toString(List<Value> arguments) {
            return literal.toString();
        }

        void checkArguments(List<Value> arguments){
            if (arguments.size() != 0){
                throw new WrongArgumentNumber(literal.toString(), arguments.size(), 0);
            }
        }
    }

    abstract class UnaryOperator implements Operator {

        public final String symbol;

        public UnaryOperator(String symbol) {
            this.symbol = symbol;
        }

        @Override
        public Value compute(Context c, List<Value> arguments) {
            checkArguments(arguments);
            return compute(c, arguments.get(0));
        }

        abstract Value compute(Context c, Value argument);

        @Override
        public String toString(List<Value> arguments) {
            checkArguments(arguments);
            return String.format("%s%s", symbol, arguments.get(0).toString());
        }

        void checkArguments(List<Value> arguments){
            if (arguments.size() != 1){
                throw new WrongArgumentNumber(symbol, arguments.size(), 1);
            }
        }

        @Override
        public Interval computeForIntervals(Context c, Value res, List<Interval> intervals){
            return computeForIntervals(c, res, intervals.get(0));
        }

        public Interval computeForIntervals(Context c, Value res, Interval interval){
            return null;
        }
    }

    abstract class BinaryOperator implements Operator {

        public final String symbol;

        Parser.MJNode currentNode;

        public BinaryOperator(String symbol) {
            this.symbol = symbol;
        }

        @Override
        public Value compute(Context c, Parser.MJNode node, List<Value> arguments) {
            checkArguments(arguments);
            currentNode = node;
            if (arguments.get(0) instanceof AppendOnlyValue || arguments.get(1) instanceof AppendOnlyValue){
                return compute(c, arguments.get(0).asAppendOnly(), arguments.get(1).asAppendOnly()).asAppendOnly();
            }
            return compute(c, arguments.get(0), arguments.get(1));
        }

        abstract Value compute(Context c, Value first, Value second);

        @Override
        public String toString(List<Value> arguments) {
            checkArguments(arguments);
            return String.format("%s%s%s", arguments.get(0), symbol, arguments.get(1));
        }

        void checkArguments(List<Value> arguments){
            if (arguments.size() != 2){
                throw new WrongArgumentNumber(symbol, arguments.size(), 2);
            }
        }

        @Override
        public Interval computeForIntervals(Context c, Value res, List<Interval> intervals){
            return computeForIntervals(c, res, intervals.get(0), intervals.get(1));
        }

        public Interval computeForIntervals(Context c, Value res, Interval first, Interval second){
            return null;
        }
    }

    abstract class BitWiseBinaryOperator extends BinaryOperator {

        public BitWiseBinaryOperator(String symbol) {
            super(symbol);
        }

        @Override
        Value compute(Context c, Value first, Value second) {
            return new Value(first.lattice().mapBits(first, second, (a, b) -> compute(c, a, b)));
        }

        abstract Bit compute(Context c, Bit first, Bit second);
    }

    /**
     * Important: the {@link StructuredModsCreator#apply(Context, Bit, Bit)}
     * adds the default bit modification for the own bit to all computed modifications, except for the unused
     * case.
     */
    interface StructuredModsCreator extends Context.ModsCreator {

        default Mods apply(Context c, Bit r, Bit a) {
            if (r.isConstant()){
                return Mods.empty();
            }
            Mods mods = null;
            switch (a.val()) {
                case ONE:
                    mods = assumeOne(c, r, a);
                    break;
                case ZERO:
                    mods = assumeZero(c, r, a);
                    break;
                case U:
                    mods = assumeUnknown(c, r, a);
                    break;
                case X:
                    return assumeUnused(c, r, a);
            }
            return mods.add(defaultOwnBitMod(c, r, a));
        }

        /**
         * Assume that <code>a</code> is one
         */
        Mods assumeOne(Context c, Bit r, Bit a);

        /**
         * Assume that <code>a</code> is zero
         */
        Mods assumeZero(Context c, Bit r, Bit a);

        /**
         * Assume that <code>a</code> is u
         */
        default Mods assumeUnknown(Context c, Bit r, Bit a) {
            return Mods.empty();
        }

        default Mods defaultOwnBitMod(Context c, Bit r, Bit a) {
            if (r.isConstant()) {
                return Mods.empty();
            }
            return new Mods(c.notChosen(a, r), c.choose(a, r));
        }

        default Mods assumeUnused(Context c, Bit r, Bit a) {
            return Mods.empty();
        }
    }

    /**
     * A bit wise operator that uses a preset computation structure. Computation steps:
     *operatorPerNode
     * <ol>
     * <li>computation of the bit value</li>
     * <li>computation of the dependencies → automatic computation of the security level and the control dependencies</li>
     * <li>computation of the bit modifications</li>
     * </ol>
     *
     * <b>Only usable for operators that don't add control dependencies</b>
     */
    abstract class BitWiseBinaryOperatorStructured extends BitWiseBinaryOperator {

        public BitWiseBinaryOperatorStructured(String symbol) {
            super(symbol);
        }

        @Override
        Bit compute(Context c, Bit x, Bit y) {
            Lattices.B bitValue = computeBitValue(x, y);
            if (bitValue.isConstant()) {
                return bl.create(bitValue);
            }
            DependencySet dataDeps = computeDataDependencies(x, y, bitValue);
            Bit r = bl.create(bitValue, dataDeps);
            c.repl(r, computeModificator(x, y, r, dataDeps));
            return r;
        }

        abstract B computeBitValue(Bit x, Bit y);

        abstract DependencySet computeDataDependencies(Bit x, Bit y, B computedBitValue);

        abstract Context.ModsCreator computeModificator(Bit x, Bit y, Bit r, DependencySet dataDeps);
    }

    abstract class BitWiseOperator implements Operator {

        private final String symbol;

        Parser.MJNode currentNode;

        public BitWiseOperator(String symbol) {
            this.symbol = symbol;
        }

        @Override
        public Value compute(Context c, Parser.MJNode node, List<Value> values) {
            currentNode = node;
            int maxWidth = values.stream().mapToInt(Value::size).max().getAsInt();
            return IntStream.range(1, maxWidth + 1).mapToObj(i -> computeBit(c, values.stream().map(v -> v.get(i)).collect(Collectors.toList()))).collect(Value.collector());
        }

        abstract Bit computeBit(Context c, List<Bit> bits);

        @Override
        public String toString(List<Value> arguments) {
            return arguments.stream().map(Value::toString).collect(Collectors.joining(symbol));
        }
    }

    /**
     * A bit wise operator that uses a preset computation structure. Computation steps:
     *
     * <ol>
     * <li>computation of the bit value</li>
     * <li>computation of the dependencies → automatic computation of the security level and the control dependencies</li>
     * <li>computation of the bit modifications</li>
     * </ol>
     * <b>Only usable for operators that don't add control dependencies</b>
     */
    abstract class BitWiseOperatorStructured extends BitWiseOperator {
        public BitWiseOperatorStructured(String symbol) {
            super(symbol);
        }

        @Override
        Bit computeBit(Context c, List<Bit> bits) {
            Lattices.B bitValue = computeBitValue(bits);
            if (bitValue.isConstant()) {
                return bl.create(bitValue);
            }
            DependencySet dataDeps = computeDataDependencies(bits, bitValue);
            Bit r = bl.create(bitValue, dataDeps);
            c.repl(r, computeModsCreator(r, dataDeps));
            return r;
        }

        abstract B computeBitValue(List<Bit> bits);

        abstract DependencySet computeDataDependencies(List<Bit> bits, B computedBitValue);

        abstract Context.ModsCreator computeModsCreator(Bit r, DependencySet dataDeps);
    }

    abstract class BinaryOperatorStructured extends BinaryOperator {

        public BinaryOperatorStructured(String symbol) {
            super(symbol);
        }

        @Override
        public Value compute(Context c, Value x, Value y) {
            List<B> bitValues = computeBitValues(x, y);
            List<DependencySet> dataDeps = computeDataDependencies(x, y, bitValues);
            List<Bit> bits = new ArrayList<>();
            for (int i = 0; i < Math.max(x.size(), y.size()); i++){
                if (bitValues.get(i).isConstant()){
                    bits.add(bl.create(bitValues.get(i)));
                } else {
                    Bit r = bl.create(bitValues.get(i), dataDeps.get(i));
                    bits.add(r);
                    c.repl(r, computeModsCreator(i + 1, r, x, y, bitValues, dataDeps.get(i)));
                }
            }
            return new Value(bits);
        }

        public List<B> computeBitValues(Value x, Value y) {
            List<B> bs = new ArrayList<>();
            for (int i = 1; i <= Math.max(x.size(), y.size()); i++){
                bs.add(computeBitValue(i, x, y));
            }
            return bs;
        }

        abstract B computeBitValue(int i, Value x, Value y);

        List<DependencySet> computeDataDependencies(Value x, Value y, List<B> computedBitValues) {
            return IntStream.range(1, Math.max(x.size(), y.size()) + 1).mapToObj(i -> computeDataDependencies(i, x, y, computedBitValues)).collect(Collectors.toList());
        }

        abstract DependencySet computeDataDependencies(int i, Value x, Value y, List<B> computedBitValues);

        abstract Context.ModsCreator computeModsCreator(int i, Bit r, Value x, Value y, List<B> bitValues, DependencySet dataDeps);
    }

    /**
     * the bitwise or operator (can be used for booleans (ints of which only the first bit matters) too)
     */
    BitWiseBinaryOperatorStructured OR = new BitWiseBinaryOperatorStructured("|") {

        @Override
        public B computeBitValue(Bit x, Bit y) {
            if (x.val() == ONE || y.val() == ONE) {
                return ONE;
            }
            if (x.val() == ZERO && y.val() == ZERO) {
                return ZERO;
            }
            return U;
        }

        @Override
        public DependencySet computeDataDependencies(Bit x, Bit y, Lattices.B computedBitValue) {
            return Util.permutatePair(x, y).stream()
                    .filter(p -> p.first.val() == U && p.second.val() != ONE)
                    .flatMap(Pair::firstStream).collect(DependencySet.collector());
        }

        @Override
        Context.ModsCreator computeModificator(Bit x, Bit y, Bit r, DependencySet dataDeps) {
            return new StructuredModsCreator() {
                @Override
                public Mods assumeOne(Context c, Bit r, Bit a) {
                    if (v(y) == ZERO) {
                        return c.repl(x, a);
                    }
                    if (v(x) == ZERO){
                        return c.repl(y, a);
                    }
                    return c.repl(x, a).intersection(c.repl(y, a));
                }

                @Override
                public Mods assumeZero(Context c, Bit r, Bit a) {
                    return c.repl(x, a).add(c.repl(y, a));
                }
            };
        }
    };

    BitWiseBinaryOperatorStructured AND = new BitWiseBinaryOperatorStructured("&") {

        @Override
        public B computeBitValue(Bit x, Bit y) {
            if (x.val() == ONE && y.val() == ONE) {
                return ONE;
            }
            if (x.val() == ZERO || y.val() == ZERO) {
                return ZERO;
            }
            return U;
        }

        @Override
        public DependencySet computeDataDependencies(Bit x, Bit y, Lattices.B computedBitValue) {
            return Util.permutatePair(x, y).stream()
                    .filter(p -> p.first.val() == U && p.second.val() != ZERO)
                    .flatMap(Pair::firstStream).collect(DependencySet.collector());
        }

        @Override
        Context.ModsCreator computeModificator(Bit x, Bit y, Bit r, DependencySet dataDeps) {
            return new StructuredModsCreator() {
                @Override
                public Mods assumeZero(Context c, Bit r, Bit a) {
                    if (v(y) == ONE) {
                        return c.repl(x, a);
                    }
                    if (v(x) == ONE){
                        return c.repl(y, a);
                    }
                    return c.repl(x, a).union(c.repl(y, a));
                }

                @Override
                public Mods assumeOne(Context c, Bit r, Bit a) {
                    return c.repl(x, a).add(c.repl(y, a));
                }
            };
        }
    };

    abstract class ShortCircuitOperator implements Operator {

        private final Parser.LexerTerminal op;

        public ShortCircuitOperator(Parser.LexerTerminal op) {
            this.op = op;
        }

        @Override
        public String toString(List<Value> arguments) {
            return arguments.stream().map(Value::toString).collect(Collectors.joining());
        }

        @Override
        public Value compute(Context c, List<Value> arguments) {
            if (arguments.size() == 1 || arguments.get(1).isBot()) {
                return arguments.get(0);
            }
            return compute(c, arguments.get(0), arguments.get(1));
        }

        public abstract Value compute(Context c, Value x, Value y);

        @Override
        public boolean allowsUnevaluatedArguments() {
            return true;
        }
    }

    Operator LOGICAL_AND = new ShortCircuitOperator(Parser.LexerTerminal.AND) {

        @Override
        public Value compute(Context c, Value x, Value y) {
            return AND.compute(c, x, y);
        }
    };

    Operator LOGICAL_OR = new ShortCircuitOperator(Parser.LexerTerminal.OR) {
        @Override
        public Value compute(Context c, Value x, Value y) {
            // assumes that x is either unknown or false
            return OR.compute(c, x, y);
        }
    };

    BitWiseBinaryOperator XOR = new BitWiseBinaryOperatorStructured("^") {

        @Override
        public B computeBitValue(Bit x, Bit y) {
            if (x.val() != y.val() && x.isConstant() && y.isConstant()) {
                return ONE;
            }
            if (x.val() == y.val() && y.isConstant()) {
                return ZERO;
            }
            return U;
        }

        @Override
        public DependencySet computeDataDependencies(Bit x, Bit y, Lattices.B computedBitValue) {
            return Util.permutatePair(x, y).stream()
                    .filter(p -> p.first.val() == U)
                    .flatMap(Pair::firstStream).collect(DependencySet.collector());
        }

        @Override
        Context.ModsCreator computeModificator(Bit x, Bit y, Bit r, DependencySet dataDeps) {
            return new StructuredModsCreator() {
                @Override
                public Mods assumeOne(Context c, Bit r, Bit a) {
                    return assumeOnePart(c, x, y).add(assumeOnePart(c, y, x));
                }

                @Override
                public Mods assumeZero(Context c, Bit r, Bit a) {
                    return assumeZeroPart(c, x, y).add(assumeZeroPart(c, y, x));
                }

                Mods assumeOnePart(Context c, Bit alpha, Bit beta){
                    if (beta.isConstant()){
                        return c.repl(alpha, bl.create(v(beta).neg()));
                    }
                    return Mods.empty();
                }

                Mods assumeZeroPart(Context c, Bit alpha, Bit beta){
                    if (beta.isConstant()){
                        return c.repl(alpha, beta);
                    }
                    return Mods.empty();
                }
            };
        }
    };

    UnaryOperator NOT = new UnaryOperator("~") {
        @Override
        public Value compute(Context c, Value x) {
            return x.stream().map(b -> {
                B val = v(b).neg();
                DependencySet dataDeps = b.isConstant() ? ds.empty() : ds.create(b);
                Bit r = bl.create(val, dataDeps);
                c.repl(r, new StructuredModsCreator() {
                    @Override
                    public Mods assumeOne(Context c, Bit r, Bit a) {
                        return c.repl(b, bl.create(ZERO));
                    }

                    @Override
                    public Mods assumeZero(Context c, Bit r, Bit a) {
                        return c.repl(b, bl.create(ONE));
                    }

                    @Override
                    public Mods assumeUnused(Context c, Bit r, Bit a) {
                        return c.repl(b, a);
                    }
                });
                return r;
            }).collect(Value.collector());
        }
    };

    UnaryOperator UNPACK = new UnaryOperator("*") {
        @Override
        Value compute(Context c, Value argument) {
            return argument;
        }
    };

    BinaryOperator EQUALS = new BinaryOperatorStructured("==") {
        @Override
        public Lattices.B computeBitValue(int i, Value x, Value y) {
            if (i > 1) {
                return ZERO;
            }
            if (x.lattice().mapBits(x, y, (a, b) -> a.val().equals(b.val()) && a.isConstant()).stream().allMatch(Boolean::booleanValue)) {
                return ONE;
            }
            if (x.lattice().mapBits(x, y, (a, b) -> !a.val().equals(b.val()) && a.isConstant() && b.isConstant()).stream().anyMatch(Boolean::booleanValue)) {
                return ZERO;
            }
            return U;
        }

        @Override
        public DependencySet computeDataDependencies(int i, Value x, Value y, List<Lattices.B> computedBitValues) {
            if (i > 1 || computedBitValues.get(0).isConstant()) {
                return DependencySetLattice.get().empty();
            }
            return Stream.concat(x.stream().filter(b -> b.val() == U),
                    y.stream().filter(b -> b.val() == U)).collect(DependencySet.collector());
        }

        @Override
        Context.ModsCreator computeModsCreator(int i, Bit r, Value x, Value y, List<B> bitValues, DependencySet dataDeps) {
            if (i > 1){
                return (c, b, a) -> Mods.empty();
            }
            return new StructuredModsCreator() {
                @Override
                public Mods assumeOne(Context c, Bit r, Bit a) {
                    if (i != 1){
                        return Mods.empty();
                    }
                    Mods mods = Mods.empty();
                    vl.mapBits(x, y, (xi, yi) -> c.repl(c.notChosen(xi, yi), c.choose(xi, yi))).forEach(mods::add);
                    return mods;
                }

                @Override
                public Mods assumeZero(Context c, Bit r, Bit a) {
                    List<Pair<Bit, Bit>> pairs = zip(x.bits, y.bits, (f, g) -> {
                        if (f.isUnknown() && g.isConstant()) {
                            return p(f, g);
                        }
                        if (g.isUnknown() && f.isConstant()) {
                            return p(g, f);
                        }
                        return null;
                    }).stream().filter(Objects::nonNull).collect(Collectors.toList());
                    if (pairs.size() == 1) {
                        return c.repl(pairs.get(0).first, bl.create(pairs.get(0).second.val().neg()));
                    }
                    return Mods.empty();
                }
            };
        }
    };

    BinaryOperator UNEQUALS = new BinaryOperatorStructured("!=") {

        @Override
        public Lattices.B computeBitValue(int i, Value x, Value y) {
            if (i > 1) {
                return ZERO;
            }
            if (x.lattice().mapBits(x, y, (a, b) -> a.val().equals(b.val()) && a.isConstant()).stream().allMatch(Boolean::booleanValue)) {
                return ZERO;
            }
            if (x.lattice().mapBits(x, y, (a, b) -> !a.val().equals(b.val()) && a.isConstant() && b.isConstant()).stream().anyMatch(Boolean::booleanValue)) {
                return ONE;
            }
            return U;
        }

        @Override
        public DependencySet computeDataDependencies(int i, Value x, Value y, List<Lattices.B> computedBitValues) {
            if (i > 1) {
                return ds.bot();
            }
            if (computedBitValues.get(0).isConstant()){
                return ds.empty();
            }
            return Stream.concat(x.stream().filter(b -> b.val() == U),
                    y.stream().filter(b -> b.val() == U)).collect(DependencySet.collector());
        }

        @Override
        Context.ModsCreator computeModsCreator(int i, Bit r, Value x, Value y, List<B> bitValues, DependencySet dataDeps) {
            if (i > 1){
                return (c, b, a) -> Mods.empty();
            }
            return new StructuredModsCreator() {
                @Override
                public Mods assumeZero(Context c, Bit r, Bit a) {
                    if (i != 1){
                        return Mods.empty();
                    }
                    Mods mods = Mods.empty();
                    vl.mapBits(x, y, (xi, yi) -> c.repl(c.notChosen(xi, yi), c.choose(xi, yi))).forEach(mods::add);
                    return mods;
                }

                @Override
                public Mods assumeOne(Context c, Bit r, Bit a) {
                    return Mods.empty();
                }
            };
        }
    };

    BinaryOperatorStructured LESS = new BinaryOperatorStructured("<") {

        Stack<DependencySet> dependentBits = new Stack<>();
        Stack<Pair<Value, Value>> args = new Stack<>();

        @Override
        public B computeBitValue(int i, Value x, Value y) {
            args.add(new Pair<>(x, y));
            if (i > 1) {
                return ZERO;
            }
            if (x.isConstant() && y.isConstant()) {
                return x.singleValue() < y.singleValue() ? ONE : ZERO;
            }
            Lattices.B val = U;
            DependencySet depBits = Stream.concat(x.stream(), y.stream()).filter(Bit::isUnknown).collect(DependencySet.collector());
            Bit a_n = x.signBit();
            Bit b_n = y.signBit();
            B v_x_n = a_n.val();
            B v_y_n = b_n.val();
            Optional<Integer> differingNonConstantIndex = firstNonMatching(x, y, (c, d) -> x.isConstant() && c == d);
            if (v_x_n.isConstant() && v_y_n.isConstant() && v_x_n != v_y_n) { // if signs differ
                depBits = ds.empty();
                if (v_x_n == ONE) { // x is negative
                    val = ONE;
                } else {
                    depBits = ds.empty();
                    val = ZERO;
                }
            } else if (v_x_n == ZERO && v_y_n == ZERO && differingNonConstantIndex.isPresent() &&
                    x.get(differingNonConstantIndex.get()).isConstant() && y.get(differingNonConstantIndex.get()).isConstant()) {
                val = y.get(differingNonConstantIndex.get()).val();
                depBits = ds.empty();
            }
            dependentBits.push(depBits);
            return val;
        }

        Optional<Integer> firstNonMatching(Value x, Value y, BiPredicate<B, B> pred) {
            int j = x.size() - 1;
            while (j >= 1 && pred.test(x.get(j).val(), y.get(j).val())) {
                j--;
            }
            if (j > 1) {
                return Optional.of(j);
            }
            return Optional.empty();
        }

        IntStream indexesWithBitValue(Value value, B b){
            IntStream.Builder ints = IntStream.builder();
            int j = value.size() - 1;
            while (j >= 1 && value.get(j).val() == b) {
                ints.add(j);
                j--;
            }
            return ints.build();
        }

        @Override
        public DependencySet computeDataDependencies(int i, Value x, Value y, List<Lattices.B> computedBitValues) {
            if (i > 1) {
                return ds.bot();
            }
            if (computedBitValues.get(0).isConstant()){
                return ds.empty();
            }
            return dependentBits.pop();
        }

        @Override
        Context.ModsCreator computeModsCreator(int i, Bit r, Value x, Value y, List<B> bitValues, DependencySet dataDeps) {
            if (i > 1){
                return (c, b, a) -> Mods.empty();
            }
            return new StructuredModsCreator() {
                @Override
                public Mods assumeOne(Context c, Bit r, Bit a) {
                    if (i != 1){
                        return Mods.empty();
                    }
                    int bitWidth = Math.max(x.size(), y.size());
                    return assumeOneValue(c, x.withBitCount(bitWidth), y.withBitCount(bitWidth));
                }

                public Mods assumeOneValue(Context c, Value x, Value y) {
                    // we have 9 different cases, depending on the sign bits
                    if (x.isNegative()) {
                        if (y.isNegative()) {
                            // do something
                            // x < y   <=>   -y < -x   <=>   ~y + 1 < ~x + 1
                            // <=>  ~y < ~x  maybe?
                            Mods mods = Mods.empty();
                            y.highBitIndicesWOSign(ONE, -1).forEach(i -> mods.add(c.repl(x.get(i), bl.create(ONE)))); // -1?
                            x.highBitIndicesWOSign(ZERO, 0).forEach(i -> mods.add(c.repl(y.get(i), bl.create(ZERO))));
                            return mods;
                        } else if (y.isPositive()) {
                            return Mods.empty();
                        } else {
                            // if !(x < -|y|) { y is positive }
                            // if !(x.largest < y.largest(sign=1)) { y.assume(sign=0) }
                            if (!(x.largest() < y.smallest(ONE))) {
                                Bit ySign = y.signBit();
                                return assumeOneValue(c, x, y.assume(ZERO)).add(c.repl(ySign, bl.create(ZERO)));
                            }
                            return Mods.empty();
                        }
                    } else if (x.isPositive()) {
                        if (y.isNegative()) {
                            return Mods.empty();
                        } else if (y.isPositive()) {
                            Mods mods = Mods.empty();
                            y.highBitIndicesWOSign(ZERO, -1).forEach(i -> mods.add(c.repl(x.get(i), bl.create(ZERO)))); // -1?
                            x.highBitIndicesWOSign(ONE, 0).forEach(i -> mods.add(c.repl(y.get(i), bl.create(ONE))));
                            return mods;
                        } else {
                            // y.assume(sign=0): x < y
                            Bit ySign = y.signBit();
                            return assumeOneValue(c, x, y.assume(ZERO)).add(c.repl(ySign, bl.create(ZERO)));
                        }
                    } else {
                        if (y.isNegative()) {
                            // x.assume(sign=1): x < y
                            Bit xSign = x.signBit();
                            return assumeOneValue(c, x.assume(ONE), y).add(c.repl(xSign, bl.create(ONE)));
                        } else if (y.isPositive()) {
                            // if !(|x| < |y|) { x is negative }
                            // if !(x.smallest(sign=0) < y.largest) { x.assume(sign=1) }
                            if (!(x.smallest(ZERO) < y.largest())) {
                                Bit xSign = x.signBit();
                                return assumeOneValue(c, x.assume(ONE), y).add(c.repl(xSign, bl.create(ONE)));
                            }
                            return Mods.empty();
                        } else {
                            return Mods.empty();
                        }
                    }
                }

                @Override
                public Mods assumeZero(Context c, Bit r, Bit _a) {
                    // we can assume that a is zero and r is unkndown and therefore ignore it
                    if (i != 1){
                        return Mods.empty();
                    }
                    // x == y or x > y
                    // check if x can be y:
                    if (vl.mapBits(x, y, (a, b) -> a.val() == b.val() || a.isUnknown() || b.isUnknown()).stream().allMatch(Boolean::booleanValue)) {
                        Mods eqMods = Mods.empty();
                        vl.mapBits(x, y, (xi, yi) -> c.repl(c.notChosen(xi, yi), c.choose(xi, yi))).forEach(eqMods::add);
                        return assumeOneValue(c, y, x).intersection(eqMods);
                    }
                    return assumeOneValue(c, y, x);
                }
            };
        }

        @Override
        public Interval computeForIntervals(Context c, Value ret, Interval first, Interval second) {
            Interval interval = new Interval(0, 1);
            if (first.end < second.start){
                interval = new Interval(1, 1);
            }
            if (first.start >= second.end){
                interval = new Interval(0, 0);
            }
            Pair<Value, Value> arg = args.pop();
            c.repl(ret.get(1), new Context.ModsCreator() {
                @Override
                public Mods apply(Context context, Bit bit, Bit assumedValue) { Mods mods = Mods.empty();
                    switch (assumedValue.val()){
                        case ONE:
                            if (arg.second.singleValued()){
                                mods.add(first, new Interval(first.start, Math.min(first.end, arg.second.singleValue() -1)).addBits(c.replace(arg.first).bitSet()));
                                arg.first.bits.forEach(b -> {
                                    mods.add(b, wrapBit(context, b));
                                });
                            }
                    }
                    return mods;
                }
            });
            return interval;
        }
    };

    BitWiseBinaryOperatorStructured PHI = new BitWiseBinaryOperatorStructured("phi") {

        @Override
        public boolean supportsArguments(List<Value> arguments) {
            return PHI_GENERIC.supportsArguments(arguments);
        }

        @Override
        Bit compute(Context c, Bit x, Bit y) {
            if (x.val() == X){
                return wrapBit(c, y);
            } else if (y.val() == X || x == y){
                return wrapBit(c, x);
            }
            Parser.PhiNode phi = (Parser.PhiNode)currentNode;
            if (phi.controlDeps.size() == 1){
                B condVal = c.nodeValue(phi.controlDeps.get(0)).get(1).val();
                if (condVal.isAtLeastUnknown() && c.nodeValue(phi.controlDeps.get(0)).singleValued()){
                    condVal = vl.parse(c.nodeValue(phi.controlDeps.get(0)).singleValue()).get(1).val();
                }
                switch (condVal){
                    case ONE:
                        return wrapBit(c, x);
                    case ZERO:
                        return wrapBit(c, y);
                }
            }
            Lattices.B bitValue = computeBitValue(x, y);
            if (bitValue.isConstant()) {
                return bl.create(bitValue);
            }
            DependencySet dataDeps = computeDataDependencies(x, y, bitValue);
            Bit r = bl.create(bitValue, dataDeps);
            c.repl(r, computeModificator(x, y, r, dataDeps));
            r.addDependencies(computeControlDeps(c, phi, bitValue, null));
            return r;
        }

        @Override
        public Lattices.B computeBitValue(Bit x, Bit y) {
            return bs.sup(x.val(), y.val());
        }

        @Override
        public DependencySet computeDataDependencies(Bit x, Bit y, Lattices.B computedBitValue) {
            return Stream.of(x, y).filter(Bit::isAtLeastUnknown).collect(DependencySet.collector());
        }

        public DependencySet computeControlDeps(Context context, Parser.MJNode node, B computedBitValue, DependencySet computedDataDependencies) {
            assert node instanceof Parser.PhiNode;
            if (bs.greaterEqualsThan(computedBitValue, U)) {
                return ((Parser.PhiNode) node).controlDeps.stream().map(n -> context.nodeValue(n).get(1)).collect(DependencySet.collector());
            }
            return ds.empty();
        }

        @Override
        Context.ModsCreator computeModificator(Bit x, Bit y, Bit r, DependencySet dataDeps) {
            return new StructuredModsCreator() {
                @Override
                public Mods assumeOne(Context c, Bit r, Bit a) {
                    return comp(c, a);
                }

                @Override
                public Mods assumeZero(Context c, Bit r, Bit a) {
                    return comp(c, a);
                }

                public Mods comp(Context c, Bit a){
                    if (dataDeps.size() == 1){
                        return c.repl(dataDeps.getSingleBit(), a);
                    }
                    return Mods.empty();
                }
            };
        }

        @Override
        void checkArguments(List<Value> arguments) {
        }

        @Override
        public boolean allowsUnevaluatedArguments() {
            return true;
        }

        @Override
        public Interval computeForIntervals(Context c, Value res, Interval x, Interval y) {
            if (x.isDefaultInterval()){
                return wrapInterval(c, y);
            } else if (y.isDefaultInterval()|| x == y){
                return wrapInterval(c, x);
            }
            Parser.PhiNode phi = (Parser.PhiNode)currentNode;
            if (phi.controlDeps.size() == 1){
                B condVal = c.nodeValue(phi.controlDeps.get(0)).get(1).val();
                switch (condVal){
                    case ONE:
                        return wrapInterval(c, x);
                    case ZERO:
                        return wrapInterval(c, y);
                }
            }
            return x.merge(y);
        }
    };

    BitWiseOperator PHI_GENERIC = new BitWiseOperatorStructured("phi") {

        @Override
        public boolean supportsArguments(List<Value> arguments) {
            return true; //!(arguments.get(0) instanceof AppendOnlyValue) || arguments.stream().allMatch(a -> a instanceof AppendOnlyValue);
        }

        @Override
        Bit computeBit(Context c, List<Bit> bits) {
            List<Bit> nonBots = bits.stream().filter(b -> b.val() != X).collect(Collectors.toList());
            if (nonBots.size() == 1){
                return wrapBit(c, nonBots.get(0));
            }
            if (bits.size() > 0 && bits.stream().noneMatch(b -> b != bits.get(0))){
                return wrapBit(c, bits.get(0));
            }
            Lattices.B bitValue = computeBitValue(bits);
            if (bitValue.isConstant()) {
                return bl.create(bitValue);
            }
            DependencySet dataDeps = computeDataDependencies(bits, bitValue);
            Bit r = bl.create(bitValue, dataDeps);
            c.repl(r, computeModsCreator(r, dataDeps));
            r.addDependencies(computeControlDeps(c, currentNode, bitValue, dataDeps));
            return r;
        }

        @Override
        public Lattices.B computeBitValue(List<Bit> bits) {
            return bs.sup(bits.stream().map(Bit::val));
        }

        @Override
        public DependencySet computeDataDependencies(List<Bit> bits, Lattices.B computedBitValue) {
            return bits.stream().filter(Bit::isUnknown).collect(DependencySet.collector());
        }

        public DependencySet computeControlDeps(Context context, Parser.MJNode node, B computedBitValue, DependencySet computedDataDependencies) {
            assert node instanceof Parser.PhiNode;
            if (computedBitValue == B.U) {
                return ((Parser.PhiNode) node).controlDeps.stream().map(n -> context.nodeValue(n).get(1)).collect(DependencySet.collector());
            }
            return ds.empty();
        }

        @Override
        Context.ModsCreator computeModsCreator(Bit r, DependencySet dataDeps) {
            return PHI.computeModificator(null, null, r, dataDeps);
        }

        @Override
        public boolean allowsUnevaluatedArguments() {
            return true;
        }
    };

    class PlaceBit extends UnaryOperator {

        final long index;

        public PlaceBit(long index) {
            super(String.format("[%d]·", index));
            this.index = index;
        }

        @Override
        Value compute(Context c, Value argument) {
            return LongStream.range(1, index + 2).mapToObj(i -> i == index ? argument.get(1) : bl.create(ZERO)).collect(Value.collector());
        }
    }

    class SelectBit extends BinaryOperator {

        public SelectBit() {
            super("·[·]");
        }

        @Override
        Value compute(Context c, Value first, Value second) {
            if (!second.isConstant()) {
                throw new NildumuError("Only constant indices supported for bit select operators");
            }
            return new Value(first.get((int)second.asLong()));
        }
    }

    /**
     * deals with unpacking on one level
     */
    static List<Value> unfoldTuple(List<Parser.ExpressionNode> expressions, List<Value> values) {
        List<Value> ret = new ArrayList<>();
        assert expressions.size() == values.size();
        return zip(expressions, values, (e, v) -> {
            if (e instanceof Parser.UnpackOperatorNode) {
                List<Value> vals = v.split();
                assert vals.size() == ((Type.TupleType) ((Parser.UnpackOperatorNode) e).expression.type).elementTypes.size();
                return vals;
            }
            return Collections.singletonList(v);
        }).stream().flatMap(List::stream).collect(Collectors.toList());
    }

    class MethodInvocation implements Operator {

        final Parser.MethodInvocationNode callSite;

        public MethodInvocation(Parser.MethodInvocationNode callSite) {
            this.callSite = callSite;
        }

        @Override
        public Value compute(Context c, List<Value> arguments) {
            arguments = Operator.unfoldTuple(callSite.arguments.arguments, arguments);
            if (callSite.definition.isPredefined()){
                return ((Parser.PredefinedMethodNode)callSite.definition).apply(arguments);
            }
            Map<Variable, AppendOnlyValue> globals = callSite.globalDefs.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> c.getVariableValue(e.getValue().first).asAppendOnly()));
            MethodInvocationHandler.MethodReturnValue ret = c.methodInvocationHandler().analyze(c, callSite, arguments, globals);
            callSite.globalDefs.forEach((v, p) -> {
                if (ret.globals.containsKey(v)) {
                    c.setVariableValue(p.second, ret.globals.get(v), null, true);
                } else {
                    c.setVariableValue(p.second, globals.get(v), null, true);
                }
            });
            c.getNewlyIntroducedInputs().putAll(ret.inputBits);
            return Value.combineOrZero(ret.values);
        }

        @Override
        public String toString(List<Value> arguments) {
            return String.format("%s(%s)", callSite.definition.name, arguments.stream().map(Value::toString).collect(Collectors.joining(",")));
        }
    }

    Operator TUPLE_LITERAL = new Operator() {

        @Override
        public Value compute(Context c, List<Value> arguments) {
            return Value.combine(arguments);
        }

        @Override
        public String toString(List<Value> arguments) {
            if (arguments.size() == 1) {
                return "(" + arguments.get(0) + ",)";
            }
            return "(" + arguments.stream().map(Objects::toString).collect(Collectors.joining(", ")) + ")";
        }

        @Override
        public boolean allowsUnevaluatedArguments() {
            return true;
        }
    };

    Operator ARRAY_LITERAL = new Operator() {

        @Override
        public Value compute(Context c, List<Value> arguments) {
            return Value.combine(arguments);
        }

        @Override
        public String toString(List<Value> arguments) {
            if (arguments.size() == 1) {
                return "{" + arguments.get(0) + ",}";
            }
            return "{" + arguments.stream().map(Objects::toString).collect(Collectors.joining(", ")) + "}";
        }

        @Override
        public boolean allowsUnevaluatedArguments() {
            return true;
        }
    };

    BinaryOperator ADD = new BinaryOperator("+") {
        @Override
        Value compute(Context c, Value first, Value second) {
            Set<Bit> argBits = Stream.concat(first.stream(), second.stream()).collect(Collectors.toSet());
            Util.Box<Bit> carry = new Util.Box<>(bl.create(ZERO));
            return vl.mapBitsToValue(first, second, (a, b) -> {
                Pair<Bit, Bit> add = fullAdder(c, a, b, carry.val);
                carry.val = add.second;
                if (c.USE_REDUCED_ADD_OPERATOR){
                    return bl.create(add.first.val(), ds.create(add.first.calculateReachedBits(argBits)));
                }
                return add.first;
            }, vl.bitWidth);
        }

        Pair<Bit, Bit> fullAdder(Context context, Bit a, Bit b, Bit c) {
            Pair<Bit, Bit> pair = halfAdder(context, a, b);
            Pair<Bit, Bit> pair2 = halfAdder(context, pair.first, c);
            Bit carry = OR.compute(context, pair.second, pair2.second);
            return new Pair<>(pair2.first, carry);
        }

        Pair<Bit, Bit> halfAdder(Context context, Bit first, Bit second) {
            return new Pair<>(XOR.compute(context, first, second), AND.compute(context, first, second));
        }

        @Override
        public Interval computeForIntervals(Context c, Value res, Interval first, Interval second) {
            try {
                return new Interval(Math.addExact(first.start, second.start),
                        Math.addExact(first.end, second.end));
            } catch (ArithmeticException ex){
                return Interval.forBitWidth(vl.bitWidth);
            }
        }
    };

    BinaryOperator LEFT_SHIFT = new BinaryOperator("<<") {
        @Override
        Value compute(Context c, Value first, Value second) {
            if (second.isConstant()){
                int shift = (int)second.asLong();
                if (shift >= c.maxBitWidth){
                    return vl.parse(0);
                }
                if (shift < 0){
                    return RIGHT_SHIFT.compute(c, first, vl.parse(-shift));
                }
                Value ret = IntStream.range(1, c.maxBitWidth + 1).mapToObj(i -> {
                    if (i - shift < 1) {
                        return bl.create(ZERO);
                    }
                    return first.get(i - shift);
                }).collect(Value.collector());
                return ret;
            }
            return createUnknownValue(first, second);
        }
    };

    BinaryOperator RIGHT_SHIFT = new BinaryOperator(">>") {

        @Override
        Value compute(Context c, Value first, Value second) {
            if (second.isConstant()){
                int shift = (int)second.asLong();
                if (shift < 0){
                    return LEFT_SHIFT.compute(c, first, vl.parse(-shift));
                }
                return IntStream.range(1, c.maxBitWidth + 1).mapToObj(i -> {
                    if (i + shift > c.maxBitWidth){
                        return bl.create(ZERO);
                    }
                    return first.get(i + shift);
                }).collect(Value.collector());
            }
            return createUnknownValue(first, second);
        }
    };

    static Value setMultSign(Value first, Value second, Value result){
        assert second.isConstant();
        Bit sign = null;
        if (first.signBit().val() == second.signBit().val()){
            sign = bl.create(ZERO);
        } else if (first.signBit().isConstant()){
            sign = bl.create(ONE);
        } else {
            sign = first.signBit();
        }
        Bit _sign = sign;
        return IntStream.range(1, result.size() + 1).mapToObj(i -> i == result.size() ? _sign : result.get(i)).collect(Value.collector());
    }

    static Value createUnknownValue(Value... deps){
        int size = Stream.of(deps).mapToInt(Value::size).max().getAsInt();
        DependencySet depBits = Stream.of(deps).flatMap(Value::stream).filter(Bit::isAtLeastUnknown).collect(DependencySet.collector());
        return IntStream.range(0, size).mapToObj(i -> bl.create(U, depBits)).collect(Value.collector());
    }

    BinaryOperator MULTIPLY = new BinaryOperator("*") {
        @Override
        Value compute(Context c, Value first, Value second) {
            if (first.isConstant() && second.isConstant()) {
                return vl.parse(first.asLong() * second.asLong());
            }
            if (second.isPowerOfTwo()) {
                return LEFT_SHIFT.compute(c, first, vl.parse((int) log2(second.asLong())));
            }
            if (first.isPowerOfTwo()) {
                return MULTIPLY.compute(c, second, first);
            }
            return createUnknownValue(first, second);
        }

        @Override
        public Interval computeForIntervals(Context c, Value res, Interval first, Interval second) {
            try {
                return new Interval(Math.multiplyExact(first.start, second.start),
                        Math.multiplyExact(first.end, second.end));
            } catch (ArithmeticException ex){
                return Interval.forBitWidth(vl.bitWidth);
            }
        }
    };

    BinaryOperator DIVIDE = new BinaryOperator("/") {
        @Override
        Value compute(Context c, Value first, Value second) {
            if (first.isConstant() && second.isConstant()){
                return vl.parse(first.asLong() * second.asLong());
            }
            if (second.isPowerOfTwoOrNegPowerOfTwo() && first.isPositive()){
                int k = (int)log2(Math.abs(second.asLong()));  // (x + (2^k-1)) >> k  // hackers's delight
                Value val = RIGHT_SHIFT.compute(c, ADD.compute(c, first, vl.parse((long)Math.pow(2, k - 1))), vl.parse(k));
                if (!second.isPowerOfTwo()){ // -2^k: negate
                    return ADD.compute(c, vl.parse(1), NOT.compute(c, val));
                }
                return val;
            }
            return createUnknownValue(first, second);
        }
    };

    BinaryOperator MODULO = new BinaryOperator("+") {
        @Override
        Value compute(Context c, Value first, Value second) {
            if (first.isConstant() && second.isConstant()) {
                return vl.parse(first.asLong() % second.asLong());
            }
            if (second.isPowerOfTwo() && !second.isNegative()) {
                return IntStream.range(1, c.maxBitWidth).mapToObj(i -> {
                    if (i > log2(second.asLong())) {
                        return bl.create(ZERO);
                    }
                    return first.get(i);
                }).collect(Value.collector());
            }
            return createUnknownValue(first, second);
        }
    };

    BinaryOperator APPEND = new BinaryOperator("@") {

        @Override
        public boolean supportsArguments(List<Value> arguments) {
            return true; //return arguments.get(0) instanceof AppendOnlyValue;
        }

        @Override
        Value compute(Context c, Value first, Value second) {
            if (!(first instanceof AppendOnlyValue)){
                return compute(c, new AppendOnlyValue(first.stream().toArray(Bit[]::new)), second);
            }
            return ((AppendOnlyValue)first).append(second);
        }

        @Override
        public boolean allowsUnevaluatedArguments() {
            return true;
        }
    };

    default Value compute(Context c, List<Value> arguments){
        throw new RuntimeException("Not implemented");
    }

    default Value computeWithIntervals(Context c, Parser.MJNode node, List<Value> arguments){
        if (!supportsArguments(arguments)){
            throw new NildumuError("Unsupported operation " + toString(arguments));
        }
        Value val = compute(c, node, arguments);
        if (val.canHaveInterval() && c.inIntervalMode()){
            Interval interval = computeForIntervals(c, val, arguments.stream().map(Value::getInterval).collect(Collectors.toList()));
            if (interval != null){
                val.setInterval(interval);
            }
            bl.reachableBits(val.bits, arguments.stream().flatMap(Value::stream).collect(Collectors.toSet()));
        }
        return val;
    }

    default Value compute(Context c, Parser.MJNode node, List<Value> arguments){
        return compute(c, arguments);
    }

    String toString(List<Value> arguments);

    default boolean allowsUnevaluatedArguments(){
        return false;
    }

    default boolean supportsArguments(List<Value> arguments){
        return arguments.stream().noneMatch(a -> a instanceof AppendOnlyValue);
    }

    default Interval computeForIntervals(Context c, Value result, List<Interval> intervals){
        return null;
    }
}
