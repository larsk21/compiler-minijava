package edu.kit.compiler.optimizations;

import java.util.Set;

import com.google.common.graph.MutableNetwork;
import com.google.common.graph.Network;
import com.google.common.graph.NetworkBuilder;

import firm.Entity;
import firm.Program;
import firm.bindings.binding_irnode.ir_opcode;
import firm.nodes.Call;
import firm.nodes.Node;
import firm.nodes.Address;
import firm.nodes.NodeVisitor;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class CallGraph {

    private final Network<Entity, CallEdge> network;

    public static CallGraph create() {
        return new BuilderVisitor().apply();
    }

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

    private static final class BuilderVisitor extends NodeVisitor.Default {

        private Entity caller;
        private final MutableNetwork<Entity, CallEdge> network;

        public BuilderVisitor() {
            this.network = NetworkBuilder.directed()
                    .allowsParallelEdges(true)
                    .allowsSelfLoops(true)
                    .build();
        }

        public CallGraph apply() {
            for (var graph : Program.getGraphs()) {
                this.caller = graph.getEntity();
                graph.walk(this);
            }

            return new CallGraph(network);
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
