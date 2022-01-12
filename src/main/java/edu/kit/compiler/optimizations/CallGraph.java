package edu.kit.compiler.optimizations;

import java.util.Set;

import com.google.common.graph.MutableNetwork;
import com.google.common.graph.NetworkBuilder;

import firm.Entity;
import firm.Graph;
import firm.Program;
import firm.bindings.binding_irnode.ir_opcode;
import firm.nodes.Address;
import firm.nodes.Call;
import firm.nodes.Node;
import firm.nodes.NodeVisitor;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class CallGraph {

    private final MutableNetwork<Entity, CallEdge> network;

    public Set<Entity> getCallers(Entity function) {
        return network.predecessors(function);
    }

    public Set<Entity> getCallees(Entity function) {
        return network.successors(function);
    }

    public Set<CallEdge> getCallsTo(Entity function) {
        return network.inEdges(function);
    }

    public Set<CallEdge> getCallsFrom(Entity function) {
        return network.outEdges(function);
    }

    public int getNumCallsTo(Entity function) {
        return network.inDegree(function);
    }

    public int getNumCallsFrom(Entity function) {
        return network.outDegree(function);
    }

    public boolean existsCall(Entity caller, Entity callee) {
        return network.hasEdgeConnecting(caller, callee);
    }

    public static CallGraph create() {
        return Visitor.build();
    }
    
    public void update(Graph function) {
        Visitor.update(this, function);
    }

    @Override
    public String toString() {
        return String.format("CallGraph(functions=%s, calls=%s)",
                network.nodes(), network.edges());

    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    @EqualsAndHashCode
    private static final class CallEdge {

        @Getter
        private final Node callSite;
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
            var inEdges = callGraph.network.inEdges(entity);
            callGraph.network.removeNode(entity);

            var visitor = new Visitor(entity, callGraph.network);
            function.walk(visitor);

            for (var edge : inEdges) {
                callGraph.network.addEdge(edge.caller, edge.callee, edge);
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
