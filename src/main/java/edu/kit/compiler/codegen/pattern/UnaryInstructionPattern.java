package edu.kit.compiler.codegen.pattern;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

import edu.kit.compiler.codegen.MatcherState;
import edu.kit.compiler.codegen.Operand;
import edu.kit.compiler.codegen.Util;
import edu.kit.compiler.intermediate_lang.Instruction;
import edu.kit.compiler.intermediate_lang.RegisterSize;
import firm.bindings.binding_irnode.ir_opcode;
import firm.nodes.Node;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class UnaryInstructionPattern implements Pattern<InstructionMatch> {

    private final ir_opcode opcode;
    private final String command;
    private final Pattern<? extends OperandMatch<? extends Operand.Target>> operand;
    private final boolean overwritesRegister;
    private final boolean hasMemory;

    @Override
    public InstructionMatch match(Node node, MatcherState matcher) {
        if (node.getOpCode() == opcode) {
            assert node.getPredCount() == 1 + getOffset();
            var match = operand.match(node.getPred(getOffset()), matcher);

            if (match.matches()) {
                var size = Util.getSize(match.getOperand().getMode());
                var targetRegister = getTarget(() -> matcher.getNewRegister(size));
                return new UnaryInstructionMatch(node, match, targetRegister, size);
            } else {
                return InstructionMatch.none();
            }
        } else {
            return InstructionMatch.none();
        }
    }

    private int getOffset() {
        return hasMemory ? 1 : 0;
    }

    private Optional<Integer> getTarget(Supplier<Integer> register) {
        return overwritesRegister ? Optional.of(register.get()) : Optional.empty();
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public final class UnaryInstructionMatch extends InstructionMatch.Basic {

        private final Node node;
        private final OperandMatch<? extends Operand.Target> match;
        private final Optional<Integer> targetRegister;
        private final RegisterSize size;

        @Override
        public Node getNode() {
            return node;
        }

        @Override
        public List<Instruction> getInstructions() {
            if (targetRegister.isPresent()) {
                return List.of(getAsOperation());
            } else {
                return List.of(getAsInput());
            }
        }

        @Override
        public Optional<Integer> getTargetRegister() {
            return targetRegister;
        }

        @Override
        public Stream<Node> getPredecessors() {
            var preds = match.getPredecessors();
            if (hasMemory) {
                preds = Stream.concat(Stream.of(node.getPred(0)), preds);
            }

            return preds;
        }

        private Instruction getAsOperation() {
            assert targetRegister.isPresent();

            var targetOperand = Operand.register(match.getOperand().getMode(), targetRegister.get());

            var inputRegisters = new ArrayList<>(match.getOperand().getSourceRegisters());
            var overwriteRegister = match.getOperand().getTargetRegister();

            // make sure the overwritten register is not part of input registers
            if (overwriteRegister.isPresent()) {
                inputRegisters.remove(overwriteRegister.get());
            }

            return Instruction.newOp(
                    Util.formatCmd(command, size, targetOperand),
                    inputRegisters, overwriteRegister, targetRegister.get());
        }

        private Instruction getAsInput() {
            assert !targetRegister.isPresent();

            return Instruction.newInput(
                    Util.formatCmd(command, size, match.getOperand()),
                    match.getOperand().getSourceRegisters());
        }
    }
}
