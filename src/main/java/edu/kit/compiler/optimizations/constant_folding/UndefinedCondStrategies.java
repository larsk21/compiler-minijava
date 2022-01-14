package edu.kit.compiler.optimizations.constant_folding;

import java.util.HashMap;
import java.util.Map;

import edu.kit.compiler.optimizations.ConstantOptimization.UndefinedCondStrategy;

import firm.BackEdges;
import firm.BlockWalker;
import firm.Graph;
import firm.Mode;
import firm.TargetValue;
import firm.BackEdges.Edge;
import firm.nodes.Block;
import firm.nodes.Cond;
import firm.nodes.Node;
import firm.nodes.Proj;

import lombok.Getter;

/**
 * Collection of strategies for choosing a TargetValue for Cond nodes with an
 * unknown TargetValue.
 */
public class UndefinedCondStrategies {

    private UndefinedCondStrategies() {}

    /**
     * Naive strategy that always chooses BFalse.
     */
    public static class Naive implements UndefinedCondStrategy {

        @Override
        public TargetValue chooseCondValue(Cond cond) {
            return TargetValue.getBFalse();
        }

    }

    /**
     * Strategy that chooses the TargetValue in a way that, when used as the
     * selector for the Cond node, will lead to the most blocks being skipped
     * by the placed Jmp in a postorder of blocks.
     * 
     * This strategy requires back edges to be enabled.
     */
    public static class SkipMostBlocksInPostorder implements UndefinedCondStrategy {

        /**
         * Create a new strategy for skipping the most blocks in a postorder.
         */
        public SkipMostBlocksInPostorder(Graph graph) {
            this.graph = graph;

            init();
        }

        private Graph graph;

        private Map<Block, Integer> postorderPositions;

        private void init() {
            PostorderPositionBlockWalker walker = new PostorderPositionBlockWalker();
            graph.walkBlocksPostorder(walker);

            postorderPositions = walker.getPostorderPositions();
        }

        @Override
        public TargetValue chooseCondValue(Cond cond) {
            int largestSkipFalse = 0, largestSkipTrue = 0;

            for (Edge succEdge : BackEdges.getOuts(cond)) {
                Node succ = succEdge.node;

                if (succ instanceof Proj && succ.getMode().equals(Mode.getX())) {
                    Proj proj = (Proj)succ;

                    int position = findLargestPostorderPositionOfSuccessor(proj);

                    if (proj.getNum() == Cond.pnFalse) {
                        largestSkipFalse = Math.max(largestSkipFalse, position);
                    } else if (proj.getNum() == Cond.pnTrue) {
                        largestSkipTrue = Math.max(largestSkipTrue, position);
                    }
                }
            }

            if (largestSkipTrue > largestSkipFalse) {
                return TargetValue.getBTrue();
            } else {
                return TargetValue.getBFalse();
            }
        }

        private int findLargestPostorderPositionOfSuccessor(Node node) {
            int position = 0;

            for (Edge succEdge : BackEdges.getOuts(node)) {
                Node succ = succEdge.node;

                position = Math.max(position, postorderPositions.getOrDefault(succ, 0));
            }

            return position;
        }

        /**
         * BlockWalker assigning a postorder position to each visited block.
         */
        private static class PostorderPositionBlockWalker implements BlockWalker {

            private int position = 0;
            /**
             * Postorder positions for each visited block.
             */
            @Getter
            private Map<Block, Integer> postorderPositions = new HashMap<>();

            @Override
            public void visitBlock(Block block) {
                postorderPositions.put(block, position);
                position++;
            }

        }

    }

}
