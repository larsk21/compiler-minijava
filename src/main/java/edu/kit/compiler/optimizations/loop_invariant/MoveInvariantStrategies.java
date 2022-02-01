package edu.kit.compiler.optimizations.loop_invariant;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import edu.kit.compiler.io.CommonUtil;
import edu.kit.compiler.optimizations.NodeCosts;
import edu.kit.compiler.optimizations.Util;
import edu.kit.compiler.optimizations.LoopInvariantOptimization.MoveInvariantStrategy;

import firm.BackEdges;
import firm.nodes.Address;
import firm.nodes.Block;
import firm.nodes.Call;
import firm.nodes.Const;
import firm.nodes.NoMem;
import firm.nodes.Node;
import firm.nodes.Phi;
import firm.nodes.Proj;

import lombok.RequiredArgsConstructor;

/**
 * Collection of strategies for deciding whether to move a loop-invariant node
 * outside the loop.
 */
public final class MoveInvariantStrategies {

    private MoveInvariantStrategies() {}

    /**
     * Strategy that moves all loop-invariant nodes.
     */
    public static class MoveAlways implements MoveInvariantStrategy {

        @Override
        public boolean decideMoveNodeTo(Node node, Block loop, Block target) {
            return true;
        }

    }

    /**
     * Strategy that moves no loop-invariant nodes.
     */
    public static class MoveNever implements MoveInvariantStrategy {

        @Override
        public boolean decideMoveNodeTo(Node node, Block loop, Block target) {
            return false;
        }

    }

    /**
     * Strategy that moves as much loop-invariant nodes as possible with the
     * restriction that cheap nodes that are not required by other invariant
     * nodes and for that a move would increase the register pressure around
     * the loop, are not moved.
     */
    @RequiredArgsConstructor
    public static class MinimizeRegisterPressure implements MoveInvariantStrategy {

        /**
         * Loops as found by LoopAnalysis.
         */
        private final Map<Block, Set<Block>> loops;
        /**
         * Loop invariant nodes as found by LoopInvariantAnalysis.
         */
        private final Map<Block, List<Node>> loopInvariantNodes;

        /**
         * Set of nodes for each loop for which this heuristic decided to move them.
         */
        private final Map<Block, Set<Node>> loopMoveableNodes = new HashMap<>();

        @Override
        public boolean decideMoveNodeTo(Node node, Block loop, Block target) {
            if (NodeCosts.getCost(node) <= 0) {
                // Firm nodes that are not mapped to a CPU instruction can be
                // moved freely
                return true;
            } else if (!isCheap(node)) {
                // nodes that are more expensive than a load from memory should
                // always be moved
                return true;
            }

            long deltaLoopInEdges = deltaLoopInEdges(node, loop);
            // ignore the distance necessary to move a node outside any loop
            int moveDistance = moveDistance(node, target) - (node.getBlock().equals(loop) ? 1 : 2);
            long moveableSuccessors = moveableSuccessors(node, loop);

            // nodes with a high move distance should not be moved
            // nodes with an increase in loop in-edges should not be moved
            // nodes that are required to be moved by successors for which this
            //   heuristic decided to move them, should be moved
            if (moveDistance + deltaLoopInEdges > moveableSuccessors) {
                return false;
            } else {
                // remember that this heuristic decided to move the node
                loopMoveableNodes.computeIfAbsent(loop, item -> new HashSet<>()).add(node);
                return true;
            }
        }

        /**
         * Get if the execution of the given node is cheaper than a load from
         * memory. The result depends on the estimates in NodeCosts.
         */
        private boolean isCheap(Node node) {
            if (node instanceof Call) {
                return false;
            } else {
                return NodeCosts.cheaperThanLoad(NodeCosts.getCost(node));
            }
        }

        /**
         * Calculate the change in loop-in edge when moving the given node
         * outside the given loop.
         */
        private long deltaLoopInEdges(Node node, Block loop) {
            Set<Block> loopBlocks = loops.get(loop);
            List<Node> invariantNodes = loopInvariantNodes.get(loop);

            // in-edge is removed if a predecessor outside the loop has no other successor in the loop
            long removedInEdges = CommonUtil.stream(node.getPreds())
                .distinct()
                .filter(pred -> !(pred instanceof Const) && !(pred instanceof Address) && !(pred instanceof NoMem))
                .filter(pred -> !loopBlocks.contains(pred.getBlock()) || invariantNodes.contains(pred))
                .filter(pred -> CommonUtil.stream(BackEdges.getOuts(pred))
                    .map(edge -> edge.node)
                    .noneMatch(succ -> loopBlocks.contains(succ.getBlock()) && !invariantNodes.contains(succ))
                )
                .count();

            // in-edge is added if a successor is not loop-invariant
            long addedInEdges = CommonUtil.stream(BackEdges.getOuts(node))
                .map(edge -> edge.node)
                .flatMap(this::skipProjs)
                .filter(succ -> !(succ instanceof Phi))
                .anyMatch(succ -> loopBlocks.contains(succ.getBlock()) && !invariantNodes.contains(succ)) ?
                1 : 0;

            return addedInEdges - removedInEdges;
        }

        /**
         * Get the successors of a node, skipping Proj nodes.
         */
        private Stream<Node> skipProjs(Node node) {
            if (node instanceof Proj) {
                return CommonUtil.stream(BackEdges.getOuts(node))
                    .map(edge -> edge.node)
                    .flatMap(this::skipProjs);
            } else {
                return Stream.of(node);
            }
        }

        /**
         * Calculate the move distance of the given node to the given target
         * block. The move distance is measure in steps where each step is from
         * a block to the immediate dominator of the block. If the immediate
         * dominator of a block is not one of its direct predecessors, then the
         * step is counted twice.
         */
        private int moveDistance(Node node, Block target) {
            int distance = 0;

            Block block = (Block) node.getBlock();
            // try to move to the target block, but never further
            while (!block.equals(target) && Util.findDeepestCommonDominator(Arrays.asList(block, target)).equals(target)) {
                Block immediateDominator = Util.getImmediateDominator(block);

                distance += CommonUtil.stream(block.getPreds())
                    .map(pred -> pred.getBlock())
                    .anyMatch(predBlock -> predBlock.equals(immediateDominator)) ?
                    1 : 2;

                block = immediateDominator;
            }

            return distance;
        }

        /**
         * Get the successors of the given node for which this heuristic
         * decided to move them out of the given loop.
         */
        private long moveableSuccessors(Node node, Block loop) {
            Set<Node> moveableNodes = loopMoveableNodes.get(loop);

            return CommonUtil.stream(BackEdges.getOuts(node))
                .map(edge -> edge.node)
                .distinct()
                .filter(succ -> !(succ instanceof Phi))
                .filter(succ -> moveableNodes.contains(succ))
                .count();
        }

    }

}
