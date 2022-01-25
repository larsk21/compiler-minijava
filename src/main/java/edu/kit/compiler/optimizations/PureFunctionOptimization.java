package edu.kit.compiler.optimizations;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import edu.kit.compiler.optimizations.attributes.AttributeAnalysis;
import firm.Graph;
import firm.Mode;
import firm.bindings.binding_irnode.ir_opcode;
import firm.nodes.Call;
import firm.nodes.Node;
import firm.nodes.NodeVisitor;
import firm.nodes.Proj;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

public final class PureFunctionOptimization implements Optimization.Local {

    @Override
    public boolean optimize(Graph graph, OptimizationState state) {
        var collector = CallCollector.apply(state.getAttributeAnalysis(), graph);

        var hasChanged = false;
        for (var call : collector.constCalls) {
            hasChanged |= unpinCall(call);
            hasChanged |= handleConstCall(call);
        }

        for (var call : collector.pureCalls) {
            hasChanged |= unpinCall(call);
            hasChanged |= handlePureCall(call, collector.usedCalls);
        }

        return hasChanged;
    }

    /**
     * Remove call to a const function from the memory chain.
     */
    private static boolean handleConstCall(Call node) {
        var graph = node.getGraph();

        if (!node.getMem().equals(graph.getNoMem())) {
            // setting mem to NoMem on the original call would break the memory chain
            var constCall = (Call) graph.copyNode(node);
            var projRes = graph.newProj(constCall, Mode.getT(), Call.pnTResult);
            constCall.setMem(graph.getNoMem());

            exchangeCall(node, projRes, node.getMem());
            return true;
        } else {
            return false;
        }
    }

    /**
     * Remove call to a pure function if the result is not used.
     */
    private static boolean handlePureCall(Call node, Set<Call> usedCalls) {
        if (!usedCalls.contains(node)) {
            var bad = node.getGraph().newBad(Mode.getT());
            exchangeCall(node, bad, node.getMem());
            return true;
        } else {
            return false;
        }
    }

    /**
     * Unpin the given call if it is currently pinned.
     */
    private static boolean unpinCall(Call node) {
        if (Util.isPinned(node)) {
            Util.setPinned(node, false);
            return true;
        } else {
            return false;
        }
    }

    private static void exchangeCall(Node call, Node result, Node mem) {
        var graph = call.getGraph();
        var bad = graph.newBad(Mode.getX());
        var preds = new Node[] { mem, result, bad, bad };
        Graph.turnIntoTuple(call, preds);
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private static final class CallCollector extends NodeVisitor.Default {

        private final Set<Call> usedCalls = new HashSet<>();
        private final List<Call> pureCalls = new LinkedList<>();
        private final List<Call> constCalls = new LinkedList<>();

        private final AttributeAnalysis analysis;

        public static CallCollector apply(AttributeAnalysis analysis, Graph graph) {
            var visitor = new CallCollector(analysis);
            graph.walk(visitor);

            return visitor;
        }

        @Override
        public void visit(Proj node) {
            if (node.getPred().getOpCode() == ir_opcode.iro_Call
                    && node.getNum() != Call.pnM) {
                usedCalls.add((Call) node.getPred());
            }
        }

        @Override
        public void visit(Call node) {
            var attributes = analysis.getAttributes(Util.getCallee(node));

            if (attributes.isTerminates()) {
                switch (attributes.getPurity()) {
                    case CONST -> constCalls.add(node);
                    case PURE -> pureCalls.add(node);
                    default -> {
                    }
                }
            }
        }
    }
}
