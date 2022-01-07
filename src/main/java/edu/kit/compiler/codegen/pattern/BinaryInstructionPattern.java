package edu.kit.compiler.codegen.pattern;

import java.util.List;
import java.util.Optional;
import java.util.function.IntSupplier;
import java.util.stream.Stream;

import edu.kit.compiler.codegen.Instructions;
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
public final class BinaryInstructionPattern implements Pattern<InstructionMatch> {

    private final ir_opcode opcode;
    private final String command;
    private final Pattern<? extends OperandMatch<? extends Operand.Target>> targetPattern;
    private final Pattern<? extends OperandMatch<? extends Operand.Source>> sourcePattern;
    private final int offset;
    private final boolean commutate;

    @Override
    public InstructionMatch match(Node node, MatcherState matcher) {
        if (node.getOpCode() == opcode) {
            assert node.getPredCount() == 2 + offset;
            var targetMatch = targetPattern.match(node.getPred(offset), matcher);
            var sourceMatch = sourcePattern.match(node.getPred(offset + 1), matcher);

            if (commutate && (!targetMatch.matches() || !sourceMatch.matches())) {
                targetMatch = targetPattern.match(node.getPred(offset + 1), matcher);
                sourceMatch = sourcePattern.match(node.getPred(offset), matcher);
            }

            if (targetMatch.matches() && sourceMatch.matches()) {
                var size = getSize(node, targetMatch.getOperand(), sourceMatch.getOperand());
                var targetRegister = getTarget(targetMatch.getOperand(),
                        () -> matcher.getNewRegister(size));
                return new BinaryInstructionMatch(node, targetMatch, sourceMatch, targetRegister, size);
            } else {
                return InstructionMatch.none();
            }
        } else {
            return InstructionMatch.none();
        }
    }

    private static Optional<Integer> getTarget(Operand.Target operand, IntSupplier register) {
        if (operand.getTargetRegister().isPresent()) {
            return Optional.of(register.getAsInt());
        } else {
            return Optional.empty();
        }
    }

    private static RegisterSize getSize(Node node, Operand target, Operand source) {
        return switch (node.getOpCode()) {
            case iro_Shl, iro_Shr, iro_Shrs -> target.getSize();
            default -> source.getSize();
        };
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private final class BinaryInstructionMatch extends InstructionMatch.Basic {

        private final Node node;
        private final OperandMatch<? extends Operand.Target> target;
        private final OperandMatch<? extends Operand.Source> source;
        private final Optional<Integer> targetRegister;
        private final RegisterSize size;

        @Override
        public Node getNode() {
            return node;
        }

        @Override
        public List<Instruction> getInstructions() {
            return List.of(Instructions.newBinary(command, size,
                    target.getOperand(), source.getOperand(), targetRegister));
        }

        @Override
        public Optional<Integer> getTargetRegister() {
            return targetRegister;
        }

        @Override
        public Stream<Node> getPredecessors() {
            return Stream.concat(
                    Util.streamPreds(node).limit(offset),
                    Stream.concat(source.getPredecessors(), target.getPredecessors()));
        }

        @Override
        public Stream<Operand> getOperands() {
            return Stream.of(target.getOperand(), source.getOperand());
        }
    }
}
