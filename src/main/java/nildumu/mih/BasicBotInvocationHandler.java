package nildumu.mih;

import nildumu.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static nildumu.Context.INFTY;
import static nildumu.Lattices.B.S;
import static nildumu.Lattices.B.U;
import static nildumu.Lattices.bl;

class BasicBotInvocationHandler extends MethodInvocationHandler {
    @Override
    public MethodReturnValue analyze(Context c, Parser.MethodInvocationNode callSite, List<Lattices.Value> arguments, Map<Variable, Lattices.AppendOnlyValue> globals) {
        if (arguments.isEmpty() && callSite.definition.getTmpInputVariableDeclarationsFromAll().isEmpty()) {
            return new MethodReturnValue(bot(callSite), globals, new InputBits(c));
        }
        Lattices.Value inputVal = callSite.definition.getTmpInputVariableDeclarationsFromAll().stream()
                .map(t -> {
                    Lattices.Value val = IntStream.range(0, c.maxBitWidth).mapToObj(i -> {
                        Lattices.Bit b = bl.create(U);
                        c.weight(b, INFTY);
                        return b;
                    }).collect(Lattices.Value.collector());
                    c.addInputValue(c.sl.parse(t.secLevel), t.expression, val);
                    return val;
                }).flatMap(Lattices.Value::stream).collect(Lattices.Value.collector());
        Lattices.DependencySet set = Stream.concat(arguments.stream().flatMap(Lattices.Value::stream), inputVal.stream()).collect(Lattices.DependencySet.collector());
        Map<Variable, Lattices.AppendOnlyValue> newGlobals = globals.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        m -> m.getValue()
                                .append(IntStream.range(0, arguments.stream()
                                        .mapToInt(Lattices.Value::size).max().orElse(0))
                                        .mapToObj(i -> bl.create(S, set)).collect(Lattices.Value.collector())
                                ).append(inputVal)));
        if (!callSite.definition.hasReturnValue()) {
            return new MethodReturnValue(bot(callSite), newGlobals, new InputBits(c));
        }
        return new MethodReturnValue(IntStream.range(0, callSite.definition.getNumberOfReturnValues())
                .mapToObj(i -> IntStream.range(0, c.maxBitWidth).mapToObj(i2 -> bl.create(U, set.copy()))
                        .collect(Lattices.Value.collector())).collect(Collectors.toList()), newGlobals, new InputBits(c));
    }
}
