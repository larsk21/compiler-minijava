package edu.kit.compiler.optimizations;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import edu.kit.compiler.optimizations.constant_folding.ConstantAnalysis;
import edu.kit.compiler.optimizations.constant_folding.TargetValueLatticeElement;

import firm.Graph;
import firm.Mode;
import firm.nodes.Node;
import firm.nodes.NodeVisitor;
import firm.nodes.Proj;

/**
 * Optimization that finds constant values for value nodes where possible and
 * replaces these node with constant nodes in the given graph.
 */
public class ConstantOptimization implements Optimization {

    private Graph graph;
    private Map<Node, TargetValueLatticeElement> nodeValues;

    @Override
    public void optimize(Graph graph) {
        this.graph = graph;

        ConstantAnalysis analysis = new ConstantAnalysis(graph);
        analysis.analyze();

        this.nodeValues = analysis.getNodeValues();

        List<Node> nodes = new ArrayList<>();
        graph.walkPostorder(new NodeVisitor.Default() {

            @Override
            public void defaultVisit(Node node) {
                nodes.add(0, node);
            }

        });

        // we transform the nodes in reverse postorder, i.e. we can access the
        // unchanged predecessors of a node when transforming it
        for (Node node : nodes) {
            transform(node, nodeValues.get(node));
        }
    }

    /**
     * Transform the given node with the given associated lattice element if
     * that element is constant.
     */
    private void transform(Node node, TargetValueLatticeElement value) {
        if (node instanceof Proj && node.getMode().equals(Mode.getM())) {
            // the predecessor of a memory projection will be set to the first
            // node that does not have a constant value following the memory
            // dependecy chain

            Node pred = node.getPred(0);
            while (nodeValues.containsKey(pred) && nodeValues.get(pred).isConstant()) {
                inputs: for (Node input : pred.getPreds()) {
                    if (input instanceof Proj && input.getMode().equals(Mode.getM())) {
                        pred = input.getPred(0);
                        break inputs;
                    }
                }
            }

            node.setPred(0, pred);
        } else if (value.isConstant()) {
            Node constantNode = graph.newConst(value.getValue());
            Graph.exchange(node, constantNode);
        }
    }

}
