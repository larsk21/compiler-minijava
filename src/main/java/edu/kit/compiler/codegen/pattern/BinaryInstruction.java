package edu.kit.compiler.codegen.pattern;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import edu.kit.compiler.codegen.NodeRegisters;
import edu.kit.compiler.codegen.Operand;
import edu.kit.compiler.codegen.Util;
import edu.kit.compiler.intermediate_lang.Instruction;
import firm.Mode;
import firm.bindings.binding_irnode.ir_opcode;
import firm.nodes.Node;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class BinaryInstruction implements Pattern<InstructionMatch> {

    // todo get rid of the booleans in the constructor
    // todo either with builder or enums

    // todo Question: is memory always 0-th predecessor?

    private final ir_opcode opcode;
    private final String command;
    private final Pattern<? extends OperandMatch<? extends Operand.Destination>> left;
    private final Pattern<OperandMatch<Operand.Register>> right;
    private final boolean overwritesRegister;
    private final boolean hasMemory;

    @Override
    public InstructionMatch match(Node node, NodeRegisters registers) {
        if (node.getOpCode() == opcode) {
            var offset = hasMemory ? 1 : 0;

            assert node.getPredCount() == 2 + offset;
            var lhs = left.match(node.getPred(offset), registers);
            var rhs = right.match(node.getPred(offset + 1), registers);

            if (lhs.matches() && rhs.matches()) {
                var mode = rhs.getOperand().getMode();
                var destination = getDestination(registers);
                return new BinaryInstructionMatch(lhs, rhs, destination, mode);
            } else {
                return InstructionMatch.none();
            }
        } else {
            return InstructionMatch.none();
        }
    }

    private Optional<Integer> getDestination(NodeRegisters registers) {
        if (overwritesRegister) {
            return Optional.of(registers.newRegister());
        } else {
            return Optional.empty();
        }
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public final class BinaryInstructionMatch extends InstructionMatch.Some {

        private final OperandMatch<? extends Operand.Destination> left;
        private final OperandMatch<Operand.Register> right;
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
