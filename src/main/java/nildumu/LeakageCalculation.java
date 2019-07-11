package nildumu;

import java.util.*;
import java.util.stream.Collectors;

import guru.nidi.graphviz.model.Graph;
import nildumu.util.DefaultMap;

import static nildumu.Lattices.*;

public class LeakageCalculation {

    public static Graph visuDotGraph(Context context, String name, Sec<?> sec){
        Set<Bit> minCut = context.computeLeakage().get(sec).minCut;
        return DotRegistry.createDotGraph(context, name,
                Collections.singletonList(new DotRegistry.Anchor("input", context.sinks(sec).stream().collect(Value.collector()))),
                new DotRegistry.Anchor("output", context.sources(sec).stream().collect(Value.collector())), minCut);
    }
}
