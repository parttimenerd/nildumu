package nildumu.mih;

import nildumu.*;
import nildumu.util.DefaultMap;
import swp.util.Pair;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static nildumu.Context.*;
import static nildumu.Lattices.bl;
import static nildumu.Lattices.vl;
import static nildumu.mih.MethodInvocationHandler.cloneBit;
import static nildumu.util.Util.p;
import static nildumu.util.Util.withIndentedStream;

public class BitGraph {

    final Context context;
    final List<Lattices.Value> parameters;
    public final Set<Lattices.Bit> parameterBits;
    /**
     * bit → parameter number, index
     */
    private final Map<Lattices.Bit, Pair<Integer, Integer>> bitInfo;

    public final MethodInvocationHandler.MethodReturnValue methodReturnValue;
    final List<Lattices.Value> returnValues;

    final List<List<Integer>> paramBitsPerReturnValue;

    final Optional<Parser.MethodNode> methodNode;

    final InputBits inputBits;

    BitGraph(Context context, List<Lattices.Value> parameters, MethodInvocationHandler.MethodReturnValue methodReturnValue,
             Parser.MethodNode methodNode, InputBits inputBits) {
        this(context, parameters, methodReturnValue, Optional.of(methodNode), inputBits);
    }

    BitGraph(Context context, List<Lattices.Value> parameters, MethodInvocationHandler.MethodReturnValue methodReturnValue,
             Optional<Parser.MethodNode> methodNode, InputBits inputBits) {
        this.context = context;
        this.parameters = parameters;
        this.parameterBits = parameters.stream().flatMap(Lattices.Value::stream).collect(Collectors.toSet());
        this.methodReturnValue = methodReturnValue;
        this.bitInfo = new HashMap<>();
        this.methodNode = methodNode;
        for (int i = 0; i < parameters.size(); i++) {
            Lattices.Value param = parameters.get(i);
            for (int j = 1; j <= param.size(); j++) {
                bitInfo.put(param.get(j), p(i, j));
            }
        }
        this.inputBits = inputBits;
        this.returnValues = methodReturnValue.values;
        assertThatAllBitsAreNotNull();
        paramBitsPerReturnValue = returnValues.stream()
                .map(v -> v.stream().map(b -> calcReachableParamBits(b).size())
                        .collect(Collectors.toList())).collect(Collectors.toList());
    }

    private void assertThatAllBitsAreNotNull() {
        returnValues.forEach(v -> {
            v.forEach(b -> {
                assert b != null : "Return bits shouldn't be null";
            });
        });
        vl.walkBits(Lattices.Value.combine(returnValues), b -> {
            assert b != null : "Bits shouldn't be null";
        });
        vl.walkBits(parameters, b -> {
            assert b != null : "Parameters bits shouldn't null";
        });
    }

    /**
     * @param globals old global values
     */
    public MethodInvocationHandler.MethodReturnValue applyToArgs(Context context, List<Lattices.Value> arguments, Map<Variable, Lattices.AppendOnlyValue> globals) {
          MethodInvocationHandler.MethodReturnValue res = withIndentedStream(() -> {
            List<Lattices.Value> extendedArguments = arguments;
            Map<Lattices.Bit, Lattices.Bit> newBits = new HashMap<>();
            // populate
            vl.walkBits(Stream.concat(inputBits.getBits().stream(), this.methodReturnValue.getCombinedValue().stream()).collect(Collectors.toSet()), bit -> {
                if (newBits.containsKey(bit)) {
                    return;
                }
                if (parameterBits.contains(bit)) {
                    Pair<Integer, Integer> loc = bitInfo.get(bit);
                    Lattices.Bit argBit = extendedArguments.get(loc.first).get(loc.second);
                    newBits.put(bit, argBit);
                } else {
                    Lattices.Bit clone = cloneBit(context, bit, d(bit));
                    clone.value(bit.value());
                    newBits.put(bit, clone);
                }
            });
            DefaultMap<Lattices.Value, Lattices.Value> newValues = new DefaultMap<>((map, value) -> {
                if (parameters.contains(value)) {
                    return arguments.get(parameters.indexOf(value));
                }
                Lattices.Value clone = value.map(b -> {
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
            // set of new global variables
            Map<Variable, Lattices.AppendOnlyValue> globs = new HashMap<>(globals);
            globs.putAll(this.methodReturnValue.globals.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                    e -> {
                        Lattices.AppendOnlyValue oldValue = globals.getOrDefault(e.getKey(), Lattices.AppendOnlyValue.createEmpty()); // new value??
                        Lattices.Value additionWithThisCall = this.methodReturnValue.globals.get(e.getKey()).map(newBits::get); // old stream content??
                        return oldValue.append(additionWithThisCall); // append to the globals
                    })));
            return new MethodInvocationHandler.MethodReturnValue(returnValues.stream()
                    .map(v -> v.map(newBits::get)).collect(Collectors.toList()),
                    globs, inputBits.map(newBits::get));
        });
        return res;
    }

    public Set<Lattices.Bit> calcReachableParamBits(Lattices.Bit bit) {
        return Lattices.BitLattice.calcReachableBits(bit, parameterBits);
    }

    public Set<Lattices.Bit> calcReachableInputAndParameterBits(Lattices.Bit bit) {
        Set<Lattices.Bit> inputBits = this.inputBits.getBits();
        inputBits.addAll(parameterBits);
        return bl.reachableBits(Collections.singleton(bit), inputBits);
    }

    public Set<Lattices.Bit> minCutBits() {
        return minCutBits(returnValues.stream().flatMap(v -> v.bitSet().stream()).collect(Collectors.toSet()), parameterBits);
    }

    public Set<Lattices.Bit> minCutBits(Set<Lattices.Bit> outputBits, Set<Lattices.Bit> inputBits) {
        return LeakageAlgorithm.usedAlgo.compute(new LeakageAlgorithm.SourcesAndSinks(INFTY, outputBits, INFTY, inputBits, context), context::weight).minCut;
    }

    public Set<Lattices.Bit> minCutBits(Set<Lattices.Bit> outputBits, Set<Lattices.Bit> inputBits, double outputWeight) {
        return LeakageAlgorithm.usedAlgo.compute(new LeakageAlgorithm.SourcesAndSinks(INFTY, outputBits, INFTY, inputBits, context), b -> outputBits.contains(b) ? outputWeight : context.weight(b)).minCut;
    }

    /**
     * Used only for the fix point iteration
     */
    @Override
    public boolean equals(Object obj) {
        throw new RuntimeException();
    }

    public Context getContext() {
        return context;
    }

    public List<Lattices.Value> getParameters() {
        return Collections.unmodifiableList(parameters);
    }

    public List<Lattices.Value> getReturnValues() {
        return Collections.unmodifiableList(returnValues);
    }

    public MethodInvocationHandler.MethodReturnValue getMethodReturnValue() {
        return methodReturnValue;
    }

    public Set<Lattices.Bit> getParameterBits() {
        return Collections.unmodifiableSet(parameterBits);
    }
}
