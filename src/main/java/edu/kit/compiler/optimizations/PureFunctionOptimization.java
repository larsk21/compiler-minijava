package edu.kit.compiler.optimizations;

import edu.kit.compiler.optimizations.attributes.AttributeAnalysis;
import firm.Graph;
import firm.Mode;
import firm.nodes.Call;
import firm.nodes.Node;
import firm.nodes.NodeVisitor;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

public final class PureFunctionOptimization implements Optimization.Local {

    @Override
    public boolean optimize(Graph graph, OptimizationState state) {
        var visitor = new Visitor(state.getAttributeAnalysis());
        graph.walk(visitor);

        return visitor.hasChanged;
    }

    private static void exchangeCall(Node call, Node result, Node mem) {
        var graph = call.getGraph();
        var bad = graph.newBad(Mode.getX());
        var preds = new Node[] { mem, result, bad, bad };
        Graph.turnIntoTuple(call, preds);
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private final class Visitor extends NodeVisitor.Default {

        private final AttributeAnalysis analysis;
        private boolean hasChanged = false;

        @Override
        public void visit(Call node) {
            var graph = node.getGraph();
            var attributes = analysis.get(Util.getCallee(node));

            // ! pure function calls could be removed if the result is not used
            // ! This is analogous to load, so maybe do as part of LDST optimization

            if (attributes.isTerminates() && attributes.isConst()
                    && !node.getMem().equals(graph.getNoMem())) {
                // we can't just set mem to NoMem as this would break the memory
                // chain, instead we insert a tuple in front of the call
                var constCall = (Call) graph.copyNode(node);
                constCall.setMem(graph.getNoMem());
                Util.setPinned(constCall, false);

                exchangeCall(node, constCall, node.getMem());
                hasChanged = true;
            }
        }
    }
}
