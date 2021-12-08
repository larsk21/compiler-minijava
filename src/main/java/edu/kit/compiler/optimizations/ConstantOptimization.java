package edu.kit.compiler.optimizations;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import edu.kit.compiler.optimizations.constant_folding.ConstantAnalysis;
import edu.kit.compiler.optimizations.constant_folding.TargetValueLatticeElement;

import firm.Graph;
import firm.Mode;
import firm.TargetValue;
import firm.bindings.binding_irgopt;
import firm.bindings.binding_irnode.pn_Cond;
import firm.nodes.Cond;
import firm.nodes.Const;
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

        boolean changes;
        do {
            ConstantAnalysis analysis = new ConstantAnalysis(graph);
            analysis.analyze();

            this.nodeValues = analysis.getNodeValues();

            // we transform the nodes in reverse postorder, i.e. we can access the
            // unchanged predecessors of a node when transforming it
            List<Node> nodes = new ArrayList<>();
            graph.walkPostorder(new NodeVisitor.Default() {

                @Override
                public void defaultVisit(Node node) {
                    nodes.add(0, node);
                }

            });

            changes = false;
            for (Node node : nodes) {
                changes |= transform(node, nodeValues.getOrDefault(node, TargetValueLatticeElement.unknown()));
            }

            binding_irgopt.remove_bads(graph.ptr);
            binding_irgopt.remove_unreachable_code(graph.ptr);
            binding_irgopt.remove_bads(graph.ptr);
        } while (changes);
    }

    /**
     * Transform the given node with the given associated lattice element if
     * that element is constant.
     */
    private boolean transform(Node node, TargetValueLatticeElement value) {
        if (node instanceof Const) {
            return false;
        } else if (node instanceof Proj && node.getMode().isData()) {
            return transformResultProj((Proj)node);
        } else if (node instanceof Proj && node.getMode().equals(Mode.getM())) {
            return transformMemoryProj((Proj)node);
        } else if (node instanceof Proj && node.getMode().equals(Mode.getX())) {
            return transformControlFlowProj((Proj)node);
        } else if (node.getMode().isData() && value.isConstant()) {
            Node constantNode = graph.newConst(value.getValue());
            Graph.exchange(node, constantNode);

            return true;
        } else if (node.getMode().isData() && value.isUnknown()) {
            Node badNode = graph.newBad(node.getMode());
            Graph.exchange(node, badNode);

            return true;
        } else {
            return false;
        }
    }

    /**
     * Replace the result projection with a Const having the same value as its
     * predecessor.
     */
    private boolean transformResultProj(Proj node) {
        Node pred = node.getPred(0);
        TargetValueLatticeElement predValue;
        if (nodeValues.containsKey(pred) && (predValue = nodeValues.get(pred)).isConstant()) {
            Node constantNode = graph.newConst(predValue.getValue());
            Graph.exchange(node, constantNode);

            return true;
        } else {
            return false;
        }
    }

    /**
     * Replace the predecessor of the memory projection with the first node
     * that does not have a constant value following the memory dependency
     * chain.
     */
    private boolean transformMemoryProj(Proj node) {
        Node pred = node.getPred(0);
        boolean anySteps = false;
        while (nodeValues.containsKey(pred) && nodeValues.get(pred).isConstant()) {
            inputs: for (Node input : pred.getPreds()) {
                if (input instanceof Proj && input.getMode().equals(Mode.getM())) {
                    pred = input.getPred(0);
                    break inputs;
                }
            }
            anySteps = true;
        }

        node.setPred(0, pred);
        return anySteps;
    }

    /**
     * Replace the control flow projection with a Jmp or Bad depending on the
     * constant value of the Cond selector.
     */
    private boolean transformControlFlowProj(Proj node) {
        Node pred = node.getPred(0);
        TargetValueLatticeElement predValue;
        if (pred instanceof Cond && (predValue = nodeValues.get(pred)).isConstant()) {
            if (
                (node.getNum() == pn_Cond.pn_Cond_false.val && predValue.getValue().equals(TargetValue.getBFalse())) ||
                (node.getNum() == pn_Cond.pn_Cond_true.val && predValue.getValue().equals(TargetValue.getBTrue()))
            ) {
                Node jmpNode = graph.newJmp(node.getBlock());
                Graph.exchange(node, jmpNode);

                return true;
            } else if (
                (node.getNum() == pn_Cond.pn_Cond_false.val && predValue.getValue().equals(TargetValue.getBTrue())) ||
                (node.getNum() == pn_Cond.pn_Cond_true.val && predValue.getValue().equals(TargetValue.getBFalse()))
            ) {
                Node badNode = graph.newBad(Mode.getX());
                Graph.exchange(node, badNode);

                return true;
            }
        }

        return false;
    }

}
