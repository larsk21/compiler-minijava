package edu.kit.compiler.optimizations;

import static firm.bindings.binding_irgraph.ir_graph_properties_t.IR_GRAPH_PROPERTY_CONSISTENT_DOMINANCE;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import edu.kit.compiler.io.CommonUtil;
import edu.kit.compiler.optimizations.analysis.LoopInvariantAnalysis;
import edu.kit.compiler.optimizations.loop_invariant.MoveInvariantStrategies;

import firm.BackEdges;
import firm.Graph;
import firm.nodes.Block;
import firm.nodes.Node;

import lombok.Setter;

/**
 * Optimization that moves loop-invariant nodes in front of the loop. Invariant
 * nodes are moved to the deepest common dominator of the predecessors of the
 * loop entry point. Note that not all invariant nodes need to be moved by this
 * analysis.
 * 
 * This analysis respects the pin state of nodes. In addition, control flow and
 * Phi nodes are never moved.
 */
public class LoopInvariantOptimization implements Optimization.Local {

    /**
     * Strategy used to decide if invariant nodes should be moved. If set to
     * Optional.empty(), the optimization will pick a default strategy.
     */
    @Setter
    private Optional<MoveInvariantStrategy> moveInvariantStrategy = Optional.empty();

    @Override
    public boolean optimize(Graph graph, OptimizationState state) {
        graph.assureProperties(IR_GRAPH_PROPERTY_CONSISTENT_DOMINANCE);

        LoopInvariantAnalysis loopInvariantAnalysis = new LoopInvariantAnalysis(graph);
        loopInvariantAnalysis.analyze();

        Map<Block, Set<Block>> loops = loopInvariantAnalysis.getLoops();
        Map<Block, List<Node>> loopInvariantNodes = loopInvariantAnalysis.getLoopInvariantNodes();

        boolean backEdgesEnabled = BackEdges.enabled(graph);
        if (!backEdgesEnabled) {
            BackEdges.enable(graph);
        }

        MoveInvariantStrategy moveInvariantStrategy = this.moveInvariantStrategy.orElse(new MoveInvariantStrategies.MoveAlways());

        boolean change = false;
        for (Map.Entry<Block, Set<Block>> loop : loops.entrySet()) {
            Block loopEntryPoint = loop.getKey();
            Set<Block> loopBlocks = loop.getValue();

            Iterable<Block> loopHeaderPredecessors = CommonUtil.stream(loopEntryPoint.getPreds())
                .map(node -> (Block) node.getBlock())
                .collect(Collectors.toList());
            Block targetBlock = Util.findDeepestCommonDominator(loopHeaderPredecessors);

            if (targetBlock == null) {
                // loop header has no predecessors
                continue;
            }

            List<Node> invariantNodes = new ArrayList<>(loopInvariantNodes.get(loopEntryPoint));
            // we traverse the nodes in reverse postorder, such that the
            // heuristic knows if the current node is required to be moved by
            // its invariant successors
            Collections.reverse(invariantNodes);

            List<Node> moveableNodes = new ArrayList<>();
            for (Node node : invariantNodes) {
                if (!loopBlocks.contains(node.getBlock())) {
                    // node is invariant to an outer loop and was already moved
                    continue;
                }

                if (moveInvariantStrategy.decideMoveNodeTo(node, loopEntryPoint, targetBlock)) {
                    moveableNodes.add(node);
                }
            }

            // we traverse the moveable nodes in postorder, such that the
            // predecessors of a node are moved before the node
            Collections.reverse(moveableNodes);

            for (Node node : moveableNodes) {
                if (CommonUtil.stream(node.getPreds()).anyMatch(pred -> loopBlocks.contains(pred.getBlock()))) {
                    // heuristic decided not to move predecessor blocks
                    continue;
                }

                node.setBlock(targetBlock);
                change = true;
            }
        }

        if (!backEdgesEnabled) {
            BackEdges.disable(graph);
        }

        return change;
    }

    /**
     * Represents a strategy for deciding whether to move a loop-invariant node
     * outside the loop.
     * 
     * It is guaranteed that the nodes will be visited in reverse postorder,
     * i.e. the decision is made for the predecessors of a node before it is
     * made for the node.
     */
    public static interface MoveInvariantStrategy {

        /**
         * Decide whether to move the given loop-invariant node outside the
         * given loop to the given target block.
         */
        boolean decideMoveNodeTo(Node node, Block loop, Block target);

    }

}
