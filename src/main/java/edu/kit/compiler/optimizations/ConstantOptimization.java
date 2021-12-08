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
            transform(node, nodeValues.getOrDefault(node, TargetValueLatticeElement.unknown()));
        }

        binding_irgopt.remove_bads(graph.ptr);
        binding_irgopt.remove_unreachable_code(graph.ptr);
        binding_irgopt.remove_bads(graph.ptr);
    }

    /**
     * Transform the given node with the given associated lattice element if
     * that element is constant.
     */
    private void transform(Node node, TargetValueLatticeElement value) {
        if (node instanceof Proj && node.getMode().isData()) {
            transformResultProj((Proj)node);
        } else if (node instanceof Proj && node.getMode().equals(Mode.getM())) {
            transformMemoryProj((Proj)node);
        } else if (node instanceof Proj && node.getMode().equals(Mode.getX())) {
            transformControlFlowProj((Proj)node);
        } else if (node.getMode().isData() && value.isConstant()) {
            Node constantNode = graph.newConst(value.getValue());
            Graph.exchange(node, constantNode);
        } else if (node.getMode().isData() && value.isUnknown()) {
            Node badNode = graph.newBad(node.getMode());
            Graph.exchange(node, badNode);
        }
    }

    /**
     * Replace the result projection with a Const having the same value as its
     * predecessor.
     */
    private void transformResultProj(Proj node) {
        Node pred = node.getPred(0);
        TargetValueLatticeElement predValue;
        if (nodeValues.containsKey(pred) && (predValue = nodeValues.get(pred)).isConstant()) {
            Node constantNode = graph.newConst(predValue.getValue());
            Graph.exchange(node, constantNode);
        }
    }

    /**
     * Replace the predecessor of the memory projection with the first node
     * that does not have a constant value following the memory dependency
     * chain.
     */
    private void transformMemoryProj(Proj node) {
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
    }

    /**
     * Replace the control flow projection with a Jmp or Bad depending on the
     * constant value of the Cond selector.
     */
    private void transformControlFlowProj(Proj node) {
        Node pred = node.getPred(0);
        TargetValueLatticeElement predValue;
        if (pred instanceof Cond && (predValue = nodeValues.get(pred)).isConstant()) {
            if (
                (node.getNum() == pn_Cond.pn_Cond_false.val && predValue.getValue().equals(TargetValue.getBFalse())) ||
                (node.getNum() == pn_Cond.pn_Cond_true.val && predValue.getValue().equals(TargetValue.getBTrue()))
            ) {
                Node jmpNode = graph.newJmp(node.getBlock());
                Graph.exchange(node, jmpNode);
            } else if (
                (node.getNum() == pn_Cond.pn_Cond_false.val && predValue.getValue().equals(TargetValue.getBTrue())) ||
                (node.getNum() == pn_Cond.pn_Cond_true.val && predValue.getValue().equals(TargetValue.getBFalse()))
            ) {
                Node badNode = graph.newBad(Mode.getX());
                Graph.exchange(node, badNode);
            }
        }
    }

}
