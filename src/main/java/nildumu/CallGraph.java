package nildumu;

import java.util.*;
import java.util.function.*;
import java.util.stream.*;

import guru.nidi.graphviz.attribute.*;
import guru.nidi.graphviz.model.*;
import guru.nidi.graphviz.model.Node;
import nildumu.util.DefaultMap;
import swp.lexer.Location;
import swp.parser.lr.BaseAST;
import swp.util.*;

import static guru.nidi.graphviz.model.Factory.*;
import static nildumu.Parser.*;
import static nildumu.util.Util.Box;

/**
 * Call graph for a program that allows to fixpoint iterate over the call graph using an
 * order-optimized worklist algorithm
 */
public class CallGraph {

    public static class CallNode {
        final MethodNode method;
        private final Set<CallNode> callees;
        private final Set<CallNode> callers;
        final boolean isMainNode;

        public CallNode(MethodNode method, Set<CallNode> callees, Set<CallNode> callers, boolean isMainNode) {
            this.method = method;
            this.callees = callees;
            this.callers = callers;
            this.isMainNode = isMainNode;
        }

        public CallNode(MethodNode method){
            this(method, new HashSet<>(), new HashSet<>(), false);
        }

        private void call(CallNode node){
            callees.add(node);
            node.callers.add(this);
        }

        @Override
        public String toString() {
            return method.getTextualId();
        }

        public Set<CallNode> calledCallNodes(){
            Set<CallNode> alreadyVisited = new LinkedHashSet<>();
            Queue<CallNode> queue = new ArrayDeque<>();
            queue.add(this);
            while (queue.size() > 0){
                CallNode cur = queue.poll();
                if (!alreadyVisited.contains(cur)){
                    alreadyVisited.add(cur);
                    queue.addAll(cur.callees);
                }
            }
            return alreadyVisited;
        }

        public Set<CallNode> calledCallNodesAndSelf() {
            Set<CallNode> nodes = calledCallNodes();
            nodes.add(this);
            return nodes;
        }

        public List<CallNode> calledCallNodesAndSelfInPostOrder(){
            return calledCallNodesAndSelfInPostOrder(new HashSet<>());
        }

        private List<CallNode> calledCallNodesAndSelfInPostOrder(Set<CallNode> alreadyVisited){
            alreadyVisited.add(this);
            return Stream.concat(callees.stream()
                    .filter(n -> !alreadyVisited.contains(n))
                    .flatMap(n -> n.calledCallNodesAndSelfInPostOrder(alreadyVisited).stream()),
                    Stream.of(this)).collect(Collectors.toList());
        }

        public Graph createDotGraph(Function<CallNode, Attributes> attrSupplier){
            return graph().graphAttr().with(RankDir.TOP_TO_BOTTOM).directed().with((Node[])calledCallNodesAndSelf()
                    .stream().map(n -> node(n.method.name)
                            .link((String[])n.callees.stream()
                            .map(m -> m.method.name).toArray(i -> new String[i])).with().with(attrSupplier.apply(n))).toArray(i -> new Node[i]));
        }

        public Set<CallNode> getCallees() {
            return Collections.unmodifiableSet(callees);
        }

        public Set<CallNode> getCallers() {
            return Collections.unmodifiableSet(callers);
        }

        public MethodNode getMethod() {
            return method;
        }
    }

    public static class CallFinderNode implements NodeVisitor<Set<MethodNode>> {

        Set<BaseAST> alreadyVisited = new HashSet<>();

        @Override
        public Set<MethodNode> visit(MJNode node) {
            alreadyVisited.add(node);
            return node.children().stream().filter(n -> !alreadyVisited.contains(n)).flatMap(n -> ((MJNode)n).accept(this).stream()).collect(Collectors.toSet());
        }

        @Override
        public Set<MethodNode> visit(MethodInvocationNode methodInvocation) {
            return Stream.concat(Stream.of(methodInvocation.definition), methodInvocation.arguments.accept(this).stream()).collect(Collectors.toSet());
        }

        @Override
        public Set<MethodNode> visit(MethodNode method) {
            return Collections.emptySet();
        }
    }

    final ProgramNode program;
    final CallNode mainNode;
    final Map<MethodNode, CallNode> methodToNode;
    final Map<CallNode, Set<CallNode>> dominators;
    final Map<CallNode, Integer> loopDepths;

    public CallGraph(ProgramNode program) {
        this.program = program;
        this.mainNode =
                new CallNode(
                        new MethodNode(
                                new Location(0, 0),
                                "$main$",
                                new ParametersNode(new Location(0, 0), Collections.emptyList()),
                                program.globalBlock,
                                new GlobalVariablesNode(new Location(0, 0), new HashMap<>())),
                        new HashSet<>(),
                        Collections.emptySet(),
                        true);
        this.methodToNode =
                Stream.concat(Stream.of(mainNode), program.methods().stream().map(CallNode::new))
                        .collect(Collectors.toMap(n -> n.method, n -> n));
        methodToNode
                .entrySet()
                .forEach(
                        e -> {
                            e.getKey()
                                    .body
                                    .accept(new CallFinderNode())
                                    .forEach(m -> e.getValue().call(methodToNode.get(m)));
                        });
        dominators = dominators(mainNode);
        loopDepths = calcLoopDepth(mainNode, dominators);
        DotRegistry.get().store("summary", "call-graph",
                () -> () -> mainNode.createDotGraph(n -> Records.of(loopDepths.get(n) + "", n.method.name)));
    }

    public <T> Map<CallNode, T> worklist(
            BiFunction<CallNode, Map<CallNode, T>, T> action,
            Function<CallNode, T> bot,
            Function<CallNode, Set<CallNode>> next,
            Map<CallNode, T> state) {
        return worklist(mainNode, action, bot, next, loopDepths::get, state);
    }

    public <T> Map<CallNode, T> worklist(
            BiFunction<CallNode, Map<CallNode, T>, T> action,
            Function<CallNode, T> bot,
            Function<CallNode, Set<CallNode>> next,
            Map<CallNode, T> state,
            BiPredicate<T, T> changed) {
        return worklist(mainNode, action, bot, next, loopDepths::get, state, changed);
    }

    public Set<MethodNode> dominators(MethodNode method){
        return dominators.get(methodToNode.get(method)).stream().map(CallNode::getMethod).collect(Collectors.toSet());
    }

    public int loopDepth(MethodNode method){
        return loopDepths.get(methodToNode.get(method));
    }

    public boolean containsRecursion(){
        return loopDepths.values().stream().anyMatch(l -> l > 0);
    }

    public CallNode callNode(MethodNode method){
        return methodToNode.get(method);
    }

    public static Map<CallNode, Set<CallNode>> dominators(CallNode mainNode) {
        Set<CallNode> bot = mainNode.calledCallNodesAndSelf();
        return worklist(
                mainNode,
                (n, map) -> {
                        Set<CallNode> nodes = new HashSet<>(n.callers.stream().filter(n2 -> n != n2).map(map::get).reduce((s1, s2) -> {
                            Set<CallNode> intersection = new HashSet<>(s1);
                            intersection.retainAll(s2);
                            return intersection;
                        }).orElseGet(() -> new HashSet<>()));
                        nodes.add(n);
                        return nodes;
                },
                n -> n == mainNode ? Collections.singleton(n) : bot,
                CallNode::getCallees,
                n -> 1,
                new HashMap<>());
    }

    public static Map<CallNode, Integer> calcLoopDepth(CallNode mainNode, Map<CallNode, Set<CallNode>> dominators) {
        Set<CallNode> loopHeaders = new HashSet<>();
        dominators.forEach((n, dom) -> {
            dom.forEach(d -> {
                if (n.callees.contains(d)) {
                    loopHeaders.add(d);
                }
            });
        });
        Map<CallNode, Set<CallNode>> dominates = new DefaultMap<>((n, map) -> new HashSet<>());
        dominators.forEach((n, dom) -> {
            dom.forEach(d -> dominates.get(d).add(n));
        });
        Map<CallNode, Set<CallNode>> dominatesDirectly = new DefaultMap<>((n, map) -> new HashSet<>());
        dominates.entrySet().forEach(e -> {
            for (CallNode dominated : e.getValue()) {
                if (e.getValue().stream().filter(d -> d != dominated && d != e.getKey()).allMatch(d -> !(dominates.get(d).contains(dominated)))) {
                    dominatesDirectly.get(e.getKey()).add(dominated);
                }
            }
        });

        Map<CallNode, Integer> loopDepths = new HashMap<>();
        Map<CallNode, CallNode> loopHeaderPerNode = new HashMap<>();
        Box<TriConsumer<CallNode, CallNode, List<Pair<CallNode, Integer>>>> action = new Box<>(null);
        action.val = (node, header, depth) -> {
            if (loopDepths.containsKey(node)) {
                return;
            }
            if (loopHeaders.contains(node)) {
                depth = new ArrayList<>(depth);
                depth.add(0, new Pair<>(node, depth.get(0).second + 1));
            }
            for (int i = 0; i < depth.size(); i++) {
                if (i == depth.size() - 1 || node.calledCallNodesAndSelf().contains(depth.get(i).first)) {
                    loopDepths.put(node, depth.get(i).second);
                    loopHeaderPerNode.put(node, depth.get(i).first);
                    break;
                }
            }
            for (CallNode Node : dominatesDirectly.get(node)) {
                if (node != Node) {
                    action.val.accept(Node, loopHeaders.contains(node) ? node : header, depth);
                }
            }
        };
        action.val.accept(mainNode, null, new ArrayList<>(Collections.singletonList(new Pair<>(null, 0))));
        return loopDepths;
    }

    /**
     * Basic extendable worklist algorithm implementation
     *
     * @param mainNode node to start (only methods that this node transitively calls, are considered)
     * @param action transfer function
     * @param bot start element creator
     * @param next next nodes for current node
     * @param priority priority of each node, usable for an inner loop optimization of the iteration
     *     order
     * @param <T> type of the data calculated for each node
     * @return the calculated values
     */
    public static <T> Map<CallNode, T> worklist(
            CallNode mainNode,
            BiFunction<CallNode, Map<CallNode, T>, T> action,
            Function<CallNode, T> bot,
            Function<CallNode, Set<CallNode>> next,
            Function<CallNode, Integer> priority,
            Map<CallNode, T> state) {
        return worklist(mainNode, action, bot, next, priority, state, (f, s) -> !f.equals(s));
    }

    /**
     * Basic extendable worklist algorithm implementation
     *
     * @param mainNode node to start (only methods that this node transitively calls, are considered)
     * @param action transfer function
     * @param bot start element creator
     * @param next next nodes for current node
     * @param priority priority of each node, usable for an inner loop optimization of the iteration
     *     order
     * @param <T> type of the data calculated for each node
     * @return the calculated values
     */
    public static <T> Map<CallNode, T> worklist(
            CallNode mainNode,
            BiFunction<CallNode, Map<CallNode, T>, T> action,
            Function<CallNode, T> bot,
            Function<CallNode, Set<CallNode>> next,
            Function<CallNode, Integer> priority,
            Map<CallNode, T> state,
            BiPredicate<T, T> changed) {
        PriorityQueue<CallNode> queue =
                new PriorityQueue<>(new TreeSet<>(Comparator.comparingInt(priority::apply)));
        queue.addAll(mainNode.calledCallNodesAndSelfInPostOrder());
        Context.log(() -> String.format("Initial order: %s", queue.toString()));
        queue.forEach(n -> state.put(n, bot.apply(n)));
        while (queue.size() > 0) {
            CallNode cur = queue.poll();
            T newRes = action.apply(cur, state);
            T prevRes = state.get(cur);
            state.put(cur, newRes);
            if (changed.test(prevRes, newRes)) {
                queue.addAll(next.apply(cur));
            }
        }
        return state;
    }
}
