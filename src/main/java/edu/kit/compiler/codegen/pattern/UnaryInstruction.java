package edu.kit.compiler.codegen.pattern;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

import edu.kit.compiler.codegen.ExitCondition;
import edu.kit.compiler.codegen.MatcherState;
import edu.kit.compiler.codegen.Operand;
import edu.kit.compiler.codegen.Util;
import edu.kit.compiler.intermediate_lang.Instruction;
import firm.Mode;
import firm.bindings.binding_irnode.ir_opcode;
import firm.nodes.Node;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class UnaryInstruction implements Pattern<InstructionMatch> {

    private final ir_opcode opcode;
    private final String command;
    private final Pattern<? extends OperandMatch<? extends Operand.Destination>> operand;
    private final boolean overwritesRegister;
    private final boolean hasMemory;

    @Override
    public InstructionMatch match(Node node, MatcherState matcher) {
        if (node.getOpCode() == opcode) {
            assert node.getPredCount() == 1 + getOffset();
            var match = operand.match(node.getPred(getOffset()), matcher);

            if (match.matches()) {
                var mode = match.getOperand().getMode();
                var destination = getDestination(matcher::getNewRegister);
                return new UnaryInstructionMatch(node, match, destination, mode);
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

    private Optional<Integer> getDestination(Supplier<Integer> register) {
        return overwritesRegister ? Optional.of(register.get()) : Optional.empty();
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public final class UnaryInstructionMatch extends InstructionMatch.Some {

        private final Node node;
        private final OperandMatch<? extends Operand.Destination> match;
        private final Optional<Integer> destination;
        private final Mode mode;

        @Override
        public List<Instruction> getInstructions() {
            if (destination.isPresent()) {
                return Arrays.asList(getAsOperation());
            } else {
                return Arrays.asList(getAsInput());
            }
        }

        @Override
        public Optional<Integer> getTargetRegister() {
            return destination;
        }

        @Override
        public Stream<Node> getPredecessors() {
            var preds = match.getPredecessors();
            if (hasMemory) {
                preds = Stream.concat(Stream.of(node.getPred(0)), preds);
            }

            return preds;
        }

        @Override
        public Optional<ExitCondition> getCondition() {
            return Optional.empty();
        }

        private Instruction getAsOperation() {
            assert destination.isPresent();

            var target = Operand.register(match.getOperand().getMode(), destination.get());

            var inputRegisters = new ArrayList<>(match.getOperand().getSourceRegisters());
            var overwriteRegister = match.getOperand().getDestinationRegister();

            // make sure the overwritten register is not part of input registers
            if (overwriteRegister.isPresent()) {
                inputRegisters.remove(overwriteRegister.get());
            }

            return Instruction.newOp(
                    Util.formatCmd(command, Util.getSize(mode), target),
                    inputRegisters, overwriteRegister, destination.get());
        }

        private Instruction getAsInput() {
            assert !destination.isPresent();

            return Instruction.newInput(
                    Util.formatCmd(command, Util.getSize(mode), match.getOperand()),
                    match.getOperand().getSourceRegisters());
        }
    }
}
