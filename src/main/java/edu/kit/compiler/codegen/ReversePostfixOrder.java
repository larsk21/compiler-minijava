package edu.kit.compiler.codegen;

import edu.kit.compiler.intermediate_lang.Block;
import edu.kit.compiler.intermediate_lang.Instruction;
import lombok.Data;

import java.util.*;

/**
 * Arranges the given blocks and sets the number of backreferences for each block.
 *
 * The order that is calculated:
 *  - is a reverse postfix order (and thus a topological order except for loops)
 *  - ensures that the blocks of each loop are in contiguous order without holes
 *  - sets the number of backrefs for each loop header correctly
 *    (backrefs = for how many loops is this block the loop header?)
 *  - sets the loop depth of each block correctly, specifically ensuring that e.g.
 *    a return block that is embedded in a loop is assigned the same loop depth
 *    as the surrounding blocks in the loop
 *
 *  This is (more or less) the best possible block layout for linear scan register
 *  allocation. However, the second condition is surprisingly hard to correctly
 *  calculate in the general case (i.e. with multiple nested loops and complicated
 *  control flow). Therefore, we need a separate step that analyses the loop depth
 *  of the blocks.
 */
public class ReversePostfixOrder {
    private Map<Integer, Block> blocks;
    private Set<Integer> visited = new HashSet<>();
    private Map<Integer, Set<Integer>> loopsPerBlock;

    private ReversePostfixOrder(Map<Integer, Block> blocks, Map<Integer, Set<Integer>> loopsPerBlock) {
        this.blocks = blocks;
        this.loopsPerBlock = loopsPerBlock;
    }

    public static List<Block> apply(Map<Integer, Block> blocks, int startBlock) {
        var loopsPerBlock = new LoopDepthAnalysis(blocks).run(startBlock);

        ReversePostfixOrder instance = new ReversePostfixOrder(blocks, loopsPerBlock);
        List<Block> result = instance.depthFirstSearch(blocks.get(startBlock)).getResult();
        instance.setFinalLoopDepth(result);

        List<Block> reversed = new ArrayList<>();
        for (int i = result.size() - 1; i >= 0; i--) {
            reversed.add(result.get(i));
        }
        return reversed;
    }

    private DFSResult depthFirstSearch(Block block) {
        if (visited.contains(block.getBlockId())) {
            return new DFSResult(block.getBlockId(), new ArrayList<>());
        }

        visited.add(block.getBlockId());

        List<DFSResult> children = new ArrayList<>();
        for (Instruction instr: block.getInstructions()) {
            if (instr.getJumpTarget().isPresent()) {
                int jmpTarget = instr.getJumpTarget().get();
                children.add(depthFirstSearch(blocks.get(jmpTarget)));
            }
        }
        assert children.size() <= 2;

        // It is important that the loop body is arranged before the loop exit,
        // to enable efficient lifetime analysis.
        boolean outputInReverseOrder = (children.size() == 2) &&
                loopsPerBlock.get(children.get(0).getBlockId()).size()
                        > loopsPerBlock.get(children.get(1).getBlockId()).size();
        if (outputInReverseOrder) {
            var tmp = children.get(0);
            children.set(0, children.get(1));
            children.set(1, tmp);
        }
        List<Block> result = new ArrayList<>();
        int lastChildId = -1;
        for (DFSResult child: children) {
            result.addAll(child.getResult());
            if (child.getResult().size() > 0) {
                lastChildId = child.getBlockId();
            }
        }

        // remove trivial jump
        List<Instruction> instrs = block.getInstructions();
        Instruction lastInstr = instrs.get(instrs.size() - 1);
        if (lastInstr.getJumpTarget().isPresent() && lastInstr.getJumpTarget().get() == lastChildId) {
            instrs.remove(instrs.size() - 1);
        }

        result.add(block);
        return new DFSResult(block.getBlockId(), result);
    }

    /**
     * Calculates the loop depth of all blocks. Also,
     * "flattens" the loop depth for e.g. return blocks within a loop.
     * This is necessary so that the lifetime analysis can detect loops
     * correctly.
     */
    private void setFinalLoopDepth(List<Block> blocks) {
        Set<Integer> visitedLabels = new HashSet<>();
        Set<Integer> previousLabels = Set.of();
        for (int i = 0; i < blocks.size(); i++) {
            // iterate through all blocks and look for new labels
            Block b = blocks.get(i);
            Set<Integer> currentLabels = loopsPerBlock.get(b.getBlockId());
            if (!currentLabels.equals(previousLabels)) {
                Set<Integer> difference = new HashSet<>(currentLabels);
                difference.removeAll(previousLabels);
                for (int label: difference) {
                    if (visitedLabels.contains(label)) {
                        // we need to fill the "hole" in the loop labels
                        addLabelBackwards(blocks, label, i - 1);
                    } else {
                        visitedLabels.add(label);
                    }
                }
            }
        }

        for (Block b: blocks) {
            int depth = loopsPerBlock.get(b.getBlockId()).size();
            b.setBlockLoopDepth(depth);
        }
    }

    private void addLabelBackwards(List<Block> blocks, int label, int lastIndex) {
        for (int j = lastIndex;;  j--) {
            Set<Integer> currentLabels = loopsPerBlock.get(blocks.get(j).getBlockId());
            if (currentLabels.contains(label)) {
                break;
            } else {
                currentLabels.add(label);
            }
        }
    }

    @Data
    private static class DFSResult {
        private final int blockId;
        private final List<Block> result;
    }

    private static Map<Integer, List<Integer>> getBackEdges(Map<Integer, Block> blocks) {
        Map<Integer, List<Integer>> result = new HashMap<>();
        for (int blockId: blocks.keySet()) {
            result.put(blockId, new ArrayList<>());
        }
        for (Block b: blocks.values()) {
            for (Instruction instr: b.getInstructions()) {
                if (instr.getJumpTarget().isPresent()) {
                    int jmpTarget = instr.getJumpTarget().get();
                    result.get(jmpTarget).add(b.getBlockId());
                }
            }
        }
        return result;
    }

    private static class LoopDepthAnalysis {
        private Map<Integer, Block> blocks;
        private Map<Integer, List<Integer>> backEdges;

        /**
         * Maps each block to the set of loops which contain the block.
         * (Loops are represented by the block id of the loop header.)
         */
        private Map<Integer, Set<Integer>> loopsPerBlock = new HashMap<>();
        private Set<Integer> visited = new HashSet<>();
        private Set<Integer> active = new HashSet<>();

        private LoopDepthAnalysis(Map<Integer, Block> blocks) {
            this.blocks = blocks;
            this.backEdges = getBackEdges(blocks);
            for (int blockId: blocks.keySet()) {
                loopsPerBlock.put(blockId, new HashSet<>());
            }
        }

        public Map<Integer, Set<Integer>> run(int startBlock) {
            depthFirstSearch(blocks.get(startBlock), -1);
            return loopsPerBlock;
        }

        private void depthFirstSearch(Block block, int previousId) {
            int blockId = block.getBlockId();
            if (active.contains(blockId)) {
                // we don't want to add a backref of the same block two times
                if (!loopsPerBlock.get(blockId).contains(blockId)) {
                    block.addBackRef();
                }

                // backtrack and add the loop to all found blocks
                Deque<Integer> queue = new ArrayDeque<>();
                queue.push(previousId);
                while (!queue.isEmpty()) {
                    int id = queue.pop();
                    var loops = loopsPerBlock.get(id);
                    if (!loops.contains(blockId) && id != blockId) {
                        queue.addAll(backEdges.get(id));
                    }
                    loops.add(blockId);
                }
                return;
            } else if (visited.contains(blockId)) {
                return;
            }

            assert block.getNumBackReferences() == 0;
            visited.add(blockId);
            active.add(blockId);

            for (Instruction instr: block.getInstructions()) {
                if (instr.getJumpTarget().isPresent()) {
                    int jmpTarget = instr.getJumpTarget().get();
                    depthFirstSearch(blocks.get(jmpTarget), blockId);
                }
            }

            active.remove(blockId);
        }
    }
}
