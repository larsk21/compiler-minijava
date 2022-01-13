package edu.kit.compiler.optimizations;

import java.util.Collections;
import java.util.Set;

import com.google.common.graph.MutableNetwork;
import com.google.common.graph.NetworkBuilder;

import firm.Entity;
import firm.Graph;
import firm.Program;
import firm.bindings.binding_irnode.ir_opcode;
import firm.nodes.Address;
import firm.nodes.Call;
import firm.nodes.NodeVisitor;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
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

    private final MutableNetwork<Entity, CallEdge> network;

    /**
     * Return a set containing every function that may call the given function.
     */
    public Set<Entity> getCallers(Entity function) {
        return network.predecessors(function);
    }

    /**
     * Return a set containing every function that may be called by the given
     * function.
     */
    public Set<Entity> getCallees(Entity function) {
        return network.successors(function);
    }

    /**
     * Return a set containing every call to the given function.
     */
    public Set<CallEdge> getCallsTo(Entity function) {
        return network.inEdges(function);
    }

    /**
     * Return a set containing every call in the given function.
     */
    public Set<CallEdge> getCallsFrom(Entity function) {
        return network.outEdges(function);
    }

    /**
     * Return true if `caller` may directly call `callee`.
     */
    public boolean existsCall(Entity caller, Entity callee) {
        return network.hasEdgeConnecting(caller, callee);
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
     * The function does not need to already exist in the call graph (may be
     * the case if an optimization adds new functions to the program).
     * 
     * Note: This operation is only valid for functions with associated graph,
     * hence the parameter is a `Graph` instead of an `Entity`.
     */
    public void update(Graph function) {
        Visitor.update(this, function);
    }

    @Override
    public String toString() {
        return String.format("CallGraph(functions=%s, calls=%s)",
                network.nodes(), network.edges());
    }

    /**
     * Represents an edge in the call graph.
     * Read as: `caller` calls `callee` at node `callSite`.
     */
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    @EqualsAndHashCode
    private static final class CallEdge {

        @Getter
        private final Call callSite;
        @Getter
        private final Entity caller;
        @Getter
        private final Entity callee;

        @Override
        public String toString() {
            return String.format("(%s -> %s)[node=%s]",
                    caller, callee, callSite.getNr());
        }
    }

    @RequiredArgsConstructor
    @AllArgsConstructor
    private static final class Visitor extends NodeVisitor.Default {

        private Entity caller;
        private final MutableNetwork<Entity, CallEdge> network;

        public static CallGraph build() {
            var visitor = new Visitor(NetworkBuilder.directed()
                    .allowsParallelEdges(true)
                    .allowsSelfLoops(true)
                    .build());

            for (var graph : Program.getGraphs()) {
                visitor.caller = graph.getEntity();
                graph.walk(visitor);
            }

            return new CallGraph(visitor.network);
        }

        public static void update(CallGraph callGraph, Graph function) {
            var entity = function.getEntity();
            var network = callGraph.network;

            // save existing calls to function
            Set<CallEdge> inEdges = Collections.emptySet();
            if (network.nodes().contains(entity)) {
                inEdges = network.inEdges(entity);
                network.removeNode(entity);
            }

            var visitor = new Visitor(entity, callGraph.network);
            function.walk(visitor);

            // restore saved calls to the function
            for (var edge : inEdges) {
                network.addEdge(edge.caller, edge.callee, edge);
            }
        }

        @Override
        public void visit(Call node) {
            if (node.getPtr().getOpCode() == ir_opcode.iro_Address) {
                var callee = ((Address) node.getPtr()).getEntity();
                var edge = new CallEdge(node, caller, callee);
                network.addEdge(caller, callee, edge);
            } else {
                throw new IllegalStateException("only constant function pointers allowed");
            }
        }
    }
}
