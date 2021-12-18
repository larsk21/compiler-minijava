package edu.kit.compiler.codegen.pattern;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
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
    private final boolean hasMemory;

    @Override
    public InstructionMatch match(Node node, MatcherState matcher) {
        if (node.getOpCode() == opcode) {
            assert node.getPredCount() == 1 + getOffset();
            var match = operand.match(node.getPred(getOffset()), matcher);

            if (match.matches()) {
                var targetRegister = getTarget(match.getOperand(), matcher::getNewRegister);
                return new UnaryInstructionMatch(node, match, targetRegister);
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

    private Optional<Integer> getTarget(Operand.Target operand,
            Function<RegisterSize, Integer> register) {
        if (operand.getTargetRegister().isPresent()) {
            return Optional.of(register.apply(operand.getSize()));
        } else {
            return Optional.empty();
        }
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public final class UnaryInstructionMatch extends InstructionMatch.Basic {

        private final Node node;
        private final OperandMatch<? extends Operand.Target> source;
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
            var preds = source.getPredecessors();
            if (hasMemory) {
                preds = Stream.concat(Stream.of(node.getPred(0)), preds);
            }

            return preds;
        }

        private Instruction getAsOperation() {
            assert targetRegister.isPresent();

            var targetOperand = Operand.register(source.getOperand().getMode(), targetRegister.get());

            var inputRegisters = new ArrayList<>(source.getOperand().getSourceRegisters());
            var overwriteRegister = source.getOperand().getTargetRegister();

            // make sure the overwritten register is not part of input registers
            if (overwriteRegister.isPresent()) {
                inputRegisters.remove(overwriteRegister.get());
            }

            return Instruction.newOp(
                    Util.formatCmd(command, source.getOperand().getSize(), targetOperand),
                    inputRegisters, overwriteRegister, targetRegister.get());
        }

        private Instruction getAsInput() {
            assert !targetRegister.isPresent();

            return Instruction.newInput(
                    Util.formatCmd(command, source.getOperand().getSize(), source.getOperand()),
                    source.getOperand().getSourceRegisters());
        }
    }
}
