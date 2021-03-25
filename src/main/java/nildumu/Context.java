package nildumu;

import nildumu.intervals.Interval;
import nildumu.mih.MethodInvocationHandler;
import nildumu.util.DefaultMap;
import nildumu.util.Util;
import swp.util.Pair;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static nildumu.Lattices.*;
import static nildumu.Parser.*;
import static nildumu.util.DefaultMap.ForbiddenAction.FORBID_DELETIONS;
import static nildumu.util.DefaultMap.ForbiddenAction.FORBID_VALUE_UPDATES;

/**
 * The context contains the global state and the global functions from the thesis.
 * <p/>
 * This is this basic idea version, but with the loop extension.
 */
public class Context {

    /**
     * Mode
     */
    public static enum Mode {
        BASIC,
        /**
         * Combines the basic mode with the tracking of path knowledge
         */
        EXTENDED,
        /**
         * Combines the extended mode with the support for loops
         */
        LOOP,
        /**
         * Add support for intervals
         */
        INTERVAL;

        @Override
        public String toString() {
            return name().toLowerCase().replace("_", " ");
        }
    }

    public static class NotAnInputBit extends NildumuError {
        NotAnInputBit(Bit offendingBit, String reason){
            super(String.format("%s is not an input bit: %s", offendingBit.repr(), reason));
        }
    }

    public static @FunctionalInterface interface ModsCreator {
        public Mods apply(Context context, Bit bit, Bit assumedValue);
    }

    public static class InvariantViolationError extends NildumuError {
        InvariantViolationError(String msg){
            super(msg);
        }
    }

    public static final Logger LOG = Logger.getLogger("Analysis");
    static {
        LOG.setLevel(Level.INFO);
    }

    public static class Branch {
        public final ExpressionNode condition;
        public final boolean val;

        public Branch(ExpressionNode condition, boolean val) {
            this.condition = condition;
            this.val = val;
        }

        @Override
        public String toString() {
            return String.format("(%s, %s)", condition, val);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Branch branch = (Branch) o;
            return val == branch.val &&
                    Objects.equals(condition, branch.condition);
        }

        @Override
        public int hashCode() {
            return Objects.hash(condition, val);
        }
    }

    public final SecurityLattice<?> sl;

    public final int maxBitWidth;

    public final IOValues input = new IOValues();

    public final IOValues output = new IOValues();

    public final boolean USE_REDUCED_ADD_OPERATOR = true;

    private final DefaultMap<Bit, Sec<?>> secMap =
            new DefaultMap<>(
                    new IdentityHashMap<>(),
                    new DefaultMap.Extension<Bit, Sec<?>>() {
                        @Override
                        public Sec<?> defaultValue(Map<Bit, Sec<?>> map, Bit key) {
                            return sl.bot();
                        }
                    },
                    FORBID_DELETIONS,
                    FORBID_VALUE_UPDATES);

    private final DefaultMap<MJNode, Operator> operatorPerNode = new DefaultMap<>(new IdentityHashMap<>(), new DefaultMap.Extension<MJNode, Operator>() {

        @Override
        public Operator defaultValue(Map<MJNode, Operator> map, MJNode key) {
            Operator op = key.getOperator(Context.this);
            if (op == null){
                if (key instanceof BinaryOperatorNode) {
                    throw new NildumuError(String.format("No operator for binary operator %s implemented", ((BinaryOperatorNode) key).operator));
                }
                if (key instanceof UnaryOperatorNode) {
                    throw new NildumuError(String.format("No operator for unary operator %s implemented", ((UnaryOperatorNode) key).operator));
                }
                throw new NildumuError(String.format("No operator for %s implemented", key));
            }
            return op;
        }
    }, FORBID_DELETIONS, FORBID_VALUE_UPDATES);

    public static class CallPath {
        final List<MethodInvocationNode> path;

        CallPath(){
            this(Collections.emptyList());
        }

        CallPath(List<MethodInvocationNode> path) {
            this.path = path;
        }

        public CallPath push(MethodInvocationNode callSite){
            List<MethodInvocationNode> newPath = new ArrayList<>(path);
            newPath.add(callSite);
            return new CallPath(newPath);
        }

        public CallPath pop() {
            List<MethodInvocationNode> newPath = new ArrayList<>(path);
            newPath.remove(newPath.size() - 1);
            return new CallPath(newPath);
        }

        @Override
        public int hashCode() {
            return path.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof CallPath && ((CallPath) obj).path.equals(path);
        }

        @Override
        public String toString() {
            return path.stream().map(m -> m.method).collect(Collectors.joining(" → "));
        }

        public boolean isEmpty() {
            return path.isEmpty();
        }

        public MethodInvocationNode peek() {
            return path.get(path.size() - 1);
        }
    }

    public static class NodeValueState {

        long nodeValueUpdateCount = 0;

        final DefaultMap<MJNode, Value> nodeValueMap = new DefaultMap<>(new LinkedHashMap<>(), new DefaultMap.Extension<MJNode, Value>() {

            @Override
            public void handleValueUpdate(DefaultMap<MJNode, Value> map, MJNode key, Value value) {
                assert value.isNotEmpty();
                if (vl.mapBits(map.get(key), value, (a, b) -> a != b).stream().anyMatch(p -> p)){
                    nodeValueUpdateCount++;
                }
            }

            @Override
            public Value defaultValue(Map<MJNode, Value> map, MJNode key) {
                return vl.bot();
            }
        }, FORBID_DELETIONS);

        long nodeVersionUpdateCount = 0;

        /**
         * Does not include the nodes that are updated with append only values (aka print variables)
         */
        long nodeVersionWOAppendValuedUpdateCount = 0;

        final DefaultMap<MJNode, Integer> nodeVersionMap = new DefaultMap<>(new LinkedHashMap<>(), new DefaultMap.Extension<MJNode, Integer>() {

            @Override
            public void handleValueUpdate(DefaultMap<MJNode, Integer> map, MJNode key, Integer value) {
                if (!map.get(key).equals(value)){
                    if (!(nodeValueMap.get(key) instanceof AppendOnlyValue)){
                        nodeVersionWOAppendValuedUpdateCount++;
                    }
                    nodeVersionUpdateCount++;
                }
            }

            @Override
            public Integer defaultValue(Map<MJNode, Integer> map, MJNode key) {
                return 0;
            }
        }, FORBID_DELETIONS);

        final DefaultMap<Variable, Integer> variableVersionMap = new DefaultMap<>(new LinkedHashMap<>(), new DefaultMap.Extension<Variable, Integer>() {

            @Override
            public void handleValueUpdate(DefaultMap<Variable, Integer> map, Variable key, Integer value) {
            }

            @Override
            public Integer defaultValue(Map<Variable, Integer> map, Variable key) {
                return 0;
            }
        }, FORBID_DELETIONS);

        final CallPath path;

        private final Map<Branch, Mods> modsMap = new DefaultMap<>((map, bit) -> {
            return Mods.empty();
        });

        private final Stack<Branch> branchStack = new Stack<>();

        private final Map<Branch, Optional<Branch>> parentBranch = new DefaultMap<>((map, branch) -> {
            return Optional.empty();
        });

        private final Map<MJNode, List<Integer>> lastParamVersions = new HashMap<>();


        public NodeValueState(CallPath path) {
            this.path = path;
        }
    }

    private Mode mode;

    final Stack<Frame> frames = new Stack<>();
    private Frame frame;

    class Frame {
        final CallPath callPath;
        final State state;
        final Set<Bit> methodParameterBits;
        final NodeValueState nodeValueState;
        /**
         * Newly introduced input bits
         */
        final InputBits inputBits;

        Frame(CallPath callPath, Set<Bit> methodParameterBits, State state) {
            this.callPath = callPath;
            this.state = state;
            this.methodParameterBits = methodParameterBits;
            this.nodeValueState = new NodeValueState(callPath);
            this.inputBits = new InputBits();
        }

        Frame(CallPath callPath, Set<Bit> methodParameterBits) {
            this(callPath, methodParameterBits, new State(new State.OutputState()));
        }

        @Override
        public String toString() {
            return callPath.toString();
        }
    }


    /*-------------------------- extended mode specific -------------------------------*/

    private final DefaultMap<Bit, ModsCreator> replMap = new DefaultMap<>((map, bit) -> {
        return ((c, b, a) -> new Mods(notChosen(b, a), choose(b, a)));
    });

    /**
     * bits that state that either of its dependencies can be used for the current bit.
     */
    private final Set<Bit> alternativeBits = new HashSet<>();

    private boolean recordAlternatives;

    /** additional mods that come from short circuiting, e.g. in  (x && y) the mods from x == true are added
     * to the mods for evaluating y */
    private Stack<Mods> additionalMods = new Stack<>();

    /*-------------------------- loop mode specific -------------------------------*/

    private final HashMap<Bit, Double> weightMap = new HashMap<>();

    public static final float INFTY = Float.MAX_VALUE;

    /*-------------------------- methods -------------------------------*/

    private MethodInvocationHandler methodInvocationHandler;

    /*-------------------------- unspecific -------------------------------*/

    /**
     * Entropy boundaries for the input and the output, allows to use the conventional min-entropy argument
     */
    public static class EntropyBounds {

        private final Map<Sec<?>, Double> maxInputEntropyPerSec;

        private final Map<Sec<?>, Double> maxOutputNumberPerSec;

        public EntropyBounds(Map<Sec<?>, Double> maxInputEntropyPerSec, Map<Sec<?>, Double> maxOutputNumberPerSec) {
            this.maxInputEntropyPerSec = maxInputEntropyPerSec;
            this.maxOutputNumberPerSec = maxOutputNumberPerSec;
        }

        public EntropyBounds(){
            this(Collections.emptyMap(), Collections.emptyMap());
        }

        public Double getMaxInputEntropy(Sec<?> sec) {
            return maxInputEntropyPerSec.getOrDefault(sec, (double) INFTY);
        }

        public Double getMaxOutputs(Sec<?> sec) {
            return maxOutputNumberPerSec.getOrDefault(sec, (double) INFTY);
        }
    }

    private final EntropyBounds entropyBounds;

    public Context(SecurityLattice sl, int maxBitWidth, EntropyBounds entropyBounds, State.OutputState outputState,
                   boolean recordAlternatives) {
        this.sl = sl;
        this.maxBitWidth = maxBitWidth;
        this.entropyBounds = entropyBounds;
        resetFrames(outputState);
        ValueLattice.get().bitWidth = maxBitWidth;
        this.recordAlternatives = recordAlternatives;
    }

    public Context(SecurityLattice sl, int maxBitWidth, EntropyBounds entropyBounds, State.OutputState outputState) {
        this(sl, maxBitWidth, entropyBounds, outputState, true);
    }

    public Context(SecurityLattice sl, int maxBitWidth, EntropyBounds entropyBounds) {
        this(sl, maxBitWidth, entropyBounds, new State.OutputState());
    }

    public Context(SecurityLattice sl, int maxBitWidth) {
        this(sl, maxBitWidth, new EntropyBounds());
    }

    public static B v(Bit bit) {
        return bit.val();
    }

    public static DependencySet d(Bit bit) {
        return bit.deps();
    }

    /**
     * Returns the security level of the bit
     *
     * @return sec or bot if not assigned
     */
    public Sec sec(Bit bit) {
        return secMap.get(bit);
    }

    /**
     * Sets the security level of the bit
     *
     * <p><b>Important note: updating the security level of a bit is prohibited</b>
     *
     * @return the set level
     */
    private Sec sec(Bit bit, Sec<?> level) {
        return secMap.put(bit, level);
    }

    public Value addInputValue(Sec<?> sec, Value value){
        input.add(sec, value);
        for (Bit bit : value){
            if (bit.val() == B.U){
                if (!bit.deps().isEmpty()){
                    throw new NotAnInputBit(bit, "has dependencies");
                }
                sec(bit, sec);
            }
        }
        getNewlyIntroducedInputs().put(sec, value);
        return value;
    }

    public void addAppendOnlyVariable(Sec<?> sec, String variable){
        frame.state.outputState.add(sec, variable);
    }

    public State.OutputState getOutputState(){
        return frame.state.outputState;
    }

    public Value addOutputValue(Sec<?> sec, Value value){
        output.add(sec, value);
        return value;
    }

    public boolean checkInvariants(Bit bit) {
        return (sec(bit) == sl.bot() || (!v(bit).isConstant() && d(bit).isEmpty()))
                && (!v(bit).isConstant() || (d(bit).isEmpty() && sec(bit) == sl.bot()));
    }

    public void checkInvariants(){
        List<String> errorMessages = new ArrayList<>();
        walkBits(b -> {
            if (!checkInvariants(b)){
              //  errorMessages.add(String.format("Invariants don't hold for %s", b.repr()));
            }
        }, p -> false);
        throw new InvariantViolationError(String.join("\n", errorMessages));
    }

    public static void log(Supplier<String> msgProducer){
        if (LOG.isLoggable(Level.FINE)){
            System.out.println(msgProducer.get());
        }
    }

    public boolean isInputBit(Bit bit) {
        return input.contains(bit);
    }

    public Value nodeValue(MJNode node){
        if (node instanceof ParameterAccessNode){
            return getVariableValue(((ParameterAccessNode) node).definition);
        } else if (node instanceof VariableAccessNode){
            if (((VariableAccessNode) node).definingExpression != null) {
                Value val = nodeValue(((VariableAccessNode) node).definingExpression);
                if (((VariableAccessNode) node).definition.hasAppendValue){
                    return val.asAppendOnly();
                }
                return replace(val);
            }
            //return getVariableValue(((VariableAccessNode) node).definition);
        } else if (node instanceof WrapperNode){
            return ((WrapperNode<Value>) node).wrapped;
        }
        return replace(frame.nodeValueState.nodeValueMap.get(node));
    }

    public Value nodeValue(MJNode node, Value value){
        assert value.isNotEmpty();
        return frame.nodeValueState.nodeValueMap.put(node, value);
    }

    Operator operatorForNode(MJNode node){
        return operatorPerNode.get(node);
    }

    public Value op(MJNode node, List<Value> arguments){
        if (node instanceof ParameterAccessNode){
            return getVariableValue(((ParameterAccessNode) node).definition);
        }
        if (node instanceof VariableAccessNode){
            VariableAccessNode access = (VariableAccessNode)node;
            if (access.definingExpression != null) {
                return replace(nodeValue(access.definingExpression));
            }
        }
        Operator operator = operatorForNode(node);
        if (operator.allowsUnevaluatedArguments() || arguments.stream().noneMatch(Value::isBot)) {
            return operator.computeWithIntervals(this, node, arguments);
        }
        return vl.bot();
    }

    @SuppressWarnings("unchecked")
    public List<MJNode> paramNode(MJNode node){
        return (List<MJNode>) (List<?>) node.children().stream().map(n -> {
            if (n instanceof  VariableAccessNode){
                VariableAccessNode access = (VariableAccessNode)n;
                if (access.definingExpression != null) {
                    return access.definingExpression;
                }
                return access;
            }
            return n;
        }).collect(Collectors.toList());
    }

    private boolean compareAndStoreParamVersion(MJNode node){
        List<Integer> curVersions = paramNode(node).stream().map(n -> {
            if (n instanceof VariableAccessNode && ((VariableAccessNode) n).definingExpression == null){
                return frame.nodeValueState.variableVersionMap.get(((VariableAccessNode) n).definition);
            }
            return frame.nodeValueState.nodeVersionMap.get(n);
        }).collect(Collectors.toList());
        if (node instanceof MethodInvocationNode){
            ((MethodInvocationNode) node).globalDefs.entrySet().stream()
                    .sorted(Comparator.comparing(e -> e.getKey().name))
                    .map(Map.Entry::getValue).map(Pair::first)
                    .map(frame.nodeValueState.variableVersionMap::get)
                    .forEach(curVersions::add);
        }
        boolean somethingChanged = true;
        if (frame.nodeValueState.lastParamVersions.containsKey(node)){
            somethingChanged = !frame.nodeValueState.lastParamVersions.get(node).equals(curVersions);
        }
        frame.nodeValueState.lastParamVersions.put(node, curVersions);
        return somethingChanged;
    }

    public boolean evaluate(MJNode node){
        log(() -> "Evaluate node " + node + " -> old value = " + nodeValue(node));

        if (node instanceof VariableAccessNode){
            Value newVal = getVariableValue(((VariableAccessNode) node).definition);
            boolean somethingChanged = !newVal.valueEquals(nodeValue(node));
            if (somethingChanged){
                frame.nodeValueState.nodeVersionMap.put(node, frame.nodeValueState.nodeVersionMap.get(node) + 1);
                frame.nodeValueState.nodeVersionUpdateCount++;
            }
            nodeValue(node, newVal);
            return somethingChanged;
        }
        boolean paramsChanged = compareAndStoreParamVersion(node);
        if (!paramsChanged){
            return false;
        }

        List<MJNode> paramNodes = paramNode(node);
        List<Value> args;
        if (node instanceof PhiNode){
            PhiNode phi = (PhiNode)node;
            assert phi.joinedVariables.size() == 2;
            ExpressionNode condition = phi.controlDepStatement.conditionalExpression;
            args = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                args.add(replace(nodeValue(phi.joinedVariables.get(i)), new Branch(condition, i == 0)));
            }
        } else {
            args = paramNodes.stream()
                    .map(this::nodeValue)
                    .map(this::replace).collect(Collectors.toList());
        }
        if (args.stream().allMatch(Value::isBot) && args.size() > 0){
            return false;
        }
        Value newValue = op(node, args);
        boolean somethingChanged = false;
        boolean gt = false;
        if (inLoopMode() && !nodeValue(node).isBot()) {
            Value oldValue = nodeValue(node);
            somethingChanged = false;
            if (!oldValue.valueEquals(newValue)){
                somethingChanged = true;
                gt = oldValue.valueGreaterEquals(newValue);
                merge(oldValue, newValue);
                nodeValue(node, oldValue);
            }
        } else {
            somethingChanged = nodeValue(node).isBot() && !newValue.isBot();
            nodeValue(node, newValue);
        }
        if (somethingChanged && !gt){
            frame.nodeValueState.nodeVersionMap.put(node, frame.nodeValueState.nodeVersionMap.get(node) + 1);
           // nodeValueState.nodeVersionUpdateCount++;
        }
        log(() -> "Evaluate node " + node + " -> new value = " + nodeValue(node));
        newValue.description(node.getTextualId()).node(node);
        return somethingChanged;
    }

    boolean merge(Value oldValue, Value newValue){
        /*List<Bit> newBits = new ArrayList<>();
        somethingChanged = vl.mapBits(oldValue, newValue, (a, b) -> {
            boolean changed = false;
            if (a.value() == null){
                a.value(oldValue); // newly created bit
                changed = true;
                newBits.add(a);
            }
            return merge(a, b) || changed;
        }).stream().anyMatch(p -> p);
        if (newBits.size() > 0){
            newValue = Stream.concat(oldValue.stream(), newBits.stream()).collect(Value.collector());
        } else {
            newValue = oldValue;
        }*/
        boolean somethingChanged = false;
        int i = 1;
        for (; i <= Math.min(oldValue.size(), newValue.size()); i++) {
            somethingChanged = merge(oldValue.get(i), newValue.get(i));
        }
        for (; i <= newValue.size(); i++) {
            oldValue.add(newValue.get(i));
            somethingChanged = true;
        }
        if (oldValue.hasInterval() && inIntervalMode()) {
            assert newValue.hasInterval();
            oldValue.interval.start = Math.min(oldValue.interval.start, newValue.interval.start);
            oldValue.interval.end = Math.min(oldValue.interval.end, newValue.interval.end);
        }
        return somethingChanged;
    }

    /**
     * Returns the unknown output bits with lower or equal security level
     */
    public List<Bit> getOutputBits(Sec<?> maxSec){
        return output.getBits().stream().filter(p -> ((SecurityLattice)sl).lowerEqualsThan(p.first, (Sec)maxSec)).map(p -> p.second).collect(Collectors.toList());
    }

    /**
     * Returns the unknown input bits with not lower security or equal level
     */
    public List<Bit> getInputBits(Sec<?> minSecEx){
        return input.getBits().stream().filter(p -> !(((SecurityLattice)sl).lowerEqualsThan(p.first, (Sec)minSecEx))).map(p -> p.second).collect(Collectors.toList());
    }

    public Value getVariableValue(Variable variable){
        return frame.state.get(variable);
    }

    public Value setVariableValue(Variable variable, Value value){
        return setVariableValue(variable, value, null);
    }
    public Value setVariableValue(Variable variable, Value value, MJNode node){
        return setVariableValue(variable, value, node, false);
    }

    public Value setVariableValue(Variable variable, Value value, MJNode node, boolean useVar){
        if (frames.size() == 1) {
            if (variable.isInput && !frames.get(0).state.get(variable).isBot()) {
                throw new UnsupportedOperationException(String.format("Setting an input variable (%s)", variable));
            }
        }
        if (useVar){
            frame.nodeValueState.variableVersionMap.put(variable, frame.nodeValueState.variableVersionMap.get(variable) + 1);
        } else {
            frame.nodeValueState.variableVersionMap.put(variable, frame.nodeValueState.nodeVersionMap.get(node));
        }
        frame.state.set(variable, value);
        return value;
    }

    public Value getVariableValue(String variable){
        return frame.state.get(variable);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("Variable states\n");
        for (int i = 0; i < frames.size(); i++){
            builder.append(frames.get(i).state);
        }
        builder.append("Input\n" + input.toString()).append("Output\n" + output.toString());
        return builder.toString();
    }

    public boolean isInputValue(Value value){
        return input.contains(value);
    }

    public Sec<?> getInputSecLevel(Value value){
        assert isInputValue(value);
        return input.getSec(value);
    }

    public Sec<?> getInputSecLevel(Bit bit){
        return input.getSec(bit);
    }

    /**
     * Walk in pre order
     * @param ignoreBit ignore bits (and all that depend on it, if not reached otherwise)
     */
    public void walkBits(Consumer<Bit> consumer, Predicate<Bit> ignoreBit){
        Set<Bit> alreadyVisitedBits = new HashSet<>();
        for (Pair<Sec, Bit> secBitPair : output.getBits()){
            BitLattice.get().walkBits(secBitPair.second, consumer, ignoreBit, alreadyVisitedBits);
        }
    }

    private Map<Sec<?>, MinCut.ComputationResult> leaks = null;


    public Map<Sec<?>, MinCut.ComputationResult> computeLeakage(MinCut.Algo algo){
        if (leaks == null){
            leaks = MinCut.compute(this, algo);
        }
        return leaks;
    }

    public Set<MJNode> nodes(){
        return frame.nodeValueState.nodeValueMap.keySet();
    }

    public List<String> variableNames(){
        List<String> variables = new ArrayList<>();
        for (int i = frames.size() - 1; i >= 0; i--){
            variables.addAll(frames.get(i).state.variableNames());
        }
        return variables;
    }

    public void initModsForBranch(Branch branch){
        if (inExtendedMode()) {
            Bit condBit = nodeValue(branch.condition).get(1);
            ModsCreator modsCreator = repl(condBit);
            Mods newMods = modsCreator.apply(this, condBit, bl.create(branch.val ? B.ONE : B.ZERO));
            if (frame.nodeValueState.modsMap.containsKey(branch)) {
                frame.nodeValueState.modsMap.put(branch, Mods.empty().add(frame.nodeValueState.modsMap.get(branch)).union(newMods));
            } else {
                frame.nodeValueState.modsMap.put(branch, newMods);
            }
        }
    }

    public void pushBranch(Branch branch){
        Optional<Branch> parent =
                frame.nodeValueState.branchStack.isEmpty() ? Optional.empty() : Optional.of(frame.nodeValueState.branchStack.peek());
        frame.nodeValueState.branchStack.push(branch);
        frame.nodeValueState.parentBranch.put(branch, parent);
    }

    public void popBranch(){
        frame.nodeValueState.branchStack.pop();
    }

    public void pushMiscMods(Mods mods) {
        additionalMods.push(mods);
    }

    public void popMiscMods() {
        additionalMods.pop();
    }

    public void assertAdditionalModsEmpty() {
        assert additionalMods.empty();
    }

    /**
     * In extended or later mode?
     */
    boolean inExtendedMode(){
        return mode.ordinal() >= Mode.EXTENDED.ordinal();
    }

    boolean inLoopMode(){
        return mode.ordinal() >= Mode.LOOP.ordinal();
    }

    public boolean inIntervalMode(){
        return mode.ordinal() >= Mode.INTERVAL.ordinal();
    }

    public Context mode(Mode mode){
        assert this.mode == null;
        this.mode = mode;
        return this;
    }

    /* -------------------------- extended mode specific -------------------------------*/

    public Bit replace(Bit bit, @Nullable Branch branch){
        if (inExtendedMode()) {
            Bit ret = null;
            for (Mods additionalMod : additionalMods) {
                if (additionalMod.definedFor(bit)) {
                    ret = additionalMod.replace(bit);
                }
            }
            if (ret != null) {
                return ret;
            }
            Optional<Branch> optCur = Optional.ofNullable(branch);
            while (optCur.isPresent()){
                Mods curMods = frame.nodeValueState.modsMap.get(optCur.get());
                if (curMods.definedFor(bit)){
                    return curMods.replace(bit);
                }
                optCur = frame.nodeValueState.parentBranch.get(optCur.get());
            }
        }
        return bit;
    }

    public Interval replace(Interval inter, @Nullable Branch branch){
        if (inExtendedMode()) {
            Interval ret = null;
            for (Mods additionalMod : additionalMods) {
                if (additionalMod.definedFor(inter)) {
                    ret = additionalMod.replace(inter);
                }
            }
            if (ret != null) {
                return ret;
            }
            Optional<Branch> optCur = Optional.ofNullable(branch);
            while (optCur.isPresent()){
                Mods curMods = frame.nodeValueState.modsMap.get(optCur.get());
                if (curMods.definedFor(inter)){
                    return curMods.replace(inter);
                }
                optCur = frame.nodeValueState.parentBranch.get(optCur.get());
            }
        }
        return inter;
    }

    public Value replace(Value value) {
        if (frame.nodeValueState.branchStack.isEmpty()){
            if (!additionalMods.empty()) {
                return replace(value, null);
            }
            return value;
        }
        return replace(value, frame.nodeValueState.branchStack.peek());
    }

    public Value replace(Value value, @Nullable Branch branch) {
        Util.Box<Boolean> replacedABit = new Util.Box<>(false);
        Value newValue = value.stream().map(b -> {
            Bit r = replace(b, branch);
            if (r != b) {
                replacedABit.val = true;
            }
            return r;
        }).collect(Value.collector());
        if (value.hasInterval()) {
            Interval inter = replace(value.interval, branch);
            newValue.setInterval(inter);
            replacedABit.val = replacedABit.val || inter != value.interval;
        }
        if (replacedABit.val) {
            newValue.bits.forEach(b -> b.value(newValue));
            return newValue;
        }
        return value;
    }

    public void repl(Bit bit, ModsCreator modsCreator){
        replMap.put(bit, modsCreator);
    }

    /**
     * Applies the repl function to get mods
     * @param bit
     * @param assumed
     */
    public Mods repl(Bit bit, Bit assumed){
        return repl(bit).apply(this, bit, assumed);
    }

    public ModsCreator repl(Bit bit){
        return replMap.get(bit);
    }

    private int c1(Bit bit){
        if (!frame.callPath.isEmpty() && frame.methodParameterBits.contains(bit)){
            return 1;
        }
        if (isInputBit(bit) && sec(bit) != sl.bot()){
            return 1;
        }
        if (isInputBit(bit) && sec(bit) != sl.bot()){
            return 1;
        }
        Queue<Bit> q = new ArrayDeque<>();
        Set<Bit> alreadyVisitedBits = new HashSet<>();
        q.add(bit);
        Set<Bit> anchors = new HashSet<>();
        while (!q.isEmpty()) {
            Bit cur = q.poll();
            if ((!frame.callPath.isEmpty() && frame.methodParameterBits.contains(cur)) ||
                    isInputBit(cur) && sec(cur) != sl.bot()) {
                anchors.add(cur);
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
        return anchors.size();
    }

    private Bit createChooseWrapBit(Bit chosen, Bit notChoosen) {
        assert !chosen.isConstant();
        Bit b = bl.create(chosen.val(), ds.create(chosen, notChoosen));
        alternativeBits.add(b);
        return b;
    }

    public Bit choose(Bit a, Bit b) {
        int ac = c1(a);
        int bc = c1(b);
        if (a.isConstant() || ac <= bc) {
            if (recordAlternatives && !a.isConstant() && ac != 0) {
                return createChooseWrapBit(a, b);
            }
            return a;
        }
        if (recordAlternatives && !b.isConstant()) {
            return createChooseWrapBit(b, a);
        }
        return b;
    }

    public Bit notChosen(Bit a, Bit b) {
        if (b.isConstant() || c1(a) >= c1(b)) {
            return a;
        }
        return b;
    }

    /* -------------------------- loop mode specific -------------------------------*/

    public double weight(Bit bit) {
        double weight = weightMap.getOrDefault(bit, 1d);
        if (bit.val() == bs.N) {
            weight = Math.max(Util.log2(3), weight);
        }
        if (bit.val() == bs.S) {
            weight = INFTY;
        }
        return weight;
    }

    public void weight(Bit bit, double weight){
        if (weight == 1){
            weightMap.remove(bit, weight);
            return;
        }
        weightMap.put(bit, weight);
    }

    public boolean hasInfiniteWeight(Bit bit){
        return weight(bit) == INFTY;
    }

    /**
     * merges n into o
     * @param o
     * @param n
     * @return true if o value equals the merge result
     */
    public boolean merge(Bit o, Bit n){
        B vt = bs.sup(v(o), v(n));
        int oldDepsCount = o.deps().size();
        boolean somethingChanged = false;
        if (vt != v(o)) {
            o.setVal(vt);
            somethingChanged = true;
        }
        o.addDependencies(d(n));
        if (oldDepsCount == o.deps().size() && !somethingChanged){
            replMap.remove(n);
            return false;
        }
        ModsCreator oModsCreator = repl(o);
        ModsCreator nModsCreator = repl(n);
        repl(o, (c, b, a) -> {
            Mods oMods = oModsCreator.apply(c, b, a);
            Mods nMods = nModsCreator.apply(c, b, a);
            return Mods.empty().add(oMods).union(nMods);
        });
        replMap.remove(n);
        return true;
    }

    public void setReturnValue(Value value){
        frames.get(frames.size() - 1).state.setReturnValue(value);
    }

    public Value getReturnValue(){
        return frames.get(frames.size() - 1).state.getReturnValue();
    }

    public long getNodeVersionUpdateCount(){
        return frame.nodeValueState.nodeVersionUpdateCount;
    }

    public long getNodeVersionWOAppendValuedUpdateCount(){
        return frame.nodeValueState.nodeVersionWOAppendValuedUpdateCount;
    }


    /*-------------------------- methods -------------------------------*/

    public Context forceMethodInvocationHandler(MethodInvocationHandler handler) {
        methodInvocationHandler = handler;
        return this;
    }

    public void methodInvocationHandler(MethodInvocationHandler handler) {
        assert methodInvocationHandler == null;
        methodInvocationHandler = handler;
    }

    public MethodInvocationHandler methodInvocationHandler(){
        if (methodInvocationHandler == null){
            methodInvocationHandler(MethodInvocationHandler.createDefault());
        }
        return methodInvocationHandler;
    }

    public void pushNewFrame(MethodInvocationNode callSite, List<Value> arguments){
        pushNewFrame(callSite, arguments.stream().flatMap(Value::stream).collect(Collectors.toSet()));
    }

    public void pushNewFrame(MethodInvocationNode callSite, Set<Bit> argumentBits){
        frames.push(new Frame(frame.callPath.push(callSite), argumentBits));
        frame = frames.peek();
    }

    public void popFrame(){
        frames.pop();
        frame = frames.peek();
    }

    public InputBits getNewlyIntroducedInputs(){
        return frame.inputBits;
    }

    public CallPath callPath(){
        return frame.callPath;
    }

    public int numberOfMethodFrames() {
        return frames.size();
    }

    public int numberOfinfiniteWeightNodes() {
        return weightMap.size();
    }

    public boolean isAlternativeBit(Bit bit) {
        return recordAlternatives && alternativeBits.contains(bit);
    }

    public void withoutAlternativeRecording(Consumer<Context> consumer) {
        boolean prev = recordAlternatives;
        recordAlternatives = false;
        consumer.accept(this);
        recordAlternatives = prev;
    }

    public Context setRecordAlternatives(boolean recordAlternatives) {
        this.recordAlternatives = recordAlternatives;
        return this;
    }

    public boolean recordsAlternatives() {
        return recordAlternatives;
    }

    public boolean hasAlternatives() {
        return recordAlternatives && alternativeBits.size() > 0;
    }

    public void resetFrames(State.OutputState outputState) {
        frames.clear();
        frames.push(new Frame(new CallPath(), new HashSet<>(), new State(outputState)));
        frame = frames.peek();
    }

    public void resetFrames() {
        resetFrames(frame != null ? frame.state.outputState : new State.OutputState());
    }

    public Set<Bit> sources(Sec<?> sec){
        return  sl
                .elements()
                .stream()
                .map(s -> (Sec<?>) s)
                .filter(s -> ((Lattice) sl).lowerEqualsThan(s, sec))
                .flatMap(s -> Stream.concat(output.getBits((Sec) s).stream(),
                        frame.state.outputState.getBits((Sec) s).stream()))
                .collect(Collectors.toSet());
    }

    public Set<Bit> sinks(Sec<?> sec){
        // an attacker at level sec can see all outputs with level <= sec
        return   sl
                .elements()
                .stream()
                .map(s -> (Sec<?>) s)
                .filter(s -> !((Lattice) sl).lowerEqualsThan(s, sec))
                .flatMap(s -> Stream.concat(input.getBits((Sec) s).stream(), getNewlyIntroducedInputs().get(s).stream()))
                .collect(Collectors.toSet());
    }

    public SourcesAndSinks sourcesAndSinks(Sec<?> sec){
        return new SourcesAndSinks(entropyBounds.getMaxOutputs(sec),
                sources(sec), entropyBounds.getMaxInputEntropy(sec), sinks(sec), this);
    }

    public static class SourcesAndSinks {

        final double sourceWeight;
        public final Set<Bit> sources;
        final double sinkWeight;
        public final Set<Bit> sinks;
        public final Context context;

        public SourcesAndSinks(double sourceWeight, Set<Bit> sources, double sinkWeight, Set<Bit> sinks, Context context){
            this.sourceWeight = sourceWeight;
            this.sources = sources;
            this.sinkWeight = sinkWeight;
            this.sinks = sinks;
            this.context = context;
        }
    }
}
