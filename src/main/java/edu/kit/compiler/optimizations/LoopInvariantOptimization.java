package edu.kit.compiler.optimizations;

import static firm.bindings.binding_irgraph.ir_graph_properties_t.IR_GRAPH_PROPERTY_CONSISTENT_DOMINANCE;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import edu.kit.compiler.io.CommonUtil;
import edu.kit.compiler.optimizations.analysis.LoopInvariantAnalysis;
import edu.kit.compiler.optimizations.loop_invariant.MoveInvariantStrategies;

import firm.Graph;
import firm.nodes.Block;
import firm.nodes.Node;

/**
 * Optimization that moves loop-invariant nodes in front of the loop. Invariant
 * nodes are moved to the deepest common dominator of the predecessors of the
 * loop entry point. Nodes invariant to inner blocks are moved as far out as
 * possible.
 * 
 * This analysis respects the pin state of nodes. In addition, control flow and
 * Phi nodes are never moved.
 */
public class LoopInvariantOptimization implements Optimization.Local {

    @Override
    public boolean optimize(Graph graph, OptimizationState state) {
        graph.assureProperties(IR_GRAPH_PROPERTY_CONSISTENT_DOMINANCE);

        LoopInvariantAnalysis loopInvariantAnalysis = new LoopInvariantAnalysis(graph);
        loopInvariantAnalysis.analyze();

        Map<Block, List<Node>> loopInvariantNodes = loopInvariantAnalysis.getLoopInvariantNodes();

        MoveInvariantStrategy moveInvariantStrategy = new MoveInvariantStrategies.MoveAlways();

        boolean change = false;
        for (Map.Entry<Block, Set<Block>> loop : loopInvariantAnalysis.getLoops().entrySet()) {
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

            for (Node node : loopInvariantNodes.get(loopEntryPoint)) {
                if (!loopBlocks.contains(node.getBlock())) {
                    // node is invariant to an outer loop and was already moved
                    continue;
                } else if (CommonUtil.stream(node.getPreds()).anyMatch(pred -> loopBlocks.contains(pred.getBlock()))) {
                    // heuristic decided not to move predecessor blocks
                    continue;
                } else if (!moveInvariantStrategy.decideMoveNodeTo(node, targetBlock)) {
                    // heuristic decided not to move this node
                    continue;
                }

                node.setBlock(targetBlock);
                change = true;
            }
        }

        return change;
    }

    /**
     * Represents a strategy for deciding whether to move a loop-invariant node
     * outside the loop.
     */
    public static interface MoveInvariantStrategy {

        /**
         * Decide whether to move the given loop-invariant node outside the
         * loop to the given target block.
         */
        boolean decideMoveNodeTo(Node node, Block target);

    }

}
