package edu.kit.compiler.intermediate_lang;

import lombok.Data;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Data
public class Block {
    @Getter
    private final List<Instruction> instructions;

    /**
     * The unique id of the block.
     */
    @Getter
    private final int blockId;

    /**
     * Number of jumps to this block along a backwards edge,
     * i.e. where the jump appears after this block.
     */
    @Getter
    private int numBackReferences;

    public Block(List<Instruction> instructions, int blockId, int numBackReferences) {
        this.instructions = new ArrayList<>(instructions);
        this.blockId = blockId;
        this.numBackReferences = numBackReferences;
    }

    public Block(int blockId) {
        this(new ArrayList<>(), blockId, 0);
    }

    public void addBackRef() {
        numBackReferences++;
    }

    @Override
    public String toString() {
        return String.format("(.L%d: %s)", blockId, instructions.toString());
    }
}
