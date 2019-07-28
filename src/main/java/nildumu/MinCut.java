package nildumu;

import org.jgrapht.Graph;
import org.jgrapht.alg.flow.PushRelabelMFImpl;
import org.jgrapht.graph.*;
import org.jgrapht.io.*;

import java.io.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import nildumu.intervals.Interval;
import nildumu.util.*;
import swp.util.Pair;

import static nildumu.Context.INFTY;
import static nildumu.Lattices.B.U;
import static nildumu.Lattices.*;
import static nildumu.util.Util.p;

/**
 * Computation of the minimum cut on graphs.
 *
 * Min-vertex-cut is transformed into min-cut via a basic transformation, described first in
 * S. Even Graph Algorithms p. 122
 */
public class MinCut {

    public static Algo usedAlgo = Algo.GRAPHT_PP;

    public enum Algo {
        GRAPHT_PP("JGraphT Preflow-Push");

        public final String description;

        Algo(String description){
            this.description = description;
        }

        @Override
        public String toString() {
            return description;
        }
    }

    public static boolean DEBUG = false;

    public static class ComputationResult {
        public final Set<Bit> minCut;
        public final double maxFlow;

        public ComputationResult(Set<Bit> minCut, double maxFlow) {
            this.minCut = minCut;
            if (maxFlow > INFTY){
                this.maxFlow = INFTY;
            } else {
                this.maxFlow = maxFlow;
            }
            if (minCut.size() > maxFlow) {
                System.err.println("#min cut > max flow");
            }
        }
    }

    public static abstract class Algorithm {

        final Context.SourcesAndSinks sourcesAndSinks;
        final Function<Bit, Double> weights;

        protected Algorithm(Context.SourcesAndSinks sourcesAndSinks, Function<Bit, Double> weights) {
            this.sourcesAndSinks = sourcesAndSinks;
            this.weights = weights;
        }

        public abstract ComputationResult compute();
    }


    private static class Vertex {
        public final Bit bit;
        public final boolean isStart;

        private Vertex(Bit bit, boolean isStart) {
            this.bit = bit;
            this.isStart = isStart;
        }

        @Override
        public int hashCode() {
            return Objects.hash(bit, isStart);
        }

        @Override
        public boolean equals(Object obj) {
            return obj.getClass() == this.getClass() && bit.equals(((Vertex)obj).bit) && isStart == ((Vertex)obj).isStart;
        }

        @Override
        public String toString() {
            return bit + "_" + (isStart ? "s" : "e");
        }
    }


    public static class GraphTPP extends Algorithm {

        protected GraphTPP(Context.SourcesAndSinks sourcesAndSinks, Function<Bit, Double> weights) {
            super(sourcesAndSinks, weights);
        }

        private double infty(){
            double w = Math.max(sourcesAndSinks.sourceWeight, sourcesAndSinks.sinkWeight);
            return Math.max(w == INFTY ? 0 : w,
                    Math.max(nonInfWeightSum(sourcesAndSinks.sources), nonInfWeightSum(sourcesAndSinks.sinks)));
        }

        private double nonInfWeightSum(Set<Bit> bits){
            return bits.stream().mapToDouble(weights::apply).filter(w -> w != INFTY).sum();
        }

        private double weightSum(Set<Bit> bits){
            return bits.stream().mapToDouble(weights::apply).sum();
        }

        @Override
        public ComputationResult compute() {
            SimpleDirectedWeightedGraph<Vertex, DefaultWeightedEdge> graph =
                    new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);
            Vertex initialSource = new Vertex(bl.forceCreateXBit(), false);
            Vertex source = new Vertex(bl.forceCreateXBit(), false);
            Vertex initialSink = new Vertex(bl.forceCreateXBit(), true);
            Vertex sink = new Vertex(bl.forceCreateXBit(), true);
            graph.addVertex(source);
            graph.addVertex(sink);
            graph.addVertex(initialSource);
            graph.addVertex(initialSink);
            double infty = (infty() + Bit.getNumberOfCreatedBits() * 2) * 4;
            if (sourcesAndSinks.sourceWeight == INFTY){
                initialSource = source;
            } else {
                graph.setEdgeWeight(graph.addEdge(initialSource, source), infty);
            }
            if (sourcesAndSinks.sinkWeight == INFTY){
                initialSink = sink;
            } else {
                graph.setEdgeWeight(graph.addEdge(sink, initialSink), infty);
            }
            Map<Bit, Pair<Vertex, Vertex>> bitToNodes =
                    new DefaultMap<>((map, bit) -> {
                        Vertex start = new Vertex(bit, true);
                        Vertex end = new Vertex(bit, false);
                        graph.addVertex(start);
                        graph.addVertex(end);
                        DefaultWeightedEdge edge = graph.addEdge(start, end);
                        graph.setEdgeWeight(edge, weights.apply(bit) == INFTY ? infty : 1);
                        return new Pair<>(start, end);
                    });
            Set<Bit> alreadyVisited = new HashSet<>();
            Set<Value> values = new HashSet<>();
            for (Bit bit : sourcesAndSinks.sources){
                bl.walkBits(bit, b -> {
                    for (Bit d : b.deps()){
                        graph.setEdgeWeight(graph.addEdge(bitToNodes.get(b).second, bitToNodes.get(d).first), infty * infty);
                    }
                    if (b.value() != null){
                        values.add(b.value());
                    }
                }, sourcesAndSinks.sinks::contains, alreadyVisited);
                graph.setEdgeWeight(graph.addEdge(source, bitToNodes.get(bit).first), infty * infty);
            }
            for (Bit bit : sourcesAndSinks.sinks){
                graph.setEdgeWeight(graph.addEdge(bitToNodes.get(bit).second, sink), infty * infty);
            }
            if (con.inIntervalMode()) {
                Map<Value, Pair<Vertex, Vertex>> intervalToNodes =
                        new DefaultMap<>((map, value) -> {
                            Vertex start = new Vertex(bl.forceCreateXBit(), true);
                            Vertex end = new Vertex(bl.forceCreateXBit(), false);
                            graph.addVertex(start);
                            graph.addVertex(end);
                            DefaultWeightedEdge edge = graph.addEdge(start, end);
                            graph.setEdgeWeight(edge, value.hasInterval() ? value.entropy() : infty);
                            return new Pair<>(start, end);
                        });
                // insert the interval edges
                for (Value value : values) {
                    if (value.hasInterval() && !value.getInterval().isDefaultInterval()) {
                        Pair<Vertex, Vertex> v = intervalToNodes.get(value);
                        for (Bit b : value.bits) {
                            graph.setEdgeWeight(graph.addEdge(bitToNodes.get(b).first, v.first), 0);
                            graph.setEdgeWeight(graph.addEdge(v.second, bitToNodes.get(b).second), 0);
                            graph.removeEdge(bitToNodes.get(b).first, bitToNodes.get(b).second);
                        }
                    }
                }
            }
            PushRelabelMFImpl<Vertex, DefaultWeightedEdge> pp = new PushRelabelMFImpl<Vertex, DefaultWeightedEdge>(graph, 0.5);
            final Vertex sink_ = initialSink;
            final Vertex source_ = initialSource;
            double maxFlow = pp.calculateMinCut(initialSource, initialSink);
            Set<Bit> minCut = pp.getCutEdges().stream().map(e -> graph.getEdgeSource(e).bit).collect(Collectors.toSet());

            /*DOTExporter<Vertex, DefaultWeightedEdge> export=new DOTExporter<>(v -> v.bit.bitNo + "",
                    v -> {
                        if (v == sink_){
                            return "sink";
                        }
                        if (v == source_){
                            return "source";
                        }
                        return v.bit.toString();
                    },
                    e -> graph.getEdgeWeight(e) + "");
            try {
                export.exportGraph(graph, new FileWriter("graph.dot"));
            }catch (IOException e){}*/

            // Problem: if some of the sink nodes or source nodes have weight different than 1, then this should be noted
            double flow = Math.min(Math.round(maxFlow), Math.min(weightSum(sourcesAndSinks.sources), weightSum(sourcesAndSinks.sinks)));
            if (flow > infty / 2){
                flow = Double.POSITIVE_INFINITY;
            }
            return new ComputationResult(minCut, flow);
        }
    }

    public static ComputationResult compute(Context.SourcesAndSinks sourcesAndSinks, Function<Bit, Double> weights){
        return new GraphTPP(sourcesAndSinks, weights).compute();
    }

    public static ComputationResult compute(Context context, Sec<?> sec){
        con = context;
        if (sec == context.sl.top()){
            return new ComputationResult(Collections.emptySet(), 0);
        }
        return compute(context.sourcesAndSinks(sec), context::weight);
    }

    private static Context con;

    public static Map<Sec<?>, ComputationResult> compute(Context context){
        return context.sl.elements().stream()
                .collect(Collectors.toMap(s -> s, s -> s == context.sl.top() ?
                        new ComputationResult(Collections.emptySet(), 0) :
                        compute(context, s)));
    }
}
