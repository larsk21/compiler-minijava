package edu.kit.compiler.codegen.pattern;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

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
public class BinaryInstructionPattern implements Pattern<InstructionMatch> {

    private final ir_opcode opcode;
    private final String command;
    private final Pattern<? extends OperandMatch<? extends Operand.Destination>> left;
    private final Pattern<OperandMatch<Operand.Register>> right;
    private final boolean overwritesRegister;
    private final boolean hasMemory;

    @Override
    public InstructionMatch match(Node node, MatcherState matcher) {
        if (node.getOpCode() == opcode) {
            var offset = hasMemory ? 1 : 0;

            assert node.getPredCount() == 2 + offset;
            var lhs = left.match(node.getPred(offset), matcher);
            var rhs = right.match(node.getPred(offset + 1), matcher);

            if (lhs.matches() && rhs.matches()) {
                var mode = getMode(node);
                var destination = getDestination(matcher::getNewRegister);
                return new BinaryInstructionMatch(node, lhs, rhs, destination, mode);
            } else {
                return InstructionMatch.none();
            }
        } else {
            return InstructionMatch.none();
        }
    }

    private Optional<Integer> getDestination(Supplier<Integer> register) {
        return overwritesRegister ? Optional.of(register.get()) : Optional.empty();
    }

    private Mode getMode(Node node) {
        return switch (node.getOpCode()) {
            case iro_Store -> {
                var store = (firm.nodes.Store) node;
                yield store.getType().getMode();
            }
            default -> node.getMode();
        };
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public final class BinaryInstructionMatch extends InstructionMatch.Basic {

        private final Node node;
        private final OperandMatch<? extends Operand.Destination> left;
        private final OperandMatch<Operand.Register> right;
        private final Optional<Integer> destination;
        private final Mode mode;

        @Override
        public Node getNode() {
            return node;
        }

        @Override
        public List<Instruction> getInstructions() {
            if (destination.isPresent()) {
                return List.of(getAsOperation());
            } else {
                return List.of(getAsInput());
            }
        }

        @Override
        public Optional<Integer> getTargetRegister() {
            return destination;
        }

        @Override
        public Stream<Node> getPredecessors() {
            var preds = Stream.concat(left.getPredecessors(), right.getPredecessors());
            if (hasMemory) {
                preds = Stream.concat(Stream.of(node.getPred(0)), preds);
            }

            return preds;
        }

        private Instruction getAsOperation() {
            assert destination.isPresent();

            var target = Operand.register(right.getOperand().getMode(), destination.get());

            var inputRegisters = getInputRegisters();
            var overwriteRegister = left.getOperand().getDestinationRegister();

            // make sure the overwritten register is not part of input registers
            if (overwriteRegister.isPresent()) {
                inputRegisters.remove(overwriteRegister.get());
            }

            return Instruction.newOp(
                    Util.formatCmd(command, Util.getSize(mode), right.getOperand(), target),
                    inputRegisters, overwriteRegister, destination.get());
        }

        private Instruction getAsInput() {
            assert !destination.isPresent();

            return Instruction.newInput(
                    Util.formatCmd(command, Util.getSize(mode), right.getOperand(), left.getOperand()),
                    getInputRegisters());
        }

        private List<Integer> getInputRegisters() {
            var input = new ArrayList<>(left.getOperand().getSourceRegisters());
            input.addAll(right.getOperand().getSourceRegisters());

            return input;
        }
    }
}
