package edu.kit.compiler.optimizations;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import edu.kit.compiler.optimizations.constant_folding.ConstantAnalysis;
import edu.kit.compiler.optimizations.constant_folding.TargetValueLatticeElement;
import edu.kit.compiler.optimizations.constant_folding.UndefinedCondStrategies;
import edu.kit.compiler.optimizations.Util.NodeListFiller;

import firm.BackEdges;
import firm.Graph;
import firm.Mode;
import firm.TargetValue;
import firm.BackEdges.Edge;
import firm.bindings.binding_irgopt;
import firm.nodes.Cond;
import firm.nodes.Const;
import firm.nodes.Node;
import firm.nodes.Proj;
import firm.nodes.Unknown;

/**
 * Optimization that finds constant values for value nodes where possible and
 * replaces these node with constant nodes in the given graph. In addition,
 * control flow with constant conditions is removed.
 * 
 * If Unknown nodes are found as an argument to an operator or Cond node, the
 * operator or Cond node is removed. The resulting program behavior for the
 * operation is undefined.
 */
public class ConstantOptimization implements Optimization.Local {

    private Graph graph;
    private Map<Node, TargetValueLatticeElement> nodeValues;

    private UndefinedCondStrategy undefinedCondStrategy;

    @Override
    public boolean optimize(Graph graph, OptimizationState state) {
        this.graph = graph;

        ConstantAnalysis analysis = new ConstantAnalysis(graph);
        analysis.analyze();

        this.nodeValues = analysis.getNodeValues();

        this.undefinedCondStrategy = new UndefinedCondStrategies.SkipMostBlocksInPostorder(graph);

        // we transform the nodes in reverse postorder, i.e. we can access the
        // unchanged predecessors of a node when transforming it
        List<Node> nodes = new ArrayList<>();
        graph.walkPostorder(new NodeListFiller(nodes, true));

        BackEdges.enable(graph);

        boolean changes = false;
        for (Node node : nodes) {
            changes |= transform(node, nodeValues.getOrDefault(node, TargetValueLatticeElement.unknown()));
        }

        BackEdges.disable(graph);

        binding_irgopt.remove_bads(graph.ptr);
        binding_irgopt.remove_unreachable_code(graph.ptr);
        binding_irgopt.remove_bads(graph.ptr);

        return changes;
    }

    /**
     * Transform the given node with the given associated lattice element if
     * that element is constant.
     */
    private boolean transform(Node node, TargetValueLatticeElement value) {
        if (node instanceof Const) {
            return false;
        } else if (node instanceof Proj && node.getMode().equals(Mode.getM())) {
            return transformMemoryProj((Proj)node);
        } else if (node instanceof Proj && node.getMode().equals(Mode.getX())) {
            return transformControlFlowProj((Proj)node);
        } else if (node.getMode().isData() && value.isConstant()) {
            Node constantNode = graph.newConst(value.getValue());
            Graph.exchange(node, constantNode);

            return true;
        } else if (node.getMode().isData() && value.isUnknown() && !(node instanceof Unknown)) {
            Node unknownNode = graph.newUnknown(node.getMode());
            Graph.exchange(node, unknownNode);

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
        Node pred = node.getPred();
        if (nodeValues.containsKey(pred) && nodeValues.get(pred).isConstant()) {
            Node predMem = graph.newBad(Mode.getM());
            inputs: for (Node input : pred.getPreds()) {
                if (input.getMode().equals(Mode.getM())) {
                    predMem = input;
                    break inputs;
                }
            }

            boolean changes = false;
            for (Edge edge : BackEdges.getOuts(node)) {
                edge.node.setPred(edge.pos, predMem);
                changes = true;
            }

            return changes;
        } else {
            return false;
        }
    }

    /**
     * Replace the control flow projection with a Jmp or Bad depending on the
     * constant value of the Cond selector.
     */
    private boolean transformControlFlowProj(Proj node) {
        Node pred = node.getPred();
        if (pred instanceof Cond) {
            TargetValueLatticeElement predValue = nodeValues.getOrDefault(pred, TargetValueLatticeElement.unknown());

            if (predValue.isUnknown()) {
                predValue = TargetValueLatticeElement.constant(undefinedCondStrategy.chooseCondValue((Cond)pred));
                nodeValues.put(pred, predValue);
            }

            if (predValue.isConstant()) {
                if (
                    (node.getNum() == Cond.pnFalse && predValue.getValue().equals(TargetValue.getBFalse())) ||
                    (node.getNum() == Cond.pnTrue && predValue.getValue().equals(TargetValue.getBTrue()))
                ) {
                    Node jmpNode = graph.newJmp(node.getBlock());
                    Graph.exchange(node, jmpNode);

                    return true;
                } else if (
                    (node.getNum() == Cond.pnFalse && predValue.getValue().equals(TargetValue.getBTrue())) ||
                    (node.getNum() == Cond.pnTrue && predValue.getValue().equals(TargetValue.getBFalse()))
                ) {
                    Node badNode = graph.newBad(Mode.getX());
                    Graph.exchange(node, badNode);

                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Represents a strategy for choosing either BFalse or BTrue as TargetValue
     * for a Cond node with an unknown TargetValue.
     */
    public static interface UndefinedCondStrategy {

        /**
         * Choose either BFalse or BTrue as TargetValue for the given Cond
         * node.
         */
        TargetValue chooseCondValue(Cond cond);

    }

}
