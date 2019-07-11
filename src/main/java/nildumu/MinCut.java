package nildumu;

import org.jgrapht.alg.flow.PushRelabelMFImpl;
import org.jgrapht.graph.*;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

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

        final Set<Bit> sourceNodes;
        final Set<Bit> sinkNodes;
        final Function<Bit, Double> weights;

        protected Algorithm(Set<Bit> sourceNodes, Set<Bit> sinkNodes, Function<Bit, Double> weights) {
            this.sourceNodes = sourceNodes;
            this.sinkNodes = sinkNodes;
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

        protected GraphTPP(Set<Bit> sourceNodes, Set<Bit> sinkNodes, Function<Bit, Double> weights) {
            super(sourceNodes, sinkNodes, weights);
        }

        private double infty(){
            return Math.max(nonInfWeightSum(sourceNodes), nonInfWeightSum(sinkNodes));
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
            Vertex source = new Vertex(bl.forceCreateXBit(), false);
            Vertex sink = new Vertex(bl.forceCreateXBit(), true);
            graph.addVertex(source);
            graph.addVertex(sink);
            double infty = (infty() + Bit.getNumberOfCreatedBits() * 2) * 4;
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
            for (Bit bit : sourceNodes){
                bl.walkBits(bit, b -> {
                    for (Bit d : b.deps()){
                        graph.setEdgeWeight(graph.addEdge(bitToNodes.get(b).second, bitToNodes.get(d).first), infty * infty);
                    }
                }, sinkNodes::contains, alreadyVisited);
                graph.setEdgeWeight(graph.addEdge(source, bitToNodes.get(bit).first), infty * infty);
            }
            for (Bit bit : sinkNodes){
                graph.setEdgeWeight(graph.addEdge(bitToNodes.get(bit).second, sink), infty * infty);
            }
            PushRelabelMFImpl<Vertex, DefaultWeightedEdge> pp = new PushRelabelMFImpl<Vertex, DefaultWeightedEdge>(graph, 0.5);
            double maxFlow = pp.calculateMinCut(source, sink);
            Set<Bit> minCut = pp.getCutEdges().stream().map(e -> graph.getEdgeSource(e).bit).collect(Collectors.toSet());
            // Problem: if soome of the sink nodes or source nodes have weight different than 1, then this should be noted
            double flow = Math.min(Math.round(maxFlow), Math.min(weightSum(sourceNodes), weightSum(sinkNodes)));
            if (flow > infty / 2){
                flow = Double.POSITIVE_INFINITY;
            }
            return new ComputationResult(minCut, flow);
        }
    }

    /**
     * Choose the algorithm by setting the static {@link MinCut#usedAlgo} variable
     */
    public static ComputationResult compute(Set<Bit> sourceNodes, Set<Bit> sinkNodes, Function<Bit, Double> weights){
        return new GraphTPP(sourceNodes, sinkNodes, weights).compute();
    }

    public static ComputationResult compute(Context context, Sec<?> sec){
        con = context;
        if (sec == context.sl.top()){
            return new ComputationResult(Collections.emptySet(), 0);
        }
        return compute(context.sources(sec), context.sinks(sec), context::weight);
    }

    private static Context con;

    public static Map<Sec<?>, ComputationResult> compute(Context context){
        return context.sl.elements().stream()
                .collect(Collectors.toMap(s -> s, s -> s == context.sl.top() ?
                        new ComputationResult(Collections.emptySet(), 0) :
                        compute(context, s)));
    }
}
