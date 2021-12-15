package edu.kit.compiler.codegen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import edu.kit.compiler.codegen.pattern.InstructionMatch;
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

@RequiredArgsConstructor
public class BasicBlocks {

    @Getter
    private final Graph graph;

    private final HashMap<Integer, BlockEntry> blocks = new HashMap<>();

    public void register(Block block) {
        assert !blocks.containsKey(block.getNr());
        blocks.put(block.getNr(), new BlockEntry(block));
    }

    public BlockEntry getEntry(Node block) {
        return blocks.get(block.getNr());
    }

    @Override
    public String toString() {
        return graph.getEntity().getName() + ": " + blocks.toString();
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public final class BlockEntry {

        @Getter
        private final Block firmBlock;

        private final List<Instruction> instructions = new ArrayList<>();
        private final List<PhiInstruction> phiInstructions = new ArrayList<>();

        private Optional<ExitCondition> exitCondition = Optional.empty();

        public int getLabel() {
            return firmBlock.getNr();
        }

        public List<Instruction> getInstructions() {
            return Collections.unmodifiableList(instructions);
        }

        public List<PhiInstruction> getPhiInstructions() {
            return Collections.unmodifiableList(phiInstructions);
        }

        public List<Instruction> getExitInstructions() {
            return exitCondition
                .map(c -> Collections.unmodifiableList(c.getInstructions()))
                .orElseGet(() -> Collections.emptyList());
        }

        public void setExitCondition(ExitCondition exitCondition) {
            assert !this.exitCondition.isPresent();
            this.exitCondition = Optional.of(exitCondition);
        }

        public void setDestination(Proj node, Block block) {
            assert node.getBlock().getNr() == firmBlock.getNr();

            var entry = getEntry(block);
            var condition = exitCondition.get();
            if (node.getPred().getOpCode() == ir_opcode.iro_Cond) {
                if (node.getNum() == Cond.pnTrue) {
                    condition.setTrueBlock(entry);
                } else if (node.getNum() == Cond.pnFalse) {
                    condition.setFalseBlock(entry);
                }
            } else {
                throw new UnsupportedOperationException("other control flow not supported");
            }
        }

        public void setDestination(Jmp node, Block block) {
            assert node.getBlock().getNr() == firmBlock.getNr();

            var entry = getEntry(block);
            var condition = exitCondition.get();
            condition.setTrueBlock(entry);
        }

        public void add(Instruction instruction) {
            instructions.add(instruction);
        }

        public void append(InstructionMatch.Basic match) {
            assert match.matches();
            instructions.addAll(match.getInstructions());
        }

        public void addPhi(PhiInstruction instruction) {
            phiInstructions.add(instruction);
        }

        @Override
        public String toString() {
            var instructions = Stream.concat(getInstructions().stream(),
                    getExitInstructions().stream()).collect(Collectors.toList());
            return String.format("(%s, %s)", phiInstructions, instructions.toString());
        }
    }
}
