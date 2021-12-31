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
import firm.bindings.binding_irnode.ir_opcode;
import firm.nodes.Node;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public abstract class BinaryInstructionPattern<T extends Operand.Target, S extends Operand.Source>
        implements Pattern<InstructionMatch> {

    private final ir_opcode opcode;
    private final Pattern<? extends OperandMatch<T>> targetPattern;
    private final Pattern<? extends OperandMatch<S>> sourcePattern;
    private final int offset;
    private final boolean commutate;

    public static <T extends Operand.Target, S extends Operand.Source> BinaryInstructionPattern<T, S> of(
            ir_opcode opcode, String command, Pattern<? extends OperandMatch<T>> target,
            Pattern<? extends OperandMatch<S>> source, int offset, boolean commutate) {
        return new BinaryInstructionPattern<T, S>(opcode, target, source, offset, commutate) {
            @Override
            protected InstructionMatch getMatch(Node node, OperandMatch<T> target,
                    OperandMatch<S> source, MatcherState matcher) {
                var targetRegister = getTarget(target.getOperand(),
                        () -> matcher.getNewRegister(source.getOperand().getSize()));
                return new BinaryInstructionMatch(node, command, target, source, targetRegister);
            }
        };
    }

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
                return getMatch(node, targetMatch, sourceMatch, matcher);
            } else {
                return InstructionMatch.none();
            }
        } else {
            return InstructionMatch.none();
        }
    }

    protected abstract InstructionMatch getMatch(Node node, OperandMatch<T> target,
            OperandMatch<S> source, MatcherState matcher);

    private static Optional<Integer> getTarget(Operand.Target operand, IntSupplier register) {
        if (operand.getTargetRegister().isPresent()) {
            return Optional.of(register.getAsInt());
        } else {
            return Optional.empty();
        }
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public final class BinaryInstructionMatch extends InstructionMatch.Basic {

        private final Node node;
        private final String command;
        private final OperandMatch<T> target;
        private final OperandMatch<S> source;
        private final Optional<Integer> targetRegister;

        @Override
        public Node getNode() {
            return node;
        }

        @Override
        public List<Instruction> getInstructions() {
            return List.of(Instructions.newBinary(command, source.getOperand().getSize(),
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
