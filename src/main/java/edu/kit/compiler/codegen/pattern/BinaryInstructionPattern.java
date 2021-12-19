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
import firm.bindings.binding_irnode.ir_opcode;
import firm.nodes.Node;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class BinaryInstructionPattern implements Pattern<InstructionMatch> {

    private final ir_opcode opcode;
    private final String command;
    private final Pattern<? extends OperandMatch<? extends Operand.Target>> left;
    private final Pattern<? extends OperandMatch<? extends Operand.Source>> right;
    private final boolean hasMemory;


    @Override
    public InstructionMatch match(Node node, MatcherState matcher) {
        if (node.getOpCode() == opcode) {
            var offset = hasMemory ? 1 : 0;

            assert node.getPredCount() == 2 + offset;
            var leftMatch = left.match(node.getPred(offset), matcher);
            var rightMatch = right.match(node.getPred(offset + 1), matcher);

            if (leftMatch.matches() && rightMatch.matches()) {
                // todo needs to be changed once swap option is implemented
                var size = rightMatch.getOperand().getSize();
                var targetRegister = getTarget(leftMatch.getOperand(),
                        () -> matcher.getNewRegister(size));
                return new BinaryInstructionMatch(node, leftMatch, rightMatch, targetRegister);
            } else {
                return InstructionMatch.none();
            }
        } else {
            return InstructionMatch.none();
        }
    }

    private Optional<Integer> getTarget(Operand.Target operand, Supplier<Integer> register) {
        if (operand.getTargetRegister().isPresent()) {
            return Optional.of(register.get());
        } else {
            return Optional.empty();
        }
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public final class BinaryInstructionMatch extends InstructionMatch.Basic {

        private final Node node;
        private final OperandMatch<? extends Operand.Target> target;
        private final OperandMatch<? extends Operand.Source> source;
        private final Optional<Integer> targetRegister;

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
            var preds = Stream.concat(target.getPredecessors(), source.getPredecessors());
            if (hasMemory) {
                preds = Stream.concat(Stream.of(node.getPred(0)), preds);
            }

            return preds;
        }

        private Instruction getAsOperation() {
            assert targetRegister.isPresent();

            var mode = source.getOperand().getMode();
            var size = source.getOperand().getSize();

            var targetOperand = Operand.register(mode, targetRegister.get());

            var inputRegisters = getInputRegisters();
            var overwriteRegister = target.getOperand().getTargetRegister();

            // make sure the overwritten register is not part of input registers
            if (overwriteRegister.isPresent()) {
                // the while loop ensures that all occurences are removed
                while (inputRegisters.remove(overwriteRegister.get()));
            }

            return Instruction.newOp(
                    Util.formatCmd(command, size, source.getOperand(), targetOperand),
                    inputRegisters, overwriteRegister, targetRegister.get());
        }

        private Instruction getAsInput() {
            assert !targetRegister.isPresent();

            var size = source.getOperand().getSize();
            return Instruction.newInput(
                    Util.formatCmd(command, size, source.getOperand(), target.getOperand()),
                    getInputRegisters());
        }

        private List<Integer> getInputRegisters() {
            var input = new ArrayList<>(target.getOperand().getSourceRegisters());
            input.addAll(source.getOperand().getSourceRegisters());

            return input;
        }
    }
}
