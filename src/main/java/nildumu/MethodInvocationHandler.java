package nildumu;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;
import java.util.stream.*;

import io.github.livingdocumentation.dotdiagram.DotGraph;
import swp.util.Pair;

import static nildumu.CallGraph.CallNode;
import static nildumu.Context.*;
import static nildumu.Lattices.B.U;
import static nildumu.Lattices.*;
import static nildumu.Parser.*;
import static nildumu.Util.p;

/**
 * Handles the analysis of methods → implements the interprocedural part of the analysis.
 *
 * Handler classes can be registered and configured via property strings.
 */
public abstract class MethodInvocationHandler {

    private static Map<String, Pair<PropertyScheme, Function<Properties, MethodInvocationHandler>>> registry = new HashMap<>();

    private static List<String> examplePropLines = new ArrayList<>();

    /**
     * Regsiter a new class of handlers
     */
    private static void register(String name, Consumer<PropertyScheme> propSchemeCreator, Function<Properties, MethodInvocationHandler> creator){
        PropertyScheme scheme = new PropertyScheme();
        propSchemeCreator.accept(scheme);
        scheme.add("handler", null);
        registry.put(name, p(scheme, creator));
    }

    /**
     * Returns the handler for the passed string, the property "handler" defines the handler class
     * to be used
     */
    public static MethodInvocationHandler parse(String props){
        Properties properties = new PropertyScheme().add("handler").parse(props, true);
        String handlerName = properties.getProperty("handler");
        if (!registry.containsKey(handlerName)){
            throw new MethodInvocationHandlerInitializationError(String.format("unknown handler %s, possible handlers are: %s", handlerName, registry.keySet()));
        }
        try {
            Pair<PropertyScheme, Function<Properties, MethodInvocationHandler>> pair = registry.get(handlerName);
            return pair.second.apply(pair.first.parse(props));
        } catch (MethodInvocationHandlerInitializationError error){
            throw error;
        } catch (Error error){
            throw new MethodInvocationHandlerInitializationError(String.format("parsing \"%s\": %s", props, error.getMessage()));
        }
    }

    public static List<String> getExamplePropLines(){
        return Collections.unmodifiableList(examplePropLines);
    }

    public static class MethodInvocationHandlerInitializationError extends NildumuError {

        public MethodInvocationHandlerInitializationError(String message) {
            super("Error initializing the method invocation handler: " + message);
        }
    }

    /**
     * A basic scheme that defines the properties (with their possible default values) for each
     * handler class
     */
    public static class PropertyScheme {
        final char SEPARATOR = ';';
        private final Map<String, String> defaultValues;

        PropertyScheme() {
            defaultValues = new HashMap<>();
        }

        public PropertyScheme add(String param, String defaultValue){
            defaultValues.put(param, defaultValue);
            return this;
        }

        public PropertyScheme add(String param){
            return add(param, null);
        }

        public Properties parse(String props){
            return parse(props, false);
        }

        public Properties parse(String props, boolean allowAnyProps){
            if (!props.contains("=")){
                props = String.format("handler=%s", props);
            }
            Properties properties = new Properties();
            try {
                properties.load(new StringReader(props.replace(SEPARATOR, '\n')));
            } catch (IOException e) {
                e.printStackTrace();
                throw new MethodInvocationHandlerInitializationError(String.format("for string \"%s\": %s", props, e.getMessage()));
            }
            for (Map.Entry<String, String> defaulValEntry : defaultValues.entrySet()) {
                if (!properties.containsKey(defaulValEntry.getKey())){
                    if (defaulValEntry.getValue() == null){
                        throw new MethodInvocationHandlerInitializationError(String.format("for string \"%s\": property %s not set", props, defaulValEntry.getKey()));
                    }
                    properties.setProperty(defaulValEntry.getKey(), defaulValEntry.getValue());
                }
            }
            if (!allowAnyProps) {
                for (String prop : properties.stringPropertyNames()) {
                    if (!defaultValues.containsKey(prop)) {
                        throw new MethodInvocationHandlerInitializationError(String.format("for string \"%s\": property %s unknown, valid properties are: %s", props, prop, defaultValues.keySet().stream().sorted().collect(Collectors.joining(", "))));
                    }
                }
            }
            return properties;
        }
    }

    /**
     * A call string based handler that just inlines a function.
     * If a function was inlined in the current call path more than a defined number of times,
     * then another handler is used to compute a conservative approximation.
     */
    public static class CallStringHandler extends MethodInvocationHandler {

        final int maxRec;

        final MethodInvocationHandler botHandler;

        private DefaultMap<Parser.MethodNode, Integer> methodCallCounter = new DefaultMap<>((map, method) -> 0);

        CallStringHandler(int maxRec, MethodInvocationHandler botHandler) {
            this.maxRec = maxRec;
            this.botHandler = botHandler;
        }

        @Override
        public void setup(ProgramNode program) {
            botHandler.setup(program);
        }

        @Override
        public Value analyze(Context c, MethodInvocationNode callSite, List<Value> arguments) {
            MethodNode method = callSite.definition;
            if (methodCallCounter.get(method) < maxRec) {
                methodCallCounter.put(method, methodCallCounter.get(method) + 1);
                c.pushNewMethodInvocationState(callSite, arguments);
                for (int i = 0; i < arguments.size(); i++) {
                    c.setVariableValue(method.parameters.get(i).definition, arguments.get(i));
                }
                Processor.process(c, method.body);
                Value ret = c.getReturnValue();
                c.popMethodInvocationState();
                methodCallCounter.put(method, methodCallCounter.get(method) - 1);
                return ret;
            }
            return botHandler.analyze(c, callSite, arguments);
        }
    }

    static class BitGraph {

        final Context context;
        final List<Value> parameters;
        private final Set<Bit> parameterBits;
        /**
         * bit → parameter number, index
         */
        private final Map<Bit, Pair<Integer, Integer>> bitInfo;

        final Value returnValue;

        final List<Integer> paramBitsPerReturnValue;

        BitGraph(Context context, List<Value> parameters, Value returnValue) {
            this.context = context;
            this.parameters = parameters;
            this.parameterBits = parameters.stream().flatMap(Value::stream).collect(Collectors.toSet());
            this.bitInfo = new HashMap<>();
            for (int i = 0; i < parameters.size(); i++) {
                Value param = parameters.get(i);
                for (int j = 1; j <= param.size(); j++) {
                    bitInfo.put(param.get(j), p(i, j));
                }
            }
            this.returnValue = returnValue;
            assertThatAllBitsAreNotNull();
            paramBitsPerReturnValue = returnValue.stream().map(b -> calcReachableParamBits(b).size()).collect(Collectors.toList());
        }

        private void assertThatAllBitsAreNotNull(){
            returnValue.forEach(b -> {
                assert b != null: "Return bits shouldn't be null";
            });
            vl.walkBits(Arrays.asList(returnValue), b -> {
                assert b != null: "Bits shouldn't be null";
            });
            vl.walkBits(parameters, b -> {
                assert b != null: "Parameters bits shouldn't null";
            });
        }

        public static Bit cloneBit(Context context, Bit bit, DependencySet deps){
            Bit clone;
            if (bit.isUnknown()) {
                clone = bl.create(U, deps);
            } else {
                clone = bl.create(v(bit));
            }
            context.repl(clone, context.repl(bit));
            return clone;
        }

        public Value applyToArgs(Context context, List<Value> arguments){
            List<Value> extendedArguments = arguments;
            Map<Bit, Bit> newBits = new HashMap<>();
            // populate
            vl.walkBits(returnValue, bit -> {
                if (parameterBits.contains(bit)){
                    Pair<Integer, Integer> loc = bitInfo.get(bit);
                    Bit argBit = extendedArguments.get(loc.first).get(loc.second);
                    newBits.put(bit, argBit);
                } else {
                    Bit clone = cloneBit(context, bit, d(bit));
                    clone.value(bit.value());
                    newBits.put(bit, clone);
                }
            });
            DefaultMap<Value, Value> newValues = new DefaultMap<Value, Value>((map, value) -> {
                if (parameters.contains(value)){
                    return arguments.get(parameters.indexOf(value));
                }
                Value clone = value.map(b -> {
                    if (!parameterBits.contains(b)) {
                        return newBits.get(b);
                    }
                    return b;
                });
                clone.node(value.node());
                return value;
            });
            // update dependencies
            newBits.forEach((old, b) -> {
                if (!parameterBits.contains(old)) {
                    b.alterDependencies(newBits::get);
                }
                //b.value(old.value());
            });
            return returnValue.map(newBits::get);
        }

        /**
         * Returns the bit of the passed set, that are reachable from the bit
         */
        public Set<Bit> calcReachableBits(Bit bit, Set<Bit> bits){
            Set<Bit> reachableBits = new HashSet<>();
            bl.walkBits(bit, b -> {
                if (bits.contains(b)){
                    reachableBits.add(b);
                }
            }, b -> false);
            return reachableBits;
        }

        public Set<Bit> calcReachableParamBits(Bit bit){
            return calcReachableBits(bit, parameterBits);
        }

        public Set<Bit> minCutBits(){
            return minCutBits(returnValue.bitSet(), parameterBits);
        }

        public Set<Bit> minCutBits(Set<Bit> outputBits, Set<Bit> inputBits){
            return MinCut.compute(outputBits, inputBits, context::weight).minCut;
        }

        public Set<Bit> minCutBits(Set<Bit> outputBits, Set<Bit> inputBits, int outputWeight){
            return MinCut.compute(outputBits, inputBits, b -> outputBits.contains(b) ? outputWeight : context.weight(b)).minCut;
        }

        public DotGraph createDotGraph(String name){
            DotGraph dotGraph = new DotGraph(name);
            DotGraph.Digraph g = dotGraph.getDigraph();
            createDotGraph(g.addCluster(name), name);
            return dotGraph;
        }

        private DotGraph.Cluster createDotGraph(DotGraph.Cluster g, String name){
            return createDotGraph(g, name, (b1, b2) -> "");
        }

        private DotGraph.Cluster createDotGraph(DotGraph.Cluster g, String name, BiFunction<Bit, Bit, String> edgeLabel){
            Set<Bit> alreadyVisited = new HashSet<>();
            Function<Bit, String> dotLabel = b -> (context.weight(b) == INFTY ? "∞" : context.weight(b)) + "|" + b.uniqueId() + ": " + b.toString().replace("|", "or");
            for (Bit bit : returnValue) {
                bl.walkBits(bit, b -> {
                    DotGraph.Node node = g.addNode(b.uniqueId());
                    node.setLabel(dotLabel.apply(b));
                    b.deps().forEach(d -> g.addAssociation(b.uniqueId(), d.uniqueId()).setLabel(edgeLabel.apply(d, d )));
                }, b -> false, alreadyVisited);
            }
            vl.walkBits(parameters, b -> {
                if (!alreadyVisited.contains(b)){
                    g.addNode(b.uniqueId());
                    alreadyVisited.add(b);
                }
            });
            g.addNode("return").setLabel("return");
            returnValue.forEach(b -> g.addAssociation("return", b.uniqueId()));
            g.addNode(name).setLabel(name);
            for (int i = 0; i < parameters.size(); i++) {
                Value param = parameters.get(i);
                String nodeId = String.format("param %d", i);
                g.addNode(nodeId).setLabel(String.format("param %d", i));
                param.forEach(b -> g.addAssociation(b.uniqueId(), nodeId));
                g.addAssociation(nodeId, name);
            }
            return g;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof BitGraph){
                //assert ((BitGraph)obj).parameterBits == this.parameterBits;
                return paramBitsPerReturnValue.equals(((BitGraph)obj).paramBitsPerReturnValue);
            }
            return false;
        }

        public void writeDotGraph(Path folder, String name){
            writeDotGraph(folder, name, (b1, b2) -> "");
        }

        public void writeDotGraph(Path folder, String name, BiFunction<Bit, Bit, String> edgeLabel){
            Path path = folder.resolve(name + ".dot");

            String graph = createDotGraph(name).render();
            try {
                Files.createDirectories(folder);
                Files.write(path, Arrays.asList(graph.split("\n")));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * A summary-edge based handler. It creates for each function beforehand summary edges:
     * these edges connect the parameter bits and the return bits. The analysis assumes that all
     * parameter bits might have a statically unknown value. The summary-edge analysis builds the
     * summary edges using a fix point iteration over the call graph. Each analysis of a method
     * runs the normal analysis of the method body and uses the prior summary edges if a method is
     * called in the body. The resulting bit graph is then reduced.
     * <p/>
     * It supports coinduction ("mode=coind") and induction ("mode=ind").
     * <p/>
     * Induction starts with no edges between parameter bits and return bits and iterates till no
     * new connection between a return bit and a parameter bit is added. It only works for programs
     * without recursion.
     * <p/>
     * Coinduction starts with the an over approximation produced by another handler ("bot" property)
     * and iterates at most a configurable number of times ("maxiter" property), by default this
     * number is {@value Integer#MAX_VALUE}.
     * <p/>
     * The default reduction policy is to connect all return bits with all parameter bits that they
     * depend upon ("reduction=basic").
     * And improved version ("reduction=mincut") includes the minimal cut bits of the bit graph from
     * the return to the parameter bits, assuming that the return bits have infinite weights.
     */
    public static class SummaryHandler extends MethodInvocationHandler {

        public static enum Mode {
            COINDUCTION,
            /**
             * The induction mode doesn't work with recursion and has spurious errors
             */
            INDUCTION
        }

        public static enum Reduction {
            BASIC,
            MINCUT;
        }

        final int maxIterations;

        final Mode mode;

        final MethodInvocationHandler botHandler;

        final Path dotFolder;

        final Reduction reductionMode;

        Map<MethodNode, BitGraph> methodGraphs;

        CallGraph callGraph;

        public SummaryHandler(int maxIterations, Mode mode, MethodInvocationHandler botHandler, Path dotFolder, Reduction reductionMode) {
            this.maxIterations = maxIterations;
            this.mode = mode;
            this.reductionMode = reductionMode;
            assert !(mode == Mode.INDUCTION) || (maxIterations == Integer.MAX_VALUE);
            this.botHandler = botHandler;
            this.dotFolder = dotFolder;
        }

        @Override
        public void setup(ProgramNode program) {
            callGraph = new CallGraph(program);
            if (dotFolder != null){
                callGraph.writeDotGraph(dotFolder, "call_graph");
            }
            if (callGraph.containsRecursion() && mode == Mode.INDUCTION){
                throw new MethodInvocationHandlerInitializationError("Induction cannot be used for programs with reduction");
            }
            Context c = program.context;
            Map<MethodNode, MethodInvocationNode> callSites = new DefaultMap<>((map, method) ->{
                MethodInvocationNode callSite = new MethodInvocationNode(method.location, method.name, null);
                callSite.definition = method;
                return callSite;
            });
            Map<CallNode, BitGraph> state = new HashMap<>();
            MethodInvocationHandler handler = createHandler(m -> state.get(callGraph.callNode(m)));
            Map<CallNode, Integer> nodeEvaluationCount = new HashMap<>();
            Util.Box<Integer> iteration = new Util.Box<>(0);
            methodGraphs = callGraph.worklist((node, s) -> {
                if (node.isMainNode || nodeEvaluationCount.getOrDefault(node, 0) > maxIterations){
                    return s.get(node);
                }
                nodeEvaluationCount.put(node, nodeEvaluationCount.getOrDefault(node, 0));
                iteration.val += 1;
                BitGraph graph = methodIteration(program.context, callSites.get(node.method), handler, s.get(node).parameters);
                if (dotFolder != null) {
                    graph.writeDotGraph(dotFolder, iteration.val + "_" + node.method.name);
                }
                BitGraph reducedGraph = reduce(c, graph);
                if (dotFolder != null) {
                    reducedGraph.writeDotGraph(dotFolder, "r" + iteration.val + "_" + node.method.name);
                }
                return reducedGraph;
            }, node ->  {
                BitGraph graph = bot(program, node.method, callSites);
                        if (dotFolder != null) {
                            graph.writeDotGraph(dotFolder, iteration.val + "_" + node.method.name);
                        }
                        return graph;
                    }
            , node -> node.getCallers().stream().filter(n -> !n.isMainNode).collect(Collectors.toSet()),
            state).entrySet().stream().collect(Collectors.toMap(e -> e.getKey().method, Map.Entry::getValue));
        }

        BitGraph bot(ProgramNode program, MethodNode method, Map<MethodNode, MethodInvocationNode> callSites){
            List<Value> parameters = generateParameters(program, method);
            if (mode == Mode.COINDUCTION) {
                Value returnValue = botHandler.analyze(program.context, callSites.get(method), parameters);
                return new BitGraph(program.context, parameters, returnValue);
            }
            return new BitGraph(program.context, parameters, createUnknownValue(program));
        }

        List<Value> generateParameters(ProgramNode program, MethodNode method){
            return method.parameters.parameterNodes.stream().map(p ->
                createUnknownValue(program)
            ).collect(Collectors.toList());
        }

        Value createUnknownValue(ProgramNode program){
            return IntStream.range(0, program.context.maxBitWidth).mapToObj(i -> bl.create(U)).collect(Value.collector());
        }

        BitGraph methodIteration(Context c, MethodInvocationNode callSite, MethodInvocationHandler handler, List<Value> parameters){
            c.pushNewMethodInvocationState(callSite, parameters.stream().flatMap(Value::stream).collect(Collectors.toSet()));
            for (int i = 0; i < parameters.size(); i++) {
                c.setVariableValue(callSite.definition.parameters.get(i).definition, parameters.get(i));
            }
            c.forceMethodInvocationHandler(handler);
            Processor.process(c, callSite.definition.body);
            Value ret = c.getReturnValue();
            c.popMethodInvocationState();
            return new BitGraph(c, parameters, ret);
        }

        MethodInvocationHandler createHandler(Function<MethodNode, BitGraph> curVersion){
            return new MethodInvocationHandler() {
                @Override
                public Value analyze(Context c, MethodInvocationNode callSite, List<Value> arguments) {
                    return curVersion.apply(callSite.definition).applyToArgs(c, arguments);
                }
            };
        }

        BitGraph reduce(Context context, BitGraph bitGraph){
            switch (reductionMode) {
                case BASIC:
                    return basicReduce(context, bitGraph);
                case MINCUT:
                    return minCutReduce(context, bitGraph);
            }
            return null;
        }

        /**
         * basic implementation, just connects a result bit with all reachable parameter bits
         */
        BitGraph basicReduce(Context context, BitGraph bitGraph){
            DefaultMap<Bit, Bit> newBits = new DefaultMap<Bit, Bit>((map, bit) -> {
                return BitGraph.cloneBit(context, bit, ds.create(bitGraph.calcReachableParamBits(bit)));
            });
            Value ret = bitGraph.returnValue.map(newBits::get);
            ret.node(bitGraph.returnValue.node());
            return new BitGraph(context, bitGraph.parameters, ret);
        }

        BitGraph minCutReduce(Context context, BitGraph bitGraph) {
            Set<Bit> anchorBits = new HashSet<>(bitGraph.parameterBits);
            Set<Bit> minCutBits = bitGraph.minCutBits(bitGraph.returnValue.bitSet(), bitGraph.parameterBits, INFTY);
            anchorBits.addAll(minCutBits);
            Map<Bit, Bit> newBits = new HashMap<>();
            // create the new bits
            Stream.concat(bitGraph.returnValue.stream(), minCutBits.stream()).forEach(b -> {
                Set<Bit> reachable = bitGraph.calcReachableBits(b, anchorBits);
                if (!b.deps().contains(b)){
                    reachable.remove(b);
                }
                Bit newB = BitGraph.cloneBit(context, b, ds.create(reachable));
                newB.value(b.value());
                newBits.put(b, newB);
            });
            bitGraph.parameterBits.forEach(b -> newBits.put(b, b));
            // update the control dependencies
            newBits.forEach((o, b) -> {
                b.alterDependencies(newBits::get);
            });
            Value ret = bitGraph.returnValue.map(newBits::get);
            ret.node(bitGraph.returnValue.node());
            return new BitGraph(context, bitGraph.parameters, ret);
        }

        @Override
        public Value analyze(Context c, MethodInvocationNode callSite, List<Value> arguments) {
            return methodGraphs.get(callSite.definition).applyToArgs(c, arguments);
        }
    }

    static {
        register("basic", s -> {}, ps -> new MethodInvocationHandler(){
            @Override
            public Value analyze(Context c, MethodInvocationNode callSite, List<Value> arguments) {
                if (arguments.isEmpty() || !callSite.definition.hasReturnValue()){
                    return vl.bot();
                }
                DependencySet set = arguments.stream().flatMap(Value::stream).collect(DependencySet.collector());
                return IntStream.range(0, arguments.stream().mapToInt(Value::size).max().getAsInt()).mapToObj(i -> bl.create(U, set)).collect(Value.collector());
            }
        });
        examplePropLines.add("handler=basic");
        register("call_string", s -> s.add("maxrec", "1").add("bot", "basic"), ps -> {
            return new CallStringHandler(Integer.parseInt(ps.getProperty("maxrec")), parse(ps.getProperty("bot")));
        });
        examplePropLines.add("handler=call_string;maxrec=2;bot=basic");
        Consumer<PropertyScheme> propSchemeCreator = s ->
                s.add("maxiter", "1")
                        .add("bot", "basic")
                        .add("dot", "")
                        .add("mode", "coind")
                        .add("reduction", "mincut");
        register("summary", propSchemeCreator, ps -> {
            Path dotFolder = ps.getProperty("dot").equals("") ? null : Paths.get(ps.getProperty("dot"));
            return new SummaryHandler(ps.getProperty("mode").equals("coind") ? Integer.parseInt(ps.getProperty("maxiter")) : Integer.MAX_VALUE,
                    ps.getProperty("mode").equals("ind") ? SummaryHandler.Mode.INDUCTION : SummaryHandler.Mode.COINDUCTION,
                    parse(ps.getProperty("bot")), dotFolder, SummaryHandler.Reduction.valueOf(ps.getProperty("reduction").toUpperCase()));
        });
        examplePropLines.add("handler=summary;maxiter=2;bot=basic;reduction=basic");
        examplePropLines.add("handler=summary;maxiter=2;bot=basic;reduction=mincut");
        //examplePropLines.add("handler=summary_mc;mode=ind");
    }

    public static MethodInvocationHandler createDefault(){
        return parse(getDefaultPropString());
    }

    public static String getDefaultPropString(){
        return "handler=call_string;maxrec=2;bot=basic";
    }

    public void setup(ProgramNode program){
    }

    public abstract Lattices.Value analyze(Context c, MethodInvocationNode callSite, List<Value> arguments);
}
