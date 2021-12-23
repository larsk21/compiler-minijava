package edu.kit.compiler.codegen;

import edu.kit.compiler.intermediate_lang.Block;
import edu.kit.compiler.intermediate_lang.Instruction;

import java.util.*;

/**
 * Arranges the given blocks in reverse postfix order and sets
 * the number of backrefs.
 */
public class ReversePostfixOrder {
    private Map<Integer, Block> blocks;
    private Set<Integer> visited = new HashSet<>();
    private List<Block> result = new ArrayList<>();

    private ReversePostfixOrder(Map<Integer, Block> blocks) {
        this.blocks = blocks;
    }

    public static List<Block> apply(Map<Integer, Block> blocks, int startBlock) {
        ReversePostfixOrder instance = new ReversePostfixOrder(blocks);
        instance.depthFirstSearch(blocks.get(startBlock));

        List<Block> reversed = new ArrayList<>();
        for (int i = instance.result.size() - 1; i >= 0; i--) {
            reversed.add(instance.result.get(i));
        }
        return reversed;
    }

    private void depthFirstSearch(Block block) {
        if (visited.contains(block.getBlockId())) {
            block.addBackRef();
            return;
        }

        assert block.getNumBackReferences() == 0;
        visited.add(block.getBlockId());
        // TODO: remove trivial jumps
        for (Instruction instr: block.getInstructions()) {
            if (instr.getJumpTarget().isPresent()) {
                int jmpTarget = instr.getJumpTarget().get();
                depthFirstSearch(blocks.get(jmpTarget));
            }
        }
        result.add(block);
    }
}
