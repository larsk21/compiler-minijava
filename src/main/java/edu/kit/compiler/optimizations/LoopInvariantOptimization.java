package edu.kit.compiler.optimizations;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import edu.kit.compiler.io.CommonUtil;
import edu.kit.compiler.optimizations.analysis.LoopInvariantAnalysis;

import firm.Graph;
import firm.nodes.Block;
import firm.nodes.Node;

/**
 * Optimization that moves loop-invariant nodes in front of the loop. Invariant
 * nodes are moved to the deepest common dominator of the predecessors of the
 * loop entry point. Nodes invariant to inner blocks are moved as far out as
 * possible.
 * 
 * Control flow, memory and Phi nodes are not affected.
 */
public class LoopInvariantOptimization implements Optimization.Local {

    @Override
    public boolean optimize(Graph graph, OptimizationState state) {
        LoopInvariantAnalysis loopInvariantAnalysis = new LoopInvariantAnalysis(graph);
        loopInvariantAnalysis.analyze();

        Map<Block, Set<Node>> loopInvariantNodes = loopInvariantAnalysis.getLoopInvariantNodes();

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
                if (loopBlocks.contains(node.getBlock())) {
                    node.setBlock(targetBlock);
                    change = true;
                }
            }
        }

        return change;
    }

}
