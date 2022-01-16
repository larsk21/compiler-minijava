package edu.kit.compiler.optimizations;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jgrapht.Graph;
import org.jgrapht.Graphs;
import org.jgrapht.alg.connectivity.KosarajuStrongConnectivityInspector;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.graph.DefaultEdge;

import firm.Entity;
import firm.Program;
import firm.bindings.binding_irnode.ir_opcode;
import firm.nodes.Address;
import firm.nodes.Call;
import firm.nodes.NodeVisitor;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;

/**
 * Represents the call graph of a Firm program.
 * 
 * Note: Whenever Entities are passed as parameters or returned from any method
 * in this class, it is assumed that they represent a function. Entities are NOT
 * required to have an associated graph, e.g. external function, like those of
 * the standard library are part of the call graph.
 * Beware that there are no guarantees that entities without associated graph
 * will be represented in the call graph (see also `#create()`).
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class CallGraph {

    private final Graph<Entity, DefaultEdge> graph;

    private Components components;

    /**
     * Return a stream containing every function that may call the given function.
     */
    public Stream<Entity> getCallers(Entity function) {
        return graph.incomingEdgesOf(function).stream()
                .map(graph::getEdgeSource);
    }

    /**
     * Return a stream containing every function that may be called by the given
     * function.
     */
    public Stream<Entity> getCallees(Entity function) {
        return graph.outgoingEdgesOf(function).stream()
                .map(graph::getEdgeTarget);
    }

    /**
     * Return the number of distinct calls from caller to callee.
     */
    public double getCallFrequency(Entity caller, Entity callee) {
        var edge = graph.getEdge(caller, callee);
        return edge == null ? 0.0 : graph.getEdgeWeight(edge);
    }

    /**
     * Return true if `caller` may directly call `callee`.
     */
    public boolean existsCall(Entity caller, Entity callee) {
        return graph.containsEdge(caller, callee);
    }

    /**
     * Returns true if there may exist a recursion if caller calls callee,
     * i.e. there exists some path through the call graph such that caller
     * (in-)directly calls itself. Returns false if caller never calls callee.
     */
    public boolean existsRecursion(Entity caller, Entity callee) {
        return graph.containsEdge(caller, callee)
                && getOrInitComponents().isSameComponent(caller, callee);
    }

    /**
     * Calls #existsRecursion(Entity, Entity) with the caller and callee of the
     * given Call node.
     */
    public boolean existsRecursion(Call call) {
        return existsRecursion(getCaller(call), getCallee(call));
    }

    /**
     * Create a CallGraph based on the current state of Firm. There is guaranteed
     * to be a node for every function with an associated graph in Firm. Any
     * function with no graph, may or may not be present in the call graph
     * (depending on whether it is called or not).
     */
    public static CallGraph create() {
        return Visitor.create(Program.getGraphs());
    }

    /**
     * Create a CallGraph based on the given graphs. Mainly used
     * for testing purposes.
     */
    public static CallGraph create(Iterable<firm.Graph> graphs) {
        return Visitor.create(graphs);
    }

    /**
     * Update the given function in the call graph. All calls to the function
     * will remain in the call graph, all outgoing calls will be reevaluated.
     * The function is not required to currently exist in the call graph (may be
     * the case if an optimization adds new functions to the program).
     * 
     * Note: This operation is only valid for functions with associated graph,
     * hence the parameter is a `Graph` instead of an `Entity`.
     */
    public void update(firm.Graph function) {
        this.components = null;
        Visitor.update(this, function);
    }

    private Components getOrInitComponents() {
        if (components != null) {
            return components;
        } else {
            return (components = new Components(graph));
        }
    }

    @Override
    public String toString() {
        return String.format("CallGraph(functions=%s, calls=%s", graph.vertexSet(),
                graph.edgeSet().stream().map(this::formatEdge)
                        .collect(Collectors.toSet()));
    }

    private String formatEdge(DefaultEdge edge) {
        return String.format("%s -> %s",
                graph.getEdgeSource(edge),
                graph.getEdgeTarget(edge));
    }

    private static Entity getCaller(Call call) {
        return call.getGraph().getEntity();
    }

    private static Entity getCallee(Call call) {
        if (call.getPtr().getOpCode() == ir_opcode.iro_Address) {
            return ((Address) call.getPtr()).getEntity();
        } else {
            throw new IllegalStateException("only constant function pointers allowed");
        }
    }

    private static final class Components {

        private final List<Set<Entity>> components;
        private final Map<Entity, Set<Entity>> functionMap;

        public Components(Graph<Entity, DefaultEdge> graph) {
            var sccInspector = new KosarajuStrongConnectivityInspector<>(graph);
            var connectedSets = sccInspector.stronglyConnectedSets();

            this.components = connectedSets;
            this.functionMap = new HashMap<>();

            for (var component : components) {
                for (var node : component) {
                    functionMap.put(node, component);
                }
            }
        }

        public boolean isSameComponent(Entity func1, Entity func2) {
            var comp1 = functionMap.get(func1);
            var comp2 = functionMap.get(func2);

            // compare components using equality operator
            return comp1 != null && comp1 == comp2;
        }
    }

    @RequiredArgsConstructor
    @AllArgsConstructor
    private static final class Visitor extends NodeVisitor.Default {

        private Entity caller;
        private final Graph<Entity, DefaultEdge> graph;

        public static CallGraph create(Iterable<firm.Graph> graphs) {
            var visitor = new Visitor(new DefaultDirectedWeightedGraph<>(DefaultEdge.class));

            for (var graph : graphs) {
                visitor.caller = graph.getEntity();
                graph.walk(visitor);
            }

            return new CallGraph(visitor.graph);
        }

        public static void update(CallGraph callGraph, firm.Graph function) {
            var entity = function.getEntity();
            var graph = callGraph.graph;

            // remove all outgoing calls from function
            graph.outgoingEdgesOf(entity).stream()
                    .forEach(graph::removeEdge);

            function.walk(new Visitor(entity, graph));
        }

        @Override
        public void visit(Call node) {
            var callee = getCallee(node);
            var edge = graph.getEdge(caller, callee);
            
            if (edge == null) {
                Graphs.addEdgeWithVertices(graph, caller, callee, 1.0);
            } else {
                var weight = graph.getEdgeWeight(edge) + 1.0;
                graph.setEdgeWeight(edge, weight);
            }
        }
    }
}
