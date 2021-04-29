package nildumu.ui;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import guru.nidi.graphviz.attribute.*;
import guru.nidi.graphviz.engine.*;
import guru.nidi.graphviz.model.*;
import nildumu.*;
import nildumu.intervals.Intervals;
import nildumu.mih.BitGraph;
import nildumu.util.DefaultMap;
import swp.util.Pair;
import swp.util.Utils;

import static guru.nidi.graphviz.attribute.Attributes.attr;
import static guru.nidi.graphviz.model.Factory.*;
import static nildumu.Context.INFTY;
import static nildumu.Lattices.*;

/**
 * Registry for generated graphviz files.
 * Yes it's global state, but it simplifies the UI and the registry can be easily cleaned.
 */
public class DotRegistry {

    /**
     * A dot file with a name and a topic
     */
    public static class DotFile implements Comparable<DotFile> {

        public final String topic;
        public final String name;
        public final Supplier<Graph> graphCreator;
        private final Path svgPath;
        private final boolean createdSVG = false;

        private DotFile(String topic, String name, Supplier<Graph> graphCreator, Path svgPath) {
            this.topic = topic;
            this.name = name;
            this.graphCreator = graphCreator;
            this.svgPath = svgPath;
        }

        public void delete(){
            try {
                Files.deleteIfExists(svgPath);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public int compareTo(DotFile o) {
            if (topic.equals(o.topic)) {
                return name.compareTo(o.name);
            }
            return topic.compareTo(o.topic);
        }

        public Path getSvgPath() {
            if (!createdSVG){
                try {
                    Graphviz.fromGraph(graphCreator.get().named(name)).engine(Engine.DOT)
                            .render(Format.SVG_STANDALONE).toFile(svgPath.toFile());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return svgPath;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public static final String TMP_DIR = "tmp";

    private boolean enabled = false;

    private Map<String, LinkedHashMap<String, DotFile>> filesPerTopic = new LinkedHashMap<>();

    private final Path tmpDir;

    private static DotRegistry instance = new DotRegistry();

    private DotRegistry(){
        tmpDir = Paths.get(TMP_DIR);
        try {
            Files.createDirectories(tmpDir);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean enabled(){
        return enabled;
    }

    public void enable(){
        enabled = true;
        GraphRegistry.get().enable();
    }

    public void disable(){
        enabled = false;
        GraphRegistry.get().disable();
    }

    public Map<String, LinkedHashMap<String, DotFile>> getFilesPerTopic() {
        return filesPerTopic;
    }

    public void reset(){
        filesPerTopic.values().stream().flatMap(l -> l.values().stream()).forEach(DotFile::delete);
        filesPerTopic.clear();
        GraphRegistry.get().reset();
        try {
            for (Path path : Files.list(tmpDir).collect(Collectors.toList())){
                Files.deleteIfExists(path);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static DotRegistry get() {
        return instance;
    }

    /**
     * Creates a dot graph using the passed graphCreator if the registry is enabled.
     * Stores it under the topic.
     *
     * <b>Does not create any files, the svg file is created lazily</b>
     */
    public void store(String topic, String name, Supplier<Supplier<Graph>> graphCreator){
        if (enabled){
            Path topicPath = tmpDir.resolve(topic);
            if (!filesPerTopic.containsKey(topic)){
                filesPerTopic.put(topic, new LinkedHashMap<>());
                try {
                    Files.createDirectories(topicPath);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            filesPerTopic.get(topic).put(name, new DotFile(topic, name, graphCreator.get(), topicPath.resolve(name + ".svg")));
        }
    }

    public boolean hasTopic(String topic){
        return filesPerTopic.containsKey(topic);
    }

    public LinkedHashMap<String, DotFile> getFilesPerTopic(String topic){
        loadGraphRegistry();
        return getFilesPerTopic_(topic);
    }

    private LinkedHashMap<String, DotFile> getFilesPerTopic_(String topic){
        return filesPerTopic.getOrDefault(topic == null ? "" : topic, new LinkedHashMap<>());
    }

    public boolean has(String topic, String name){
        loadGraphRegistry();
        return _has(topic, name);
    }

    private boolean _has(String topic, String name){
        return getFilesPerTopic_(topic).containsKey(name);
    }

    public Optional<DotFile> get(String topic, String name){
        loadGraphRegistry();
        return Optional.ofNullable(getFilesPerTopic_(topic).getOrDefault(name, null));
    }

    public static class Anchor {

        private final String name;

        private final Value value;

        public Anchor(String name, Value value) {
            this.name = name;
            this.value = value;
        }
    }

    public static Graph createDotGraph(Context context, String name, List<Anchor> topAnchors, Anchor botAnchor,
                                       Set<Bit> selectedBits){
        return createDotGraph(context, name, topAnchors, botAnchor, b -> {
            if (selectedBits.contains(b)){
                return Arrays.asList(Color.RED, Color.RED.font());
            }
            return Collections.emptyList();
        });
    }

    public static Graph createDotGraph(Context context, String name, List<Anchor> topAnchors, Anchor botAnchor,
                                       Function<Bit, List<Attributes>> nodeAttributes){
        List<MutableNode> nodeList = new ArrayList<>();
        Set<Bit> alreadyVisited = new HashSet<>();
        Function<Bit, Attributes> dotLabel = b -> {
            List<String> parts = new ArrayList<>();
            if (context.weight(b) == INFTY){
                parts.add("inf");
            }
            parts.add(b.uniqueId());
            if (context.isAlternativeBit(b)) {
                parts.add("either");
            } else {
                parts.add(b.toString().replace("<", "&lt;"));
            }
            return Records.of((String[])parts.toArray(new String[0]));
        };
        Map<Bit, MutableNode> nodes = new DefaultMap<>((map, b) -> {
            MutableNode node = mutNode(b.bitNo + "");
            node.add(dotLabel.apply(b));
            node.add(nodeAttributes.apply(b).toArray(new Attributes[0]));
            node.add(attr("font-family", "Helvetica"));
            node.add(attr("fontname", "Helvetica"));
            nodeList.add(node);
            return node;
        });
        Set<Value> values = new HashSet<>();
        Map<Bit, Set<Bit>> outgoingEdges = new DefaultMap<>((map, n) -> new HashSet<>());
        for (Bit bit : botAnchor.value) {
            bl.walkBits(bit, b -> {
                b.deps().stream().forEach(b_ -> outgoingEdges.get(b_).add(b));
                if (b.value() != null){
                    values.add(b.value());
                }
                }, b -> false, alreadyVisited, b -> b.deps().stream().sorted(Comparator.comparingLong(d -> d.bitNo)).collect(Collectors.toList()));
        }
        outgoingEdges.forEach((b, b_) -> {
            nodes.get(b).addLink((String[])outgoingEdges.get(b).stream().sorted(Comparator.comparingLong(d -> d.bitNo)).map(d -> d.bitNo + "").toArray(String[]::new));
        });
        if (context.inIntervalMode()) {
            Map<Value, MutableNode> intervalToNodes =
                    new DefaultMap<>((map, value) -> {
                        MutableNode node = mutNode(value.interval.id + "");
                        node.add(Records.of(new String[]{value.interval.id + "",
                                value.interval.start + "", value.interval.end + "", "#" + Intervals.countPattern(value.interval, value.asConstraints())}));
                        node.add(attr("font-family", "Helvetica"));
                        node.add(attr("fontname", "Helvetica"));
                        node.add(Color.CHOCOLATE4, Color.CHOCOLATE4.font());
                        nodeList.add(node);
                        return node;
                    });
            // insert the interval edges
            for (Value value : values) {
                if (value.hasInterval() && !value.getInterval().isDefaultInterval()) {
                    for (Bit b : value.bits.toArray(new Bit[0])) {
                        if (nodes.containsKey(b)) {
                            nodes.get(b).linkTo(intervalToNodes.get(value))
                                    .add(Color.CHOCOLATE4.fill());
                            intervalToNodes.get(value).linkTo(nodes.get(b))
                                    .add(Color.CHOCOLATE4.fill());
                        }
                    }
                }
            }
        }
        topAnchors.stream().sorted(Comparator.comparing(s -> s.name)).forEach(anchor -> {
            Value val = anchor.value;
            String nodeId = anchor.name;
            MutableNode paramNode = mutNode(nodeId);
            paramNode.add(Color.GREEN, Color.GREEN.font());
            nodeList.add(paramNode);
            val.stream().map(nodes::get).forEach(n -> paramNode.addLink(n));
        });
        MutableNode ret = mutNode(botAnchor.name);
        ret.add(Color.BLUE, Color.BLUE.font());
        nodeList.add(ret);
        botAnchor.value.stream().map(nodes::get).forEach(n -> n.addLink(ret));
        return graph(name).directed().nodeAttr().with(Font.name("Helvetica")).graphAttr().with(RankDir.TOP_TO_BOTTOM).graphAttr().with(Font.name("Helvetica")).with((MutableNode[])nodeList.toArray(new MutableNode[0]));
    }

    public static Graph createDotGraph(BitGraph bg, String name, boolean withMinCut) {
        Lattices.Value ret = new Lattices.Value(bg.getReturnValues().stream()
                .flatMap(Lattices.Value::stream).collect(Collectors.toList()));
        return DotRegistry.createDotGraph(bg.getContext(), name, Stream.concat(IntStream.range(0, bg.getParameters().size())
                        .mapToObj(i -> new DotRegistry.Anchor(String.format("param %d", i), bg.getParameters().get(i))),
                bg.getMethodReturnValue().globals.entrySet().stream().map(e ->
                        new DotRegistry.Anchor(e.getKey().name, e.getValue()))
                ).collect(Collectors.toList()),
                new DotRegistry.Anchor("return", Lattices.Value.combine(bg.getReturnValues())),
                withMinCut ? bg.minCutBits(ret.bitSet(), bg.getParameterBits(), INFTY) : Collections.emptySet());
    }

    public static void writeDotGraph(BitGraph bg, Path folder, String name, boolean withMinCut) {
        Path path = folder.resolve(name + ".dot");
        try {
            Files.createDirectories(folder);
            Graphviz.fromGraph(createDotGraph(bg, name, withMinCut)).render(Format.XDOT).toFile(path.toFile());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static Graph createDotGraph(CallGraph.CallNode node, Function<CallGraph.CallNode, Attributes> attrSupplier){
        return graph().graphAttr().with(RankDir.TOP_TO_BOTTOM).directed().with((Node[])node.calledCallNodesAndSelf()
                .stream().map(n -> node(n.method.name)
                        .link((String[])n.callees.stream()
                                .map(m -> m.method.name).toArray(String[]::new)).with().with(attrSupplier.apply(n))).toArray(Node[]::new));
    }

    public void loadGraphRegistry() {
        for (Utils.Triple<String, String, CallGraph> cgTriple : GraphRegistry.get().getCallGraphsPerTopic()) {
            if (!_has(cgTriple.first, cgTriple.second)) {
                store(cgTriple.first, cgTriple.second, () -> () -> createDotGraph(cgTriple.third.mainNode,
                        (CallGraph.CallNode n) -> Records.of(cgTriple.third.loopDepths.get(n) + "", n.method.name)));
            }
        }
        for (Utils.Quadruple<Pair<String, String>, BitGraph, String, Boolean> quadruple : GraphRegistry.get().getBitGraphsPerTopic()) {
            if (!_has(quadruple.first.first, quadruple.first.second)) {
                store(quadruple.first.first, quadruple.first.second, () -> () -> createDotGraph(quadruple.second, quadruple.third, quadruple.fourth));
            }
        }
    }

    public static Graph visuLeakageDotGraph(Context context, String name, Sec<?> sec){
        Set<Bit> minCut = context.computeLeakage(MinCut.usedAlgo).get(sec).minCut;
        return createDotGraph(context, name,
                Collections.singletonList(new Anchor("input", context.sinks(sec).stream().collect(Value.collector()))),
                new Anchor("output", context.sources(sec).stream().collect(Value.collector())), minCut);
    }
}
