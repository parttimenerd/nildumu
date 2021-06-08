package nildumu;

import nildumu.solver.SolverBasedLeakageAlgorithm;
import nildumu.solver.PMSATSolverImpl;
import nildumu.util.DefaultMap;
import org.jgrapht.alg.flow.PushRelabelMFImpl;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleDirectedWeightedGraph;
import swp.util.Pair;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static nildumu.Context.INFTY;
import static nildumu.Lattices.*;

/**
 * Computation of the minimum cut on graphs.
 *
 * Min-vertex-cut is transformed into min-cut via a basic transformation, described first in
 * S. Even Graph Algorithms p. 122
 */
public class MinCut {

    public static boolean DEBUG = false;


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


    public static class GraphTPP extends LeakageAlgorithm {

        protected GraphTPP(SourcesAndSinks sourcesAndSinks, Function<Bit, Double> weights) {
            super(sourcesAndSinks, weights);
            assert !sourcesAndSinks.context.recordsAlternatives();
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
            for (Bit bit : sourcesAndSinks.sources){
                bl.walkBits(bit, b -> {
                    for (Bit d : b.deps()){
                        graph.setEdgeWeight(graph.addEdge(bitToNodes.get(b).second, bitToNodes.get(d).first), infty * infty);
                    }
                }, sourcesAndSinks.sinks::contains, alreadyVisited);
                graph.setEdgeWeight(graph.addEdge(source, bitToNodes.get(bit).first), infty * infty);
            }
            for (Bit bit : sourcesAndSinks.sinks){
                graph.setEdgeWeight(graph.addEdge(bitToNodes.get(bit).second, sink), infty * infty);
            }
            PushRelabelMFImpl<Vertex, DefaultWeightedEdge> pp = new PushRelabelMFImpl<Vertex, DefaultWeightedEdge>(graph, 0.5);
            double maxFlow = pp.calculateMinCut(initialSource, initialSink);
            Set<Bit> minCut = pp.getCutEdges().stream().map(e -> graph.getEdgeSource(e).bit).collect(Collectors.toSet());
            // Problem: if some of the sink nodes or source nodes have weight different than 1, then this should be noted
            double flow = Math.min(Math.round(maxFlow), Math.min(weightSum(sourcesAndSinks.sources), weightSum(sourcesAndSinks.sinks)));
            if (flow > infty / 2){
                flow = Double.POSITIVE_INFINITY;
            }
            return new ComputationResult(minCut, flow);
        }
    }

}
