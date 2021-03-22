package nildumu.mih;

import nildumu.*;
import nildumu.util.DefaultMap;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A call string based handler that just inlines a function.
 * If a function was inlined in the current call path more than a defined number of times,
 * then another handler is used to compute a conservative approximation.
 */
public class InliningHandler extends MethodInvocationHandler {

    final int maxRec;

    final MethodInvocationHandler botHandler;

    private final DefaultMap<Parser.MethodNode, Integer> methodCallCounter = new DefaultMap<>((map, method) -> 0);

    InliningHandler(int maxRec, MethodInvocationHandler botHandler) {
        this.maxRec = maxRec;
        this.botHandler = botHandler;
    }

    @Override
    public void setup(Parser.ProgramNode program) {
        botHandler.setup(program);
    }

    @Override
    public MethodReturnValue analyze(Context c, Parser.MethodInvocationNode callSite, List<Lattices.Value> arguments, Map<Variable, Lattices.AppendOnlyValue> globals) {
        Parser.MethodNode method = callSite.definition;
        if (methodCallCounter.get(method) < maxRec) {
            methodCallCounter.put(method, methodCallCounter.get(method) + 1);
            c.pushNewFrame(callSite, arguments);
            for (int i = 0; i < arguments.size(); i++) {
                c.setVariableValue(method.parameters.get(i).definition, arguments.get(i));
            }
            globals.forEach((v, a) -> {
                if (method.globalDefs.containsKey(v)) {
                    c.setVariableValue(method.globalDefs.get(v).first, a);
                }
            });
            Processor.process(c, method.body);
            Lattices.Value ret = c.getReturnValue();
            Map<Variable, Lattices.AppendOnlyValue> globalVals = method.globalDefs.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                    e -> c.getVariableValue(e.getValue().second).asAppendOnly()));
            InputBits inputBits = c.getNewlyIntroducedInputs();
            c.popFrame();
            methodCallCounter.put(method, methodCallCounter.get(method) - 1);
            return new MethodReturnValue(ret.split(callSite.definition.getNumberOfReturnValues()), globalVals, inputBits);
        }
        return botHandler.analyze(c, callSite, arguments, globals);
    }
}
