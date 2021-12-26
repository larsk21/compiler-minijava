package edu.kit.compiler.codegen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import edu.kit.compiler.intermediate_lang.Instruction;
import firm.Graph;
import firm.bindings.binding_irnode.ir_opcode;
import firm.nodes.Block;
import firm.nodes.Cond;
import firm.nodes.Jmp;
import firm.nodes.Node;
import firm.nodes.Proj;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Represents the basic blocks of a function.
 */
public class BasicBlocks {

    @Getter
    private final Graph graph;

    private final HashMap<Integer, BlockEntry> blocks = new HashMap<>();

    private int currentLabel;

    public BasicBlocks(Graph graph, int jumpLabel) {
        this.graph = graph;
        this.currentLabel = jumpLabel;
    }

    /**
     * Return the entry for the given Firm block. The caller must ensure that
     * the node is actually a block;
     */
    public BlockEntry getEntry(Node block) {
        assert block.getOpCode() == ir_opcode.iro_Block;
        return blocks.computeIfAbsent(block.getNr(),
                n -> new BlockEntry((Block) block, newLabel()));
    }

    public int newLabel() {
        return currentLabel++;
    }

    public Collection<BlockEntry> getEntries() {
        return blocks.values();
    }

    public BlockEntry getStartBlock() {
        return blocks.get(graph.getStartBlock().getNr());
    }

    @Override
    public String toString() {
        return graph.getEntity().getName() + ": " + blocks.toString();
    }

    /**
     * Represents an entry associated with a Firm Block.
     */
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public final class BlockEntry {

        @Getter
        private final Block firmBlock;
        private final int jumpLabel;

        private final List<Instruction> instructions = new ArrayList<>();
        private final List<PhiInstruction> phiInstructions = new ArrayList<>();

        @Getter
        private Optional<ExitCondition> exitCondition = Optional.empty();

        /**
         * Return the jump label of the block.
         */
        public int getLabel() {
            return jumpLabel;
        }

        /**
         * Return the basic instructions of this block.
         */
        public List<Instruction> getInstructions() {
            return Collections.unmodifiableList(instructions);
        }

        /**
         * Return the Phi instruction defined in this block
         */
        public List<PhiInstruction> getPhiInstructions() {
            return Collections.unmodifiableList(phiInstructions);
        }

        /**
         * Return the instructions used to exit this block.
         */
        public List<Instruction> getExitInstructions() {
            return exitCondition
                    .map(c -> Collections.unmodifiableList(c.getInstructions()))
                    .orElseGet(() -> Collections.emptyList());
        }

        /**
         * Set the exit condition of this block.
         */
        public void setExitCondition(ExitCondition exitCondition) {
            assert !this.exitCondition.isPresent();
            this.exitCondition = Optional.of(exitCondition);
        }

        /**
         * Set the jump destination of this block to the given Firm block.
         * The Proj node is used to determine which destination (true or false)
         * needs to be set.
         */
        public void setDestination(Proj node, Block block) {
            assert node.getBlock().getNr() == firmBlock.getNr();

            var entry = getEntry(block);
            var condition = exitCondition.get();
            if (node.getPred().getOpCode() == ir_opcode.iro_Cond) {
                if (node.getNum() == Cond.pnTrue) {
                    condition.setTrueBlock(entry.getLabel());
                } else if (node.getNum() == Cond.pnFalse) {
                    condition.setFalseBlock(entry.getLabel());
                } else {
                    throw new IllegalStateException();
                }
            } else {
                throw new UnsupportedOperationException("other control flow not supported");
            }
        }

        /**
         * Set the jump destination of this block the given Firm block.
         */
        public void setDestination(Jmp node, Block block) {
            assert node.getBlock().getNr() == firmBlock.getNr();

            var entry = getEntry(block);
            var condition = exitCondition.get();
            condition.setTrueBlock(entry.getLabel());
        }

        /**
         * Add the given instruction to the regular instructions of this block.
         */
        public void add(Instruction instruction) {
            instructions.add(instruction);
        }

        /**
         * Add the given instructions to the regular instructions of this block.
         */
        public void append(List<Instruction> instructions) {
            this.instructions.addAll(instructions);
        }

        /**
         * Add the given Phi instruction to the Phi instructions of this block.
         */
        public void addPhi(PhiInstruction instruction) {
            phiInstructions.add(instruction);
        }

        @Override
        public String toString() {
            var instructions = Stream.concat(getInstructions().stream(),
                    getExitInstructions().stream()).collect(Collectors.toList());
            return String.format("(.L%d: %s, %s)", jumpLabel, phiInstructions, instructions.toString());
        }
    }
}
