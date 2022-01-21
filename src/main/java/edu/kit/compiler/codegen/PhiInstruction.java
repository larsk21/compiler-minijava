package edu.kit.compiler.codegen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import firm.Mode;
import firm.nodes.Block;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Represents a Phi instruction used during instruction selection. A Phi
 * instruction corresponds to a Phi node in the Firm graph. These must be
 * translated to multiple IL instructions at a later stage.
 */
@RequiredArgsConstructor
public final class PhiInstruction {

    private final List<Entry> entries = new ArrayList<>();

    /**
     * The target register of the Phi instruction.
     */
    @Getter
    private final int targetRegister;

    /**
     * The mode of the corresponding Phi node.
     */
    @Getter
    private final Mode mode;

    public List<Entry> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    /**
     * Add an entry, corresponding to a predecessor of Phi node. The entry may
     * be interpreted as follows: If the control flow left the given block
     * before entering the block of the Phi node, load the given register into
     * the target register of the Phi instruction.
     *
     * Important: In order for the phi resolver to work correctly, the entries must
     * be added in order of the firm predecessors.
     */
    public void addEntry(Block block, Operand.Register register) {
        entries.add(new Entry(block, register));
    }

    @Override
    public String toString() {
        var source = entries.stream().map(e -> e.toString())
                .collect(Collectors.joining(", "));

        return String.format("phi %s -> @%d", source, targetRegister);
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class Entry {
        @Getter
        private final Block predBlock;

        @Getter
        private final Operand.Register operand;

        @Override
        public String toString() {
            return "@" + operand.get();
        }

        public int getRegister() {
            return operand.get();
        }
    }
}
