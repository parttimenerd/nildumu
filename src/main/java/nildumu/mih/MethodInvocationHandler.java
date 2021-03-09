package nildumu.mih;

import nildumu.Context;
import nildumu.InputBits;
import nildumu.Lattices;
import nildumu.Variable;
import swp.util.Pair;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static nildumu.Context.v;
import static nildumu.Lattices.*;
import static nildumu.Lattices.B.S;
import static nildumu.Parser.MethodInvocationNode;
import static nildumu.Parser.ProgramNode;
import static nildumu.util.Util.p;

/**
 * Handles the analysis of methods → implements the interprocedural part of the analysis.
 * <p>
 * Handler classes can be registered and configured via property strings.
 */
public abstract class MethodInvocationHandler {

    private static Map<String, Pair<PropertyScheme, Function<Properties, MethodInvocationHandler>>> registry = new HashMap<>();

    private static List<String> examplePropLines = new ArrayList<>();

    /**
     * Regsiter a new class of handlers
     */
    private static void register(String name, Consumer<PropertyScheme> propSchemeCreator, Function<Properties, MethodInvocationHandler> creator) {
        PropertyScheme scheme = new PropertyScheme();
        propSchemeCreator.accept(scheme);
        scheme.add("handler", null);
        registry.put(name, p(scheme, creator));
    }

    public static enum Reduction {
        BASIC,
        MINCUT;
    }

    /**
     * Returns the handler for the passed string, the property "handler" defines the handler class
     * to be used
     */
    public static MethodInvocationHandler parse(String props) {
        Properties properties = new PropertyScheme().add("handler").parse(props, true);
        String handlerName = properties.getProperty("handler");
        if (!registry.containsKey(handlerName)) {
            throw new MethodInvocationHandlerInitializationError(String.format("unknown handler %s, possible handlers are: %s", handlerName, registry.keySet()));
        }
        try {
            Pair<PropertyScheme, Function<Properties, MethodInvocationHandler>> pair = registry.get(handlerName);
            return pair.second.apply(pair.first.parse(props));
        } catch (MethodInvocationHandlerInitializationError error) {
            throw error;
        } catch (Error error) {
            throw new MethodInvocationHandlerInitializationError(String.format("parsing \"%s\": %s", props, error.getMessage()));
        }
    }

    public static List<String> getExamplePropLines() {
        return Collections.unmodifiableList(examplePropLines);
    }

    public static class MethodReturnValue {
        public final List<Value> values;
        public final Map<Variable, AppendOnlyValue> globals;
        public final InputBits inputBits;

        MethodReturnValue(List<Value> values, Map<Variable, AppendOnlyValue> globals, InputBits inputBits) {
            this.values = values;
            this.globals = globals;
            this.inputBits = inputBits;
        }

        public Value getCombinedReturnValue() {
            return Value.combine(values);
        }

        public Value getCombinedValue() {
            return Stream.concat(Stream.of(getCombinedReturnValue()), globals.values().stream()).flatMap(Value::stream).collect(Value.collector());
        }

        MethodReturnValue map(Function<Bit, Bit> transformer) {
            return new MethodReturnValue(values.stream().map(v -> v.map(transformer)).collect(Collectors.toList()), globals.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().map(transformer).asAppendOnly())),
                    inputBits.map(transformer));
        }

        @Override
        public String toString() {
            return Arrays.toString(values.toArray(new Value[0])) + " " + globals.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining(", "));
        }
    }

    static List<Value> bot(MethodInvocationNode callSite) {
        return Collections.nCopies(callSite.definition.getNumberOfReturnValues(), vl.bot());
    }

    static {
        register("basic", s -> {
        }, ps -> new BasicBotInvocationHandler());
        examplePropLines.add("handler=basic");
        register("inlining", s -> s.add("maxrec", "2").add("bot", "basic"), ps -> {
            return new InliningHandler(Integer.parseInt(ps.getProperty("maxrec")), parse(ps.getProperty("bot")));
        });
        examplePropLines.add("handler=inlining;maxrec=5;bot=summary");
        examplePropLines.add("handler=inlining;maxrec=2;bot={handler=summary;bot=inlining}");
        Consumer<PropertyScheme> propSchemeCreator = s ->
                s.add("maxiter", "1")
                        .add("bot", "basic")
                        .add("mode", "auto")
                        .add("reduction", "mincut")
                        .add("csmaxrec", "0")
                        .add("dot", "");
        register("summary", propSchemeCreator, ps -> {
            Path dotFolder = ps.getProperty("dot").equals("") ? null : Paths.get(ps.getProperty("dot"));
            return new SummaryHandler(ps.getProperty("mode").equals("coind") ? Integer.parseInt(ps.getProperty("maxiter")) : Integer.MAX_VALUE,
                    ps.getProperty("mode").equals("ind") ? SummaryHandler.Mode.INDUCTION : (ps.getProperty("mode").equals("auto") ? SummaryHandler.Mode.AUTO : SummaryHandler.Mode.COINDUCTION),
                    parse(ps.getProperty("bot")), dotFolder, Reduction.valueOf(ps.getProperty("reduction").toUpperCase()), Integer.parseInt(ps.getProperty("csmaxrec")));
        });
        examplePropLines.add("handler=summary;bot=basic;reduction=basic");
        examplePropLines.add("handler=summary;bot=basic;reduction=mincut");
        //examplePropLines.add("handler=summary_mc;mode=ind");
    }

    public static MethodInvocationHandler createDefault() {
        return parse(getDefaultPropString());
    }

    public static String getDefaultPropString() {
        return "handler=inlining;maxrec=2;bot=basic";
    }

    public void setup(ProgramNode program) {
    }

    public MethodReturnValue analyze(Context c, MethodInvocationNode callSite, List<Value> arguments, Map<Variable, AppendOnlyValue> globals) {
        DependencySet set = arguments.stream().flatMap(Value::stream).collect(DependencySet.collector());
        Map<Variable, AppendOnlyValue> newGlobals = globals.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, m -> m.getValue().append(new Value(bl.create(S, set)))));
        List<Value> ret = analyze(c, callSite, arguments);
        return new MethodReturnValue(ret, newGlobals, new InputBits());
    }

    public List<Value> analyze(Context c, MethodInvocationNode callSite, List<Value> arguments) {
        throw new RuntimeException("Not yet implemented");
    }


    public static Lattices.Bit cloneBit(Context context, Lattices.Bit bit, Lattices.DependencySet deps) {
        Lattices.Bit clone;
        if (bit.isAtLeastUnknown()) {
            clone = bl.create(bit.val(), deps);
        } else {
            clone = bl.create(v(bit));
        }
        context.repl(clone, context.repl(bit));
        context.weight(clone, context.weight(bit));
        return clone;
    }

}
