package nildumu;

import nildumu.intervals.Interval;
import nildumu.intervals.Intervals;
import nildumu.util.Util;
import swp.util.Pair;

import java.time.temporal.ValueRange;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static nildumu.Lattices.B.*;
import static nildumu.util.Util.log2;
import static nildumu.util.Util.toBinaryString;

/**
 * The basic lattices needed for the project
 */
public class Lattices {

    /**
     * If true, then enable additional checks
     */
    private static final boolean ENABLE_MISC_CHECKS = false;

    @FunctionalInterface
    public interface IdToElement {
        IdToElement DEFAULT = x -> null;

        Object toElem(String id);
    }

    /**
     * A basic lattice that contains elements of type T. Has only a bottom element.
     *
     *
     *
     * @param <T> type of the elements
     */
    public interface Lattice<T> {

        /**
         * Supremum of the two elements
         */
        T sup(T a, T b);

        /**
         * Calculates the supremum of the elements
         */
        default T sup(Stream<T> elems){
            return elems.reduce(bot(), this::sup);
        }

        /**
         * Infimum of the two elements
         */
        T inf(T a, T b);

        /**
         * Calculates the supremum of the elements
         *
         * throws an error if elems is empty
         */
        default T inf(Stream<T> elems){
            return elems.reduce(this::inf).get();
        }

        /**
         * Smallest element
         */
        T bot();

        /**
         * Calculate the minimum on the inputs, if they are comparable
         */
        default Optional<T> min(T a, T b){
            T infElemen = inf(a, b);
            if (infElemen.equals(a)){
                return Optional.of(a);
            }
            if (infElemen.equals(b)){
                return Optional.of(b);
            }
            return Optional.empty();
        }

        /**
         * Calculate the minimum on the inputs, if they are comparable
         */
        default Optional<T> max(T a, T b){
            T supElement = sup(a, b);
            if (supElement.equals(a)){
                return Optional.of(a);
            }
            if (supElement.equals(b)){
                return Optional.of(b);
            }
            return Optional.empty();
        }

        /**
         * a < b?
         */
        default boolean lowerEqualsThan(T a, T b){
            return inf(a, b).equals(a);
        }

        /**
         * a < b?
         */
        default boolean greaterEqualsThan(T a, T b){
            return sup(a, b).equals(a);
        }

        /**
         * Is elem one of the elements in the passed list
         */
        default boolean in(T elem, T... elements){
            for (T e : elements) {
                if (elem.equals(e)){
                    return true;
                }
            }
            return false;
        }

        default String toString(T elem){
            return elem.toString();
        }

        default T parse(String str){
            return parse(str, IdToElement.DEFAULT);
        }

        default T parse(String str, IdToElement idToElement){
            return parse(0, str, idToElement).first;
        }

        /**
         *
         * @param start
         * @param str
         * @return (result, index of char after the parsed)
         */
        Pair<T, Integer> parse(int start, String str, IdToElement idToElement);
    }

    public static class ParsingError extends NildumuError {
        public ParsingError(String source, int column, String message){
            super(String.format("Error in '%s' in column %d: %s", source.substring(0, column) + "\uD83D\uDDF2" + source.substring(column), column, message));
        }
    }

    /**
     * A set of lattice elements
     *
     * @param <T> type of the elements
     */
    public static class SetLattice<T, X extends Set<T>> implements Lattice<X> {

        final Lattice<T> elementLattice;
        final Function<Collection<T>, X> setProducer;
        private final X bot;

        public SetLattice(Lattice<T> elementLattice, Function<Collection<T>, X> setProducer) {
            this.elementLattice = elementLattice;
            this.setProducer = setProducer;
            this.bot = setProducer.apply(Collections.emptySet());
        }

        public X create(T... elements){
            return setProducer.apply(Arrays.asList(elements));
        }

        public X create(Collection<T> elements){
            return setProducer.apply(elements);
        }

        @Override
        public X sup(X a, X b) {
            List<T> t = new ArrayList<>();
            t.addAll(a);
            t.addAll(b);
            return setProducer.apply(t);
        }

        @Override
        public X inf(X a, X b) {
            X newSet = setProducer.apply(a);
            newSet.retainAll(b);
            return newSet;
        }

        @Override
        public X bot() {
            return bot;
        }

        /**
         * Parses comma separated sets, that are encased in "{" and "}", ids are prefixed by "#"
         * @param str
         * @return
         */
        public Pair<X, Integer> parse(int start, String str, IdToElement idToElement) {
            while (str.charAt(start) == ' ' && start < str.length() - 1){
                start++;
            }
            if (str.charAt(start) != '{'){
                if (str.charAt(start) == 'ø'){
                    return new Pair<>(bot, start + 1);
                }
                throw new ParsingError(str, start, "Expected '{'");
            }
            int i = start + 1;
            Set<T> elements = new HashSet<>();
            for (; i < str.length() - 1; i++) {
                while (str.charAt(i) == ' ' && i < str.length() - 1){
                    i++;
                }
                if (str.charAt(i) == '#'){
                    i++;
                    int end = i;
                    while (Character.isJavaIdentifierPart(str.charAt(end)) && end < str.length() - 1){
                        end++;
                    }
                    String id = str.substring(i, end);
                    Object res = idToElement.toElem(id);
                    if (res == null){
                        throw new NildumuError(String.format("No such id %s", id));
                    }
                    elements.add((T)res);
                    i = end;
                } else {
                    Pair<T, Integer> ret = elementLattice.parse(i, str, idToElement);
                    elements.add(ret.first);
                    i = ret.second;
                }
                while (str.charAt(i) == ' ' && i < str.length() - 1){
                    i++;
                }
                switch (str.charAt(i)){
                    case ',':
                        continue;
                    case '}':
                        return new Pair<>(create(elements), i + 1);
                    default:
                        throw new ParsingError(str, i, "Expected '}'");
                }
            }
            if (str.charAt(i) != '}'){
                throw new ParsingError(str, i, "Expected '}'");
            }
            return new Pair<>(create(elements), i + 1);
        }

        @Override
        public String toString(X elem) {
            if (elem.isEmpty()){
                return "ø";
            }
            return "{" + elem.stream().map(elementLattice::toString).collect(Collectors.joining(", ")) + "}";
        }

    }

    public interface CompleteLattice<T> extends Lattice<T> {
        T top();

        /**
         * Calculates the supremum of the elements
         *
         * throws an error if elems is empty
         */
        default T inf(Stream<T> elems){
            return elems.reduce(top(), this::inf);
        }

    }

    public interface BoundedLattice<T> extends CompleteLattice<T> {
        Set<T> elements();
    }

    public interface SecurityLattice<T extends Sec> extends BoundedLattice<T> {

        static SecurityLattice<?> forName(String name){
            if (lattices().containsKey(name)){
                return lattices().get(name);
            }
            throw new NoSuchElementException(String.format("No such security lattice %s, expected one of these: %s", name, String.join(", ",lattices().keySet())));
        }

        static Map<String, SecurityLattice> lattices(){
            Map<String, SecurityLattice> map = new HashMap<>();
            map.put("basic", BasicSecLattice.get());
            map.put("diamond", DiamondSecLattice.get());
            return Collections.unmodifiableMap(map);
        }

        default String latticeName(){
            return lattices().entrySet().stream().filter(e -> getClass().equals(e.getValue().getClass())).findAny().get().getKey();
        }
    }

    public interface LatticeElement<T, L extends Lattice<T>> {

        L lattice();
    }

    public interface Sec<T extends Sec<T>> extends LatticeElement<T, SecurityLattice<T>>{}

    public enum BasicSecLattice implements SecurityLattice<BasicSecLattice>, Sec<BasicSecLattice> {
        LOW, HIGH;


        @Override
        public BasicSecLattice top() {
            return HIGH;
        }

        @Override
        public BasicSecLattice bot() {
            return LOW;
        }

        @Override
        public BasicSecLattice sup(BasicSecLattice a, BasicSecLattice b) {
            return a == LOW ? b : a;
        }

        @Override
        public BasicSecLattice inf(BasicSecLattice a, BasicSecLattice b) {
            return a == HIGH ? b : a;
        }

        @Override
        public Pair<BasicSecLattice, Integer> parse(int start, String str, IdToElement idToElement) {
            switch (str.charAt(start)){
                case 'h':
                    return new Pair<>(HIGH, start + 1);
                case 'l':
                    return new Pair<>(LOW, start + 1);
            }
            throw new ParsingError(str, start, String.format("No such security lattice element '%s'", str.substring(start, start + 1)));
        }

        public String toString() {
            return name().toLowerCase().substring(0, 1);
        }

        @Override
        public Set<BasicSecLattice> elements() {
            return new LinkedHashSet<>(Arrays.asList(LOW, HIGH));
        }

        @Override
        public SecurityLattice<BasicSecLattice> lattice() {
            return HIGH;
        }

        public static BasicSecLattice get(){
            return HIGH;
        }

    }

    public enum DiamondSecLattice implements SecurityLattice<DiamondSecLattice>, Sec<DiamondSecLattice> {
        LOW(0, "l"), MID1(1, "m"), MID2(1, "n"), HIGH(2, "h");

        final int level;
        final String shortName;

        DiamondSecLattice(int level, String shortName){
            this.level = level;
            this.shortName = shortName;
        }

        @Override
        public DiamondSecLattice top() {
            return HIGH;
        }

        @Override
        public DiamondSecLattice bot() {
            return LOW;
        }

        @Override
        public DiamondSecLattice sup(DiamondSecLattice a, DiamondSecLattice b) {
            if (a.level == b.level){
                if (a != b){
                    return HIGH;
                }
                return a;
            }
            if (a.level > b.level){
                return a;
            } else {
                return b;
            }
        }

        @Override
        public DiamondSecLattice inf(DiamondSecLattice a, DiamondSecLattice b) {
            if (a.level == b.level){
                if (a != b){
                    return LOW;
                }
                return a;
            }
            if (a.level < b.level){
                return a;
            } else {
                return b;
            }
        }

        @Override
        public Pair<DiamondSecLattice, Integer> parse(int start, String str, IdToElement idToElement) {
            switch (str.charAt(start)){
                case 'h':
                    return new Pair<>(HIGH, start + 1);
                case 'm':
                    return new Pair<>(MID1, start + 1);
                case 'n':
                    return new Pair<>(MID2, start + 1);
                case 'l':
                    return new Pair<>(LOW, start + 1);
            }
            throw new ParsingError(str, start, String.format("No such security lattice element '%s'", str.substring(start, start + 1)));
        }

        @Override
        public String toString() {
            return shortName;
        }

        @Override
        public Set<DiamondSecLattice> elements() {
            return new LinkedHashSet<>(Arrays.asList(HIGH, MID1, MID2, LOW));
        }

        @Override
        public SecurityLattice<DiamondSecLattice> lattice() {
            return HIGH;
        }

        public static DiamondSecLattice get(){
            return HIGH;
        }
    }


    /**
     * The bit nildumu, known from the BitValue paper
     */
    public enum B implements BoundedLattice<B>, LatticeElement<B, B> {

        /**
         * Bit represents a *
         */
        S("s", Optional.empty(), 4),
        /**
         * Bit represents a *
         */
        N("n", Optional.empty(), 3),
        /**
         * Bit might not be present
         */
        E("e", Optional.empty(), 1),
        /**
         * Bit is not yet evaluated
         */
        X("x", Optional.empty(), 0),
        /**
         * Value is unknown, can be 1 or 0
         */
        U("u", Optional.empty(), 2),
        ZERO("0", Optional.of(0), 1),
        ONE("1", Optional.of(1), 1);

        public final String name;
        public final Optional<Integer> value;
        public final int level;

        B(String name, Optional<Integer> value, int level) {
            this.name = name;
            this.value = value;
            this.level = level;
        }

        public B top(){
            return U;
        }

        @Override
        public B sup(B a, B b) {
            if (a != b && a.isConstant() && b.isConstant()){
                return U;
            }
            if ((lowerEqualsThan(a, U) || lowerEqualsThan(b, U)) && (a.isE() || b.isE()) && Math.max(a.level, b.level) <= N.level){
                return N;
            }
            return lowerEqualsThan(a, b) ? b : a;
        }

        private boolean isE() {
            return this == E;
        }

        @Override
        public boolean lowerEqualsThan(B a, B b) {
            return a.level < b.level || (a == b && a.level == b.level);
        }

        @Override
        public boolean greaterEqualsThan(B a, B b) {
            return lowerEqualsThan(b, a);
        }

        @Override
        public B inf(B a, B b) {
            if (a != b && a.isConstant() && b.isConstant()){
                return X;
            }
            if ((a.isE() || b.isE()) && (lowerEqualsThan(a, E) || lowerEqualsThan(b, E))){
                return X;
            }
            return lowerEqualsThan(a, b) ? a : b;
        }

        public B bot(){
            return X;
        }

        @Override
        public Pair<B, Integer> parse(int start, String str, IdToElement idToElement) {
            switch (str.charAt(start)){
                case 'x': return new Pair<>(X, start + 1);
                case 'u': return new Pair<>(U, start + 1);
                case '0': return new Pair<>(ZERO, start + 1);
                case '1': return new Pair<>(ONE, start + 1);
                case 'e': return new Pair<>(E, start + 1);
                case 'n': return new Pair<>(N, start + 1);
                case 's': return new Pair<>(S, start + 1);
            }
            throw new ParsingError(str, start, String.format("No such bit lattice element '%s'", str.substring(start, start + 1)));
        }

        @Override
        public String toString() {
            return name;
        }

        public boolean isConstant(){
            return value.isPresent();
        }

        @Override
        public Set<B> elements() {
            return new HashSet<>(Arrays.asList(X, U, ZERO, ONE, S, E));
        }

        @Override
        public B lattice() {
            return X;
        }

        public B neg(){
            switch (this){
                case ZERO:
                    return ONE;
                case ONE:
                    return ZERO;
                default:
                    return this;
            }
        }

        public boolean isAtLeastUnknown() {
            return bs.greaterEqualsThan(this, U);
        }
    }

    public interface DependencySet extends Set<Bit> {
        default Bit getSingleBit(){
            assert size() == 1;
            return iterator().next();
        }
        DependencySet map(Function<Bit, Bit> mapper);

        static Collector<Bit, ?, DependencySet> collector(){
            return Collectors.collectingAndThen(Collectors.toList(), DependencySetImpl::new);
        }

        DependencySet copy();
    }

    public static class DependencySetImpl extends HashSet<Bit> implements DependencySet {

        private DependencySetImpl(Collection<? extends Bit> c) {
            super(c);
        }

        private DependencySetImpl(Bit bit){
            this(Collections.singleton(bit));
        }

        @Override
        public boolean add(Bit bit) {
            return super.add(bit);
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean remove(Object o) {
            assert o instanceof Bit && ((Bit) o).val == X;
            return super.remove(o);
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String toString() {
            return DependencySetLattice.get().toString(this);
        }

        public static Collector<Bit, ?, DependencySet> collector(){
            return Collectors.collectingAndThen(Collectors.toList(), DependencySetImpl::new);
        }

        public Bit getSingleBit(){
            assert size() == 1;
            return iterator().next();
        }

        public DependencySet map(Function<Bit, Bit> mapper){
            return stream().map(b -> {
                Bit c = mapper.apply(b);
                if (c == null){
                    throw new NullPointerException(b.toString());
                }
                return c;
            }).collect(DependencySetImpl.collector());
        }

        @Override
        public boolean equals(Object o) {
            return (o instanceof DependencySetImpl && super.equals(o)) || (o instanceof EmptyDependencySet && this.isEmpty());
        }

        @Override
        public DependencySet copy() {
            return new DependencySetImpl(this);
        }
    }

    /**
     * Empty dependency set, used for all bits except of unknown bits.
     */
    public static class EmptyDependencySet extends AbstractSet<Bit> implements DependencySet {

        private EmptyDependencySet(){}

        @Override
        public Iterator<Bit> iterator() {
            return Collections.emptyIterator();
        }

        @Override
        public int size() {
            return 0;
        }

        @Override
        public DependencySet map(Function<Bit, Bit> mapper) {
            return this;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof EmptyDependencySet || (o instanceof DependencySetImpl && ((DependencySetImpl) o).isEmpty());
        }

        @Override
        public DependencySet copy() {
            return this;
        }
    }

    public static class DependencySetLattice extends SetLattice<Bit, DependencySet> {

        private static final DependencySetLattice instance = new DependencySetLattice();

        private final EmptyDependencySet empty = new EmptyDependencySet();

        public DependencySetLattice() {
            super(BitLattice.get(), DependencySetImpl::new);
        }

        public static DependencySetLattice get(){
            return instance;
        }

        /**
         * Use {@link this#empty()} if the set should be unmodifiable, instead
         * @return
         */
        @Override
        public DependencySet bot() {
            return new DependencySetImpl(Collections.emptySet());
        }

        @Deprecated
        @Override
        public DependencySet sup(Stream<DependencySet> elems) {
            return super.sup(elems);
        }

        @Deprecated
        @Override
        public DependencySet sup(DependencySet a, DependencySet b) {
            return super.sup(a, b);
        }

        @Deprecated
        @Override
        public DependencySet inf(Stream<DependencySet> elems) {
            return super.inf(elems);
        }

        @Deprecated
        @Override
        public DependencySet inf(DependencySet a, DependencySet b) {
            return super.inf(a, b);
        }

        public DependencySet empty(){
            return empty;
        }
    }

    public static final DependencySetLattice ds = DependencySetLattice.get();
    public static final B bs = B.U;
    public static final BitLattice bl = BitLattice.get();
    public static final ValueLattice vl = ValueLattice.get();

    public static class BitLattice implements Lattice<Bit> {

        private final static BitLattice BIT_LATTICE = new BitLattice();

        public BitLattice() {
        }

        /**
         * Returns the bit of the passed set, that are reachable from the bit
         */
        public static Set<Bit> calcReachableBits(Bit bit, Set<Bit> bits){
            Set<Bit> reachableBits = new HashSet<>();
            bl.walkBits(bit, b -> {
                if (bits.contains(b)){
                    reachableBits.add(b);
                }
            }, b -> false);
            return reachableBits;
        }

        @Deprecated
        @Override
        public Bit sup(Bit a, Bit b) {
            return create(bs.sup(a.val, b.val), ds.sup(a.deps, b.deps));
        }

        @Deprecated
        @Override
        public Bit inf(Bit a, Bit b) {
            return create(bs.inf(a.val, b.val), ds.inf(a.deps, b.deps));
        }

        @Override
        public Bit bot() {
            return create(X);
        }

        public Bit create(B val){
           /* if (val != U){
                return constantBits.get(val);
            }*/
            return new Bit(val);
        }

        public Bit create(B val, DependencySet deps) {
            if (!val.isAtLeastUnknown()){
                return create(val);
            }
            return new Bit(val, deps);
        }

        public Bit forceCreateXBit(){
            return new Bit(X);
        }

        @Override
        public Pair<Bit, Integer> parse(int start, String str, IdToElement idToElement) {
            while (str.charAt(start) == ' '){
                start++;
            }
            if (str.charAt(start) != '(' && str.charAt(start) != '#'){
                return new Pair<>(new Bit(bs.parse(start, str, idToElement).first), start + 1);
            }
            Pair<List<Object>, Integer> ret = parseTuple(start, str, idToElement, bs, ds);
            return new Pair<>(
                    new Bit((B)ret.first.get(0),
                    (DependencySet)ret.first.get(1)), ret.second);
        }

        @Override
        public String toString(Bit bit) {
            return bit.toString();
        }

        public static BitLattice get(){
            return BIT_LATTICE;
        }

        public void walkBits(Bit startBit, Consumer<Bit> consumer, Predicate<Bit> ignoreBit){
            walkBits(startBit, consumer, ignoreBit, new HashSet<>());
        }

        public void walkBits(Bit startBit, Consumer<Bit> consumer, Predicate<Bit> ignoreBit, Set<Bit> alreadyVisitedBits){
            Stack<Bit> bitsToVisit = new Stack<>();
            if (ignoreBit.test(startBit)){
                return;
            }
            bitsToVisit.push(startBit);
            while (!bitsToVisit.isEmpty()){
                Bit cur = bitsToVisit.pop();
                if (alreadyVisitedBits.contains(cur)){
                    continue;
                }
                consumer.accept(cur);
                if (!ignoreBit.test(cur)){
                    bitsToVisit.addAll(cur.deps);
                }
                alreadyVisitedBits.add(cur);
            }
        }

        /**
         * Doesn't look into the interdependencies of the end bits
         */
        public Set<Bit> reachableBits(Collection<Bit> startBits, Set<Bit> endBits){
            Set<Bit> reachable = new HashSet<>();
            Set<Bit> alreadyVisitedBits = new HashSet<>();
            Stack<Bit> bitsToVisit = new Stack<>();
            bitsToVisit.addAll(startBits);
            while (!bitsToVisit.isEmpty()){
                Bit cur = bitsToVisit.pop();
                if (endBits.contains(cur)){
                    reachable.add(cur);
                    continue;
                }
                if (alreadyVisitedBits.contains(cur)){
                    continue;
                }
                bitsToVisit.addAll(cur.deps);
                alreadyVisitedBits.add(cur);
            }
            return reachable;
        }

        public void walkBits(Bit startBit, Consumer<Bit> consumer, Predicate<Bit> ignoreBit, Set<Bit> alreadyVisitedBits, Function<Bit, Collection<Bit>> next){
            Stack<Bit> bitsToVisit = new Stack<>();
            if (ignoreBit.test(startBit)){
                return;
            }
            bitsToVisit.push(startBit);
            while (!bitsToVisit.isEmpty()){
                Bit cur = bitsToVisit.pop();
                if (alreadyVisitedBits.contains(cur)){
                    continue;
                }
                consumer.accept(cur);
                if (!ignoreBit.test(cur)){
                    bitsToVisit.addAll(next.apply(cur));
                }
                alreadyVisitedBits.add(cur);
            }
        }
    }

    /**
     * Parse a tuple of lattice elements
     */
    static Pair<List<Object>, Integer> parseTuple(int start, String str, IdToElement idToElement, Lattice<?>... latticesForParsing){
        while (str.charAt(start) == ' '){
            start++;
        }
        if (str.charAt(start) != '('){
            throw new ParsingError(str, start, "Expected '('");
        }
        int i = start + 1;
        List<Object> elements = new ArrayList<>();
        Lattice<?> curLattice = latticesForParsing[0];
        for (; i < str.length() - 1 && elements.size() < latticesForParsing.length; i++) {
            while (str.charAt(i) == ' '){
                i++;
            }
            Pair<?, Integer> ret = curLattice.parse(i, str, idToElement);
            i = ret.second;
            elements.add(ret.first);
            curLattice = latticesForParsing[elements.size()];
            while (str.charAt(i) == ' '){
                i++;
            }
            switch (str.charAt(i)){
                case ',':
                    continue;
                case ')':
                    return new Pair<>(elements, i + 1);
                default:
                    throw new ParsingError(str, i, "Expected ')'");
            }
        }
        if (str.charAt(i) != ')'){
            throw new ParsingError(str, i, "Expected ')'");
        }
        return new Pair<>(elements, i + 1);
    }

    public static class Bit implements LatticeElement<Bit, BitLattice> {

        private static long NUMBER_OF_BITS = 0;

        public static boolean toStringGivesBitNo = false;

        private B val;
        private DependencySet deps;
        /**
         * Like the identity in the thesis
         */
        final long bitNo;
        private int valueIndex = 0;
        private Value value = null;
        /**
         * Store to use by analyses
         */
        Object store = null;

        private Bit(B val, DependencySet deps) {
            this.val = val;
            this.deps = deps;
            this.bitNo = NUMBER_OF_BITS++;
            assert checkInvariant();
        }

        private Bit(B val){
            this(val, val.isConstant() ? ds.empty() : ds.bot());
        }

        @Override
        public String toString() {
            if (toStringGivesBitNo){
                return bitNo + "";
            }
            String inputStr = isInputBit() ? "#" : "";
            if (valueIndex == 0){
                return val.toString();
            }
            return String.format("%s%s[%d]%s", inputStr, value == null ? "" : (value.node() == null ? value.description() : value.node().getTextualId()), valueIndex, val);
        }

        @Override
        public BitLattice lattice() {
            return BitLattice.get();
        }

        public boolean hasDependencies(){
            return deps.size() > 0;
        }

        /**
         * Check the (const → no data deps) invariant
         * @return
         */
        public boolean checkInvariant(){
            return !val.isConstant() || deps.isEmpty();
        }

        public boolean isConstant(){
            return val.isConstant();
        }

        /**
         * Compares the val and the dependencies
         */
        public boolean valueEquals(Bit other){
            return val.equals(other.val) && deps.equals(other.deps);
        }

        /**
         * Is this >= other
         * @param other
         * @return
         */
        public boolean valueGreaterEquals(Bit other){
            return bs.greaterEqualsThan(val, other.val) && ds.greaterEqualsThan(deps(), other.deps());
        }

        public String repr() {
            String name = "";
            if (value != null && !value.description.isEmpty()){
                name = String.format("%s[%d]", value.node() == null ? value.description() : value.node().getTextualId(), valueIndex);
            }
            return String.format("(%s%s, %s)", name, bs.toString(val), ds.toString(deps));
        }

        public Bit valueIndex(int index){
            if (valueIndex == 0) {
                valueIndex = index;
            }
            return this;
        }

        public Value value(){
            return value;
        }

        public Bit value(Value value){
            if (this.value == null) {
                this.value = value;
            }
            return this;
        }

        public boolean isUnknown(){
            return val == B.U;
        }

        public boolean isAtLeastUnknown() {
            return val.isAtLeastUnknown();
        }

        public boolean isInputBit(){
            return isUnknown() && !hasDependencies();
        }

        public static long getNumberOfCreatedBits(){
            return NUMBER_OF_BITS;
        }

        public static void resetNumberOfCreatedBits(){
            NUMBER_OF_BITS = 0;
        }

        public String uniqueId(){
            return bitNo + "";
        }

        public void addDependency(Bit newDependency){
            if (!isAtLeastUnknown()){
                return;
            }
            if (deps instanceof EmptyDependencySet){
                deps = new DependencySetImpl(newDependency);
            } else {
                deps.add(newDependency);
            }
        }

        public Bit addDependencies(Iterable<Bit> newDependencies){
            newDependencies.forEach(this::addDependency);
            return this;
        }

        public void alterDependencies(Function<Bit, Bit> transformer){
            if (deps.size() > 0){
                this.deps = deps.map(transformer);
            }
        }

        public void setVal(B newVal){
            assert bs.greaterEqualsThan(newVal, val);
            this.val = newVal;
        }

        public B val() {
            return val;
        }

        public DependencySet deps() {
            return deps;
        }


        public Bit copy(){
            return new Bit(val, deps.copy());
        }

        public void removeXDependency(Bit bit) {
            assert bit.val == X;
            deps.remove(bit);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Bit bit = (Bit) o;
            return bitNo == bit.bitNo;
        }

        @Override
        public int hashCode() {
            return Objects.hash(bitNo);
        }

        public Set<Bit> calculateReachedBits(Set<Bit> bitsToReach){
            Queue<Bit> q = new ArrayDeque<>();
            Set<Bit> alreadyVisitedBits = new HashSet<>();
            q.add(this);
            Set<Bit> reachedBits = new HashSet<>();
            while (!q.isEmpty()) {
                Bit cur = q.poll();
                if (bitsToReach.contains(cur)) {
                    reachedBits.add(cur);
                } else {
                    cur.deps().stream().filter(Bit::isAtLeastUnknown).filter(b -> {
                        if (alreadyVisitedBits.contains(b)) {
                            return false;
                        }
                        alreadyVisitedBits.add(b);
                        return true;
                    }).forEach(q::offer);
                }
            }
            return reachedBits;
        }
    }

    public static class ValueLattice implements Lattice<Value> {

        public int bitWidth = 32;

        private static final ValueLattice lattice = new ValueLattice();

        private static final Value BOT = ValueLattice.get().parse("0bxx");

        @Deprecated
        @Override
        public Value sup(Value a, Value b) {
            return mapBitsToValue(a, b, bl::sup);
        }

        @Deprecated
        @Override
        public Value inf(Value a, Value b) {
            return mapBitsToValue(a, b, bl::inf);
        }

        @Override
        public Value bot() {
            return parse("0bxx");
        }

        /**
         * 0b[Bits] or the integer number, bits may be followed by "{n}" with n being the number of times the bit
         * is repeated, e.g. "0bu{3}" is equal to "0buuu"
         */
        @Override
        public Pair<Value, Integer> parse(int start, String str, IdToElement idToElement) {
            if (str.length() > start + 1) {
                if (str.charAt(start) == '0' && str.charAt(start + 1) == 'b') {
                    int i = start + 2;
                    List<Bit> bits = new ArrayList<>();
                    while (i < str.length()) {
                        while (str.charAt(i) == ' ') {
                            i++;
                        }
                        if (str.charAt(i) == '{') {
                            i++;
                            int begin = i;
                            while (str.charAt(i) != '}') {
                                i++;
                            }
                            int n = Integer.parseInt(str.substring(begin, i));
                            Bit firstBit = bits.get(0);
                            bits.addAll(0, Collections.nCopies(n - 1, firstBit));
                            i++;
                        } else {
                            Pair<Bit, Integer> ret = bl.parse(i, str, idToElement);
                            i = ret.second;
                            bits.add(0, ret.first);
                            while (i < str.length() && str.charAt(i) == ' ') {
                                i++;
                            }
                        }
                    }
                    Value val = new Value(bits);
                    if (val.isConstant()) {
                        val.setInterval(new Interval(val.asLong(), val.asLong()));
                    }
                    return new Pair<>(val, i);
                }
            }
            int end = start;
            char startChar = str.charAt(start);
            if (startChar != '+' && startChar != '-' && !Character.isDigit(startChar)) {
                throw new ParsingError(str, start, "Expected number or sign");
            }
            end++;
            while (end < str.length() && Character.isDigit(str.charAt(end))) {
                end++;
            }
            return new Pair<>(parse("0b" + toBinaryString(Integer.parseInt(str.substring(start, end)))), end);
        }

        public Value parse(long val){
            return parse(Long.toString(val));
        }

        public static ValueLattice get() {
            return lattice;
        }

        public <R> List<R> mapBits(Value a, Value b, BiFunction<Bit, Bit, R> transformer) {
            int width = Math.max(a.size(), b.size());
            if (!a.hasArbitraryWidth() && !b.hasArbitraryWidth()){
                width = Math.min(width, bitWidth);
            }
            return mapBits(a, b, transformer, width);
        }

        public <R> List<R> mapBits(Value a, Value b, BiFunction<Bit, Bit, R> transformer, int width) {
            List<R> res = new ArrayList<>();
            for (int i = 1; i <= width; i++){
                res.add(transformer.apply(a.get(i), b.get(i)));
            }
            return res;
        }

        public Value mapBitsToValue(Value a, Value b, BiFunction<Bit, Bit, Bit> transformer) {
            return new Value(mapBits(a, b, transformer));
        }

        public Value mapBitsToValue(Value a, Value b, BiFunction<Bit, Bit, Bit> transformer, int width) {
            return new Value(mapBits(a, b, transformer, width));
        }

        public String toString(Value elem) {
            return elem.toString();
        }

        public void walkBits(Value value, Consumer<Bit> consumer){
            Set<Bit> alreadyVisited = new HashSet<>();
            value.forEach(b -> bl.walkBits(b, consumer, c -> false, alreadyVisited));
        }

        public void walkBits(List<Value> values, Consumer<Bit> consumer){
            Set<Bit> alreadyVisited = new HashSet<>();
            values.forEach(v -> v.forEach(b -> bl.walkBits(b, consumer, c -> false, alreadyVisited)));
        }

        public void walkBits(Collection<Bit> bits, Consumer<Bit> consumer){
            Set<Bit> alreadyVisited = new HashSet<>();
            bits.forEach(b -> bl.walkBits(b, consumer, c -> false, alreadyVisited));
        }
    }

    public static class Value implements LatticeElement<Value, ValueLattice>, Iterable<Bit> {

        final List<Bit> bits;

        private String description = "";
        private Parser.MJNode node = null;

        Interval interval = null;

        protected Value(){
            this.bits = new ArrayList<>();
        }

        public Value(List<Bit> bits) {
            //assert bits.size() > 1;
            this.bits = new ArrayList<>(bits);
            for (int i = 0; i < Math.min(bits.size(), vl == null ? 1000 : vl.bitWidth); i++) {
                Bit bit = bits.get(i);
                bit.valueIndex(i + 1);
                bit.value(this);
            }
            assert !ENABLE_MISC_CHECKS || !hasDuplicateBits();
        }

        private Value(Vector<Bit> bits) {
            //assert bits.size() > 1;
            this.bits = bits;
            assert !ENABLE_MISC_CHECKS || !hasDuplicateBits();
        }

        public Value(Bit... bits) {
            this(Arrays.asList(bits));
        }

        public static Value combine(List<Value> values) {
            return values.stream().flatMap(v -> v.withBitCountMultipleOf(ValueLattice.get().bitWidth).bits.stream()).collect(Value.collector());
        }

        /**
         * Combine the values to a single value and return a zero value if the passed list is empty
         */
        public static Value combineOrZero(List<Value> values) {
            if (values.isEmpty()) {
                return vl.parse("0");
            }
            return combine(values);
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof Value && ((Value) obj).bits.equals(bits);
        }

        @Override
        public int hashCode() {
            return bits.hashCode();
        }

        /**
         * Compares the val and the dependencies of bits
         */
        public boolean valueEquals(Value other){
            return ValueLattice.get().mapBits(this, other, Bit::valueEquals).stream().allMatch(Boolean::booleanValue);
        }

        @Override
        public String toString() {
            List<Bit> reversedBits = new ArrayList<>(bits);
            Collections.reverse(reversedBits);
            return reversedBits.stream().map(b -> b.val.toString()).collect(Collectors.joining("")) +
                    mapInterval(Interval::toString, "");
        }

        public String repr() {
            List<Bit> reversedBits = new ArrayList<>(bits);
            Collections.reverse(reversedBits);
            String ret = reversedBits.stream().map(Bit::repr).collect(Collectors.joining(""));
            if (!description.equals("")) {
                return String.format("(%s|%s)", description, ret);
            }
            return ret +  mapInterval(i -> i.toString() + "#" + entropy(), "");
        }

        public String toString(Function<Bit, String> bitToId) {
            return ValueLattice.get().toString(this);
        }

        @Override
        public ValueLattice lattice() {
            return ValueLattice.get();
        }

        public int size(){
            return bits.size();
        }

        /**
         * Returns the sign bit if the index is too big
         * @param index index that starts at 1
         * @return
         */
        public Bit get(int index){
            assert index > 0;
            while (bits.size() < index){
                bits.add(this.signBit().copy());
            }
            return bits.get(index - 1);
        }

        public boolean hasDependencies(){
            return bits.stream().anyMatch(Bit::hasDependencies);
        }

        public boolean isConstant(){
            return (bits.stream().allMatch(Bit::isConstant) && bits.size() > 0);
        }

        public long asLong(){
            assert isConstant();
            long result = 0;
            boolean neg = signBit().val == ONE;
            int signBitVal = signBit().val.value.get();
            for (int i = bits.size() - 1; i >= 0; i--){
                result = result * 2l;
                int bitVal = bits.get(i).val.value.get();
                if (signBitVal != bitVal){
                    result += 1l;
                }
            }
            if (neg){
                return -result - 1l;
            }
            return result;
        }

        public long asLong(B sign, B assumedUnknown){
            long result = 0;
            boolean neg = sign == ONE;
            int signBitVal = sign.value.get();
            for (int i = bits.size() - 1; i >= 0; i--){
                result = result * 2;
                B val = bits.get(i).val;
                int bitVal = (val == U ? assumedUnknown : val).value.get();
                if (signBitVal != bitVal){
                    result += 1;
                }
            }
            if (neg){
                return -result - 1;
            }
            return result;
        }

        @Override
        public Iterator<Bit> iterator() {
            return bits.iterator();
        }

        public Stream<Bit> stream(){
            return bits.stream();
        }

        public static Collector<Bit, ?, Value> collector(){
            return Collectors.collectingAndThen(Collectors.toList(), Value::new);
        }

        public String description(){
            return description;
        }

        public Value description(String description){
            if (this.description.isEmpty()) {
                this.description = description;
            }
            return this;
        }

        public Bit signBit(){
            return bits.get(bits.size() - 1);
        }

        public Stream<Bit> getRange(ValueRange range) {
            return Util.stream(range).mapToObj(bits::get);
        }

        public Value node(Parser.MJNode node) {
            if (this.node == null) {
                this.node = node;
            }
            return this;
        }

        public Parser.MJNode node(){
            return node;
        }

        public String toLiteralString(){
            if (isConstant() && bits.size() <= vl.bitWidth){
                return Long.toString(asLong());
            }
            List<Bit> reversedBits = new ArrayList<>(bits);
            Collections.reverse(reversedBits);
            return "0b" + reversedBits.stream().map(b -> b.val.toString()).collect(Collectors.joining(""));
        }

        public boolean isNegative(){
            return signBit().val == ONE;
        }

        public boolean isPositive(){
            return signBit().val == ZERO;
        }

        public Value map(Function<Bit, Bit> mapper){
            return stream().map(mapper).collect(Value.collector());
        }

        public Set<Bit> bitSet(){
            return new HashSet<>(bits);
        }

        public boolean isPowerOfTwo(){
            if (!isConstant()){
                return false;
            }
            double twoLog = log2(asLong());
            return ((int)twoLog) == twoLog;
        }

        public void add(Bit bit){
            //assert bits.size() <= vl.bitWidth;
            bits.add(bit);
        }

        public boolean isBot(){
            return stream().allMatch(b -> b.val == X);
        }

        public int numberOfDistinctBits(){
            return (int)stream().map(b -> b.bitNo).distinct().count();
        }

        public boolean hasDuplicateBits(){
            return numberOfDistinctBits() != size();
        }

        public AppendOnlyValue asAppendOnly() {
            return new AppendOnlyValue(bits.toArray(new Bit[0]));
        }

        static Value createEmpty(){
            Value val = new Value();
            for (int i = 0; i < vl.bitWidth; i++) {
                val.add(new Bit(X));
            }
            return val;
        }

        public boolean hasArbitraryWidth() {
            return false;
        }

        public boolean bitValEquals(Value other) {
            return vl.mapBits(this, other, (a, b) -> a.val() == b.val()).stream().allMatch(Boolean::booleanValue);
        }

        public boolean endsWithStar(){
            return get(size()).val == S;
        }

        public boolean valueGreaterEquals(Value other) {
            return vl.mapBits(this, other, Bit::valueGreaterEquals).stream().allMatch(Boolean::booleanValue);
        }

        public Intervals.Constraints asConstraints(){
            return new Intervals.Constraints(){

                @Override
                public B get(int index) {
                    return Value.this.get(index + 1).val;
                }

                @Override
                public int size() {
                    return Value.this.size();
                }
            };
        }

        public boolean hasInterval(){
            return interval != null;
        }

        public boolean canHaveInterval(){
            return true;
        }

        public Interval getInterval(){
            if (canHaveInterval() && interval == null) {
                interval = Interval.forBitWidth(vl.bitWidth);
            }
            return interval;
        }

        public <T> T mapInterval(Function<Interval, T> func, T defaultVal){
            if (hasInterval()){
                return func.apply(getInterval());
            }
            return defaultVal;
        }

        public void setInterval(Interval interval){
            //Objects.requireNonNull(interval);
            this.interval = interval;
        }

        /**
         * Entropy of this value in bits
         *
         * @return entropy
         */
        public double entropy(){
            if (hasInterval()){
                return Util.log2(Intervals.countPattern(interval, asConstraints()));
            }
            return this.stream().filter(Bit::isAtLeastUnknown).count();
        }

        public boolean singleValued() {
            if (isConstant()){
                return true;
            }
            return hasInterval() && interval.start == interval.end; // TODO
        }

        public long singleValue() {
            assert singleValued();
            if (isConstant()) {
                return asLong();
            }
            return interval.start;
        }

        public List<Value> split() {
            assert bits.size() % vl.bitWidth == 0;
            return split(bits.size() / vl.bitWidth);
        }

        public List<Value> split(int partCount) {
            if (partCount == 0) {
                return Collections.emptyList();
            }
            if (isBot()) {
                return IntStream.range(0, partCount).mapToObj(i -> vl.bot()).collect(Collectors.toList());
            }
            assert bits.size() % partCount == 0;
            int partSize = bits.size() / partCount;
            return IntStream.range(0, partCount)
                    .mapToObj(i -> new Value(bits.subList(i * partSize, (i + 1) * partSize)))
                    .collect(Collectors.toList());
        }

        /**
         * create a value that mirrors this value but has a specific number of bits, fill with zeros
         */
        public Value withBitCount(int width) {
            List<Bit> newBits = bits.subList(0, Math.min(width, bits.size()));
            while (newBits.size() < width) {
                newBits.add(this.signBit().copy());
            }
            return new Value(newBits);
        }

        public Value withBitCountMultipleOf(int width) {
            return withBitCount((int) (Math.ceil(bits.size() * 1.0 / width) * width));
        }

        public boolean isNotEmpty() {
            return bits.size() > 0;
        }

        /**
         * Add deps to each bit and returns the current value
         */
        public Value addDeps(Iterable<Bit> deps) {
            bits.forEach(b -> b.addDependencies(deps));
            return this;
        }

        public boolean mightBe(boolean b) {
            /*if (singleValued()) {
                return (asInt() != 0) == b;
            }
            return b || bits.stream().allMatch(bit -> bit.val != ONE);*/
            return get(1).val == U || is(b);
        }

        public boolean is(boolean b) {
            return b ? (get(1).val == ONE) : (get(1).val == ZERO);
        }

        public Value assume(B sign) {
            assert sign.isConstant();
            if (sign == signBit().val) {
                return this;
            }
            Vector<Bit> newBits = new Vector<>(bits);
            newBits.set(bits.size() - 1, bl.create(sign));
            return new Value(newBits);
        }

        public long largest() {
            return largest(signBit().val);
        }

        public long largest(B sign) {
            assert sign.isConstant();
            if (sign == ZERO) {
                return asLong(sign, ONE);
            }
            return asLong(sign, ZERO);
        }

        public long smallest() {
            return smallest(signBit().val);
        }

        public long smallest(B sign) {
            assert sign.isConstant();
            if (sign == ZERO) {
                return asLong(sign, ZERO);
            }
            return asLong(sign, ONE);
        }

        /**
         * Returns the bit indices (without the sign indices),
         * indices = highest non sign bit indices, …, last consecutive val bit indices + app
         */
        public IntStream highBitIndicesWOSign(B val, int app) {
            int smallest = bits.size();
            for (int i = bits.size() - 1; i >= 1 && get(i).val == val; i--) {
                smallest = i;
            }
            return IntStream.range(Math.max(1, smallest + app), bits.size());
        }
    }

    /**
     * Only appending bits is possible, but not accessing the individual
     */
    public static class AppendOnlyValue extends Value {

        public AppendOnlyValue(Bit... bits) {
            super(Arrays.stream(bits).map(b -> b.val == X ? bl.create(E) : b).toArray(Bit[]::new));
        }

        @Override
        public Bit get(int index) {
            assert index > 0;
            while (size() < index) {
                add(new Bit(E));
            }
            return super.get(index);
        }

        public AppendOnlyValue append(Value value, int bitWidth){
            AppendOnlyValue newValue = new AppendOnlyValue();
            stream().filter(b -> !b.val.isE() && b.val != X).forEach(newValue::add);
            value.stream().forEach(newValue::add);
            return newValue;
        }

        public AppendOnlyValue append(Value value){
            return append(value, vl.bitWidth);
        }

        public static AppendOnlyValue createEmpty() {
            AppendOnlyValue val = new AppendOnlyValue();
            for (int i = 0; i < vl.bitWidth; i++) {
                val.add(new Bit(E));
            }
            return val;
        }

        @Override
        public AppendOnlyValue asAppendOnly() {
            return this;
        }

        @Override
        public boolean hasArbitraryWidth() {
            return true;
        }

        /**
         * Assumes that the shorter value is a substring of the longer
         *
         * @param other other value
         * @return bits that one value has and the other has not
         */
        public AppendOnlyValue difference(AppendOnlyValue other) {
            if (other.sizeWithoutEs() < this.sizeWithoutEs()){
                return other.difference(this);
            }
            if (other.sizeWithoutEs() == this.sizeWithoutEs()){
                return new AppendOnlyValue();
            }
            return new AppendOnlyValue(other.bits.subList(sizeWithoutEs(), other.sizeWithoutEs()).toArray(new Bit[0]));
        }

        @Override
        public void add(Bit bit) {
            super.add(bit);
        }

        public int sizeWithoutEs() {
            // TODO: improve
            return (int) bits.stream().filter(b -> b.val != E).count();
        }

        @Override
        public AppendOnlyValue clone(){
            return new AppendOnlyValue(bits.toArray(new Bit[0]));
        }

        /**
         * Clones also the bits of the value
         */
        public AppendOnlyValue deepClone(){
            return new AppendOnlyValue(bits.stream().map(Bit::copy).toArray(Bit[]::new));
        }

        public AppendOnlyValue cloneWithoutEs(){
            return new AppendOnlyValue(stream().filter(b -> b.val != E).toArray(Bit[]::new));
        }

        public double entropy(){
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean hasInterval() {
            return false;
        }
    }
}
