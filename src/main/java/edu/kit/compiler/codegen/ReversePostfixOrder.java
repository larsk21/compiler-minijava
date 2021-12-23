package edu.kit.compiler.codegen;

import edu.kit.compiler.intermediate_lang.Block;
import edu.kit.compiler.intermediate_lang.Instruction;
import lombok.Data;

import java.util.*;

/**
 * Arranges the given blocks in reverse postfix order and sets
 * the number of backrefs.
 */
public class ReversePostfixOrder {
    private Map<Integer, Block> blocks;
    private Set<Integer> visited = new HashSet<>();

    private ReversePostfixOrder(Map<Integer, Block> blocks) {
        this.blocks = blocks;
    }

    public static List<Block> apply(Map<Integer, Block> blocks, int startBlock) {
        ReversePostfixOrder instance = new ReversePostfixOrder(blocks);
        List<Block> result = instance.depthFirstSearch(blocks.get(startBlock)).getResult();

        List<Block> reversed = new ArrayList<>();
        for (int i = result.size() - 1; i >= 0; i--) {
            reversed.add(result.get(i));
        }
        return reversed;
    }

    private DFSResult depthFirstSearch(Block block) {
        if (visited.contains(block.getBlockId())) {
            block.addBackRef();
            return new DFSResult(-1, new ArrayList<>(), true);
        }

        assert block.getNumBackReferences() == 0;
        visited.add(block.getBlockId());
        // TODO: remove trivial jumps

        List<DFSResult> children = new ArrayList<>();
        for (Instruction instr: block.getInstructions()) {
            if (instr.getJumpTarget().isPresent()) {
                int jmpTarget = instr.getJumpTarget().get();
                children.add(depthFirstSearch(blocks.get(jmpTarget)));
            }
        }
        assert children.size() <= 2;

        int lastChildId = -1;
        boolean alwaysEndsInBackref = children.size() > 0;
        List<Block> result = new ArrayList<>();
        // It is important that the loop body is arranged before the loop exit,
        // to enable efficient lifetime analysis.
        for (DFSResult child: children) {
            if (!child.isAlwaysEndsInBackref()) {
                alwaysEndsInBackref = false;
                result.addAll(child.getResult());
                lastChildId = child.getBlockId();
            }
        }
        for (DFSResult child: children) {
            if (child.isAlwaysEndsInBackref()) {
                result.addAll(child.getResult());
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
        return new DFSResult(block.getBlockId(), result, alwaysEndsInBackref);
    }

    @Data
    private static class DFSResult {
        private final int blockId;
        private final List<Block> result;
        private final boolean alwaysEndsInBackref;
    }
}
