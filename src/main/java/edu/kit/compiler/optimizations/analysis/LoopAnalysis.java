package edu.kit.compiler.optimizations.analysis;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import edu.kit.compiler.optimizations.Util.BlockSuccessorMapper;

import firm.Graph;
import firm.nodes.Block;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Analysis that finds all loops in a graph. Loops consist of a loop entry
 * point (not necessarily the loop header) and loop blocks (including the loop
 * entry point). If a loop contains another loop, then the loop blocks of the
 * outer loop will also contain the loop blocks of the inner loop.
 * 
 * In addition, a mapping from blocks to the loops in which they are contained
 * is computed.
 */
@RequiredArgsConstructor
public class LoopAnalysis {

    private final Graph graph;

    /**
     * All loops in the graph.
     * 
     * The loop entry point is the map key, while the loop blocks are the
     * value.
     */
    @Getter
    private Map<Block, Set<Block>> loops = new HashMap<>();
    /**
     * Map with all loops in which the key block is contained.
     */
    @Getter
    private Map<Block, Set<Block>> blockLoops = new HashMap<>();

    private Map<Block, Set<Block>> successors = new HashMap<>();

    private Set<Block> visited = new HashSet<>();
    private Set<Block> active = new HashSet<>();

    /**
     * Analyze the given graph to find loops.
     */
    public void analyze() {
        BlockSuccessorMapper blockSuccessorMapper = new BlockSuccessorMapper(successors);
        graph.walkBlocksPostorder(blockSuccessorMapper);

        dfs(graph.getStartBlock());
    }

    /**
     * Depth-first seach though the blocks of a graph. The blocks are traversed
     * in control flow order, i.e. from start to end.
     */
    private Set<Block> dfs(Block block) {
        if (isActive(block)) {
            // loop (re-)discovered
            loops.computeIfAbsent(block, item -> new HashSet<>()).add(block);
            blockLoops.get(block).add(block);

            // return this as active loop
            return Set.of(block);
        } else if (isVisited(block)) {
            // continue loop if inside
            Set<Block> activeLoops = new HashSet<>(blockLoops.get(block));
            activeLoops.remove(block);
            return activeLoops;
        }

        // initialize block
        blockLoops.put(block, new HashSet<>());

        markActive(block);

        // find directly surrounding loops
        Set<Block> directLoops = new HashSet<>();
        for (Block succ : successors.get(block)) {
            directLoops.addAll(dfs(succ));
        }

        markInactive(block);

        // loop ends when moving from the loop entry point to its predecessor
        directLoops.remove(block);

        Set<Block> loopBlocks;
        if (loops.containsKey(block)) {
            // if this block is the loop entry point, propagate loops blocks to
            // outer surrounding loops
            loopBlocks = loops.get(block);
        } else {
            // else only add this block to its directly surrounding loops
            loopBlocks = Set.of(block);
        }

        // update block loops with directly surrounding loops
        for (Block loopBlock : loopBlocks) {
            blockLoops.get(loopBlock).addAll(directLoops);
        }

        // add blocks to directly surrounding loops
        for (Block directLoop : directLoops) {
            loops.get(directLoop).addAll(loopBlocks);
        }

        markVisited(block);

        // return directly surrounding loops to predecessors
        return directLoops;
    }

    /**
     * If the given block was visited before. This includes that the edges to
     * all its successors were followed.
     */
    private boolean isVisited(Block block) {
        return visited.contains(block);
    }

    /**
     * Mark the given block as visited. This includes that the edges to all its
     * successors were followed.
     */
    private void markVisited(Block block) {
        visited.add(block);
    }

    /**
     * If the given block is active. A block is active if at least one incoming
     * edge, but not all outgoing edges were followed.
     */
    private boolean isActive(Block block) {
        return active.contains(block);
    }

    /**
     * Mark the given block as active. A block is active if at least one
     * incoming edge, but not all outgoing edges were followed.
     */
    private void markActive(Block block) {
        active.add(block);
    }

    /**
     * Mark the given block as inactive. A block is active if at least one
     * incoming edge, but not all outgoing edges were followed.
     */
    private void markInactive(Block block) {
        active.remove(block);
    }

}
