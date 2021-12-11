package edu.kit.compiler.intermediate_lang;

import lombok.Data;

import java.util.List;

@Data
public class Block {

    /**
     * List of instructions that this block contains, the last instruction has to be the jump instruction
     */
    private final List<Instruction> instructions;
    private final List<Block> targets;
    private final List<Block> references;

    public Block(List<Instruction> instructions, List<Block> targets, List<Block> references) {
        // block has to contain at least one jump instruction
        assert instructions.size() > 1;
        this.instructions = instructions;
        this.targets = targets;
        this.references = references;
    }

}
