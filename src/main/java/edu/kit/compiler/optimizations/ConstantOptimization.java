package edu.kit.compiler.optimizations;

import java.util.Map;
import java.util.Map.Entry;

import edu.kit.compiler.optimizations.constant_folding.ConstantAnalysis;
import edu.kit.compiler.optimizations.constant_folding.TargetValueLatticeElement;

import firm.Graph;
import firm.nodes.Node;

/**
 * Optimization that finds constant values for value nodes where possible and
 * replaces these node with constant nodes in the given graph.
 */
public class ConstantOptimization implements Optimization {

    private Graph graph;

    @Override
    public void optimize(Graph graph) {
        this.graph = graph;

        ConstantAnalysis analysis = new ConstantAnalysis(graph);
        analysis.analyze();

        Map<Node, TargetValueLatticeElement> nodeValues = analysis.getNodeValues();
        for (Entry<Node, TargetValueLatticeElement> nodeValue : nodeValues.entrySet()) {
            transform(nodeValue.getKey(), nodeValue.getValue());
        }
    }

    /**
     * Transform the given node with the given associated lattice element if
     * that element is constant.
     */
    private void transform(Node node, TargetValueLatticeElement value) {
        if (value.isConstant()) {
            Node constantNode = graph.newConst(value.getValue());
            Graph.exchange(node, constantNode);
        }
    }

}
