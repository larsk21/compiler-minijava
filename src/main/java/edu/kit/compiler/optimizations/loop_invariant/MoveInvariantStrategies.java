package edu.kit.compiler.optimizations.loop_invariant;

import edu.kit.compiler.optimizations.LoopInvariantOptimization.MoveInvariantStrategy;

import firm.nodes.Block;
import firm.nodes.Node;

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
        public boolean decideMoveNodeTo(Node node, Block target) {
            return true;
        }

    }

}
