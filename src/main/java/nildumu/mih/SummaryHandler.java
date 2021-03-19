package nildumu.mih;

import nildumu.*;
import nildumu.util.DefaultMap;
import nildumu.util.Util;

import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static nildumu.Context.INFTY;
import static nildumu.Context.log;
import static nildumu.Lattices.B.U;
import static nildumu.Lattices.bl;
import static nildumu.Lattices.ds;

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
public class SummaryHandler extends MethodInvocationHandler {

    public enum Mode {
        COINDUCTION,
        /**
         * The induction mode doesn't work with recursion and has spurious errors
         */
        INDUCTION,
        AUTO
    }

    final int maxIterations;

    final Mode mode;

    final MethodInvocationHandler botHandler;

    final Path dotFolder;

    final Reduction reductionMode;

    final int callStringMaxRec;

    Map<Parser.MethodNode, BitGraph> methodGraphs;

    CallGraph callGraph;

    public SummaryHandler(int maxIterations, Mode mode, MethodInvocationHandler botHandler, Path dotFolder, Reduction reductionMode, int callStringMaxRec) {
        this.maxIterations = maxIterations;
        this.mode = mode;
        this.reductionMode = reductionMode;
        this.callStringMaxRec = callStringMaxRec;
        assert !(mode == Mode.INDUCTION || mode == Mode.AUTO) || (maxIterations == Integer.MAX_VALUE);
        this.botHandler = botHandler;
        this.dotFolder = dotFolder;
    }

    @Override
    public void setup(Parser.ProgramNode program) {
        Context.log(() -> "Setup summary handler");
        Mode _mode = mode;
        callGraph = new CallGraph(program);
        if (_mode == Mode.AUTO) {
            _mode = Mode.INDUCTION;
        }
        List<CallGraph.CallNode> nodes = new ArrayList<>();
        Mode usedMode = _mode;
        Context c = program.context;
        Map<Parser.MethodNode, Parser.MethodInvocationNode> callSites = new DefaultMap<>((map, method) -> {
            Parser.MethodInvocationNode callSite = new Parser.MethodInvocationNode(method.location, method.name, null, null);
            callSite.definition = method;
            return callSite;
        });
        Map<CallGraph.CallNode, PrintHistory.ReduceResult<BitGraph>> state = new HashMap<>();
        // bitGraph.parameters do not change
        MethodInvocationHandler handler = createHandler(m -> state.get(callGraph.callNode(m)).value);
        Util.Box<Integer> iteration = new Util.Box<>(0);
        Map<CallGraph.CallNode, PrintHistory.HistoryEntry> history = new HashMap<>();
        c.withoutAlternativeRecording(con -> {
            methodGraphs = callGraph.worklist((node, s) -> {
                        if (node.isMainNode || iteration.val > maxIterations) {
                            return s.get(node);
                        }
                        nodes.add(node);
                        log(() -> String.format("Setup: Analyse %s", node.method.name));
                        iteration.val += 1;
                        BitGraph graph = methodIteration(program.context, callSites.get(node.method), handler, s.get(node).value.parameters);
                        String name = String.format("%3d %s", iteration.val, node.method.name);
                        if (dotFolder != null) {
                            graph.writeDotGraph(dotFolder, name, true);
                        }
                        DotRegistry.get().store("summary", name,
                                () -> () -> graph.createDotGraph("", true));
                        BitGraph reducedGraph = reduce(c, graph);
                        PrintHistory.HistoryEntry newHist = PrintHistory.HistoryEntry.create(reducedGraph, history.containsKey(node) ? Optional.of(history.get(node)) : Optional.empty());
                        PrintHistory.ReduceResult<BitGraph> furtherReducedGraph = reduceGlobals(node, reducedGraph, newHist, c);
                        history.put(node, PrintHistory.HistoryEntry.create(furtherReducedGraph.value, newHist.prev));
                        if (dotFolder != null) {
                            graph.writeDotGraph(dotFolder, name + " [reduced]", false);
                        }
                        DotRegistry.get().store("summary", name + " [reduced]",
                                () -> () -> furtherReducedGraph.value.createDotGraph("", false));
                        return furtherReducedGraph;
                    }, node -> {
                        BitGraph graph = bot(program, node.method, callSites, usedMode);
                        String name = String.format("%3d %s", iteration.val, node.method.name);
                        if (dotFolder != null) {
                            graph.writeDotGraph(dotFolder, name, false);
                        }
                        DotRegistry.get().store("summary", name,
                                () -> () -> graph.createDotGraph("", false));
                        return new PrintHistory.ReduceResult<>(graph);
                    }
                    , node -> node.getCallers().stream().filter(n -> !n.isMainNode).collect(Collectors.toSet()),
                    state, (f, s) -> {
                        return !s.addedAStarBit && !f.value.equals(s.value);
                    }).entrySet().stream().collect(Collectors.toMap(e -> e.getKey().method, e -> e.getValue().value));
        });
        Context.log(() -> "Finish setup");
    }

    /**
     * Reduce the append only globals
     *
     * @param node
     * @param reducedGraph
     * @param history      history, including the one to be analysed (the current)
     * @return
     */
    private PrintHistory.ReduceResult<BitGraph> reduceGlobals(CallGraph.CallNode node, BitGraph reducedGraph, PrintHistory.HistoryEntry history, Context c) {
        PrintHistory.ReduceResult<Map<Variable, Lattices.AppendOnlyValue>> result =
                PrintHistory.ReduceResult.create(reducedGraph.methodReturnValue.globals.keySet().stream()
                        .collect(Collectors.toMap(v -> v, v -> history.map.get(v).reduceAppendOnly(c::weight))));
        return new PrintHistory.ReduceResult<>(new BitGraph(reducedGraph.context, reducedGraph.parameters,
                new MethodReturnValue(reducedGraph.methodReturnValue.values, result.value, reducedGraph.inputBits), node.method, reducedGraph.inputBits),
                result.addedAStarBit, result.somethingChanged);
    }


    BitGraph bot(Parser.ProgramNode program, Parser.MethodNode method, Map<Parser.MethodNode, Parser.MethodInvocationNode> callSites, Mode usedMode) {
        List<Lattices.Value> parameters = generateParameters(program, method);
        if (usedMode == Mode.COINDUCTION) {
            MethodReturnValue returnValue = botHandler.analyze(program.context, callSites.get(method), parameters, new HashMap<>());
            return new BitGraph(program.context, parameters, returnValue, method, new InputBits());
        }
        // TODO: problem with input bits?
        return new BitGraph(program.context, parameters, new MethodReturnValue(createUnknownValue(program, method.getNumberOfReturnValues()),
                new HashMap<>(), new InputBits()), method, new InputBits());
    }

    List<Lattices.Value> generateParameters(Parser.ProgramNode program, Parser.MethodNode method) {
        return method.parameters.parameterNodes.stream().map(p ->
                createUnknownValue(program)
        ).collect(Collectors.toList());
    }

    Lattices.Value createUnknownValue(Parser.ProgramNode program) {
        return IntStream.range(0, program.context.maxBitWidth).mapToObj(i -> bl.create(U)).collect(Lattices.Value.collector());
    }

    List<Lattices.Value> createUnknownValue(Parser.ProgramNode program, int count) {
        return Collections.nCopies(count,
                IntStream.range(0, program.context.maxBitWidth).mapToObj(i -> bl.create(U)).collect(Lattices.Value.collector()));
    }

    BitGraph methodIteration(Context c, Parser.MethodInvocationNode callSite, MethodInvocationHandler handler, List<Lattices.Value> parameters) {
        System.out.println("iterate " + callSite.method);
        c.resetFrames();
        c.pushNewFrame(callSite, parameters.stream().flatMap(Lattices.Value::stream).collect(Collectors.toSet()));
        for (int i = 0; i < parameters.size(); i++) {
            c.setVariableValue(callSite.definition.parameters.get(i).definition, parameters.get(i));
        }
        c.forceMethodInvocationHandler(handler);
        Processor.process(c, callSite.definition.body);
        List<Lattices.Value> ret = c.getReturnValue().split(callSite.definition.getNumberOfReturnValues());
        Map<Variable, Lattices.AppendOnlyValue> globalVals = callSite.definition.globalDefs.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                e -> c.getVariableValue(e.getValue().second).asAppendOnly()));
        InputBits inputBits = c.getNewlyIntroducedInputs();
        c.popFrame();
        c.forceMethodInvocationHandler(this);
        MethodReturnValue retValue = new MethodReturnValue(ret, globalVals, inputBits);
        return new BitGraph(c, parameters, retValue, callSite.definition, inputBits);
    }

    MethodInvocationHandler createHandler(Function<Parser.MethodNode, BitGraph> curVersion) {
        MethodInvocationHandler handler = new MethodInvocationHandler() {
            @Override
            public MethodReturnValue analyze(Context c, Parser.MethodInvocationNode callSite, List<Lattices.Value> arguments, Map<Variable, Lattices.AppendOnlyValue> globals) {
                return curVersion.apply(callSite.definition).applyToArgs(c, arguments, globals);
            }
        };
        if (callStringMaxRec > 0) {
            return new InliningHandler(callStringMaxRec, handler);
        }
        return handler;
    }

    BitGraph reduce(Context context, BitGraph bitGraph) {
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
    BitGraph basicReduce(Context context, BitGraph bitGraph) {
        DefaultMap<Lattices.Bit, Lattices.Bit> newBits = new DefaultMap<Lattices.Bit, Lattices.Bit>((map, bit) -> {
            return cloneBit(context, bit, ds.create(bitGraph.calcReachableInputAndParameterBits(bit)).map(map::get));
        });
        bitGraph.parameterBits.forEach(b -> newBits.put(b, b));
        MethodReturnValue newRetValue = bitGraph.methodReturnValue.map(newBits::get);
        Util.zip(newRetValue.values, bitGraph.returnValues, (v1, v2) -> v1.node(v2.node()));
        return new BitGraph(context, bitGraph.parameters, newRetValue, bitGraph.methodNode, bitGraph.inputBits.map(newBits::get));
    }

    BitGraph minCutReduce(Context context, BitGraph bitGraph) {
        Set<Lattices.Bit> anchorBits = new HashSet<>(bitGraph.parameterBits);
        Set<Lattices.Bit> inputBits = bitGraph.inputBits.getBits();
        anchorBits.addAll(inputBits);
        Set<Lattices.Bit> minCutBits = bitGraph.minCutBits(bitGraph.methodReturnValue.getCombinedValue().bitSet(),
                anchorBits, INFTY);
        anchorBits.addAll(minCutBits);
        Map<Lattices.Bit, Lattices.Bit> newBits = new HashMap<>();
        // create the new bits
        Stream.concat(Stream.concat(bitGraph.inputBits.getBits().stream(), bitGraph.methodReturnValue.getCombinedValue().stream()), minCutBits.stream()).forEach(b -> {
            Set<Lattices.Bit> reachable = Lattices.BitLattice.calcReachableBits(b, anchorBits);
            if (!b.deps().contains(b)) {
                reachable.remove(b);
            }
            Lattices.Bit newB = cloneBit(context, b, ds.create(reachable));
            newB.value(b.value());
            newBits.put(b, newB);
        });
        bitGraph.parameterBits.forEach(b -> newBits.put(b, b));
        // update the control dependencies
        newBits.forEach((o, b) -> {
            b.alterDependencies(newBits::get);
        });
        MethodReturnValue ret = bitGraph.methodReturnValue.map(newBits::get);
        Util.zip(ret.values, bitGraph.returnValues, (v1, v2) -> v1.node(v2.node()));
        BitGraph newGraph = new BitGraph(context, bitGraph.parameters, ret, bitGraph.methodNode, bitGraph.inputBits.map(newBits::get));
        //assert !isReachable || newGraph.calcReachableBits(newGraph.returnValue.get(1), newGraph.parameters.get(0).bitSet()).size() > 0;
        return newGraph;
    }

    @Override
    public MethodReturnValue analyze(Context c, Parser.MethodInvocationNode callSite, List<Lattices.Value> arguments, Map<Variable, Lattices.AppendOnlyValue> globals) {
        return methodGraphs.get(callSite.definition).applyToArgs(c, arguments, globals);
    }
}
