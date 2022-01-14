package edu.kit.compiler.optimizations;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jgrapht.Graph;
import org.jgrapht.Graphs;
import org.jgrapht.graph.DefaultDirectedGraph;
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
     * Return true if `caller` may directly call `callee`.
     */
    public boolean existsCall(Entity caller, Entity callee) {
        return graph.containsEdge(caller, callee);
    }

    /**
     * Create a CallGraph based on the current state of Firm. There is guaranteed
     * to be a node for every function with an associated graph in Firm. Any
     * function with no graph, may or may not be present in the call graph
     * (depending on whether it is called or not).
     */
    public static CallGraph create() {
        return Visitor.build();
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
        Visitor.update(this, function);
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

    @RequiredArgsConstructor
    @AllArgsConstructor
    private static final class Visitor extends NodeVisitor.Default {

        private Entity caller;
        private final Graph<Entity, DefaultEdge> graph;

        public static CallGraph build() {
            var visitor = new Visitor(new DefaultDirectedGraph<>(DefaultEdge.class));

            for (var graph : Program.getGraphs()) {
                visitor.caller = graph.getEntity();
                visitor.graph.addVertex(visitor.caller);
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
            if (node.getPtr().getOpCode() == ir_opcode.iro_Address) {
                var callee = ((Address) node.getPtr()).getEntity();
                Graphs.addEdgeWithVertices(graph, caller, callee);
            } else {
                throw new IllegalStateException("only constant function pointers allowed");
            }
        }
    }
}
