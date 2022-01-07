package edu.kit.compiler.codegen.pattern;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
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
public class UnaryInstructionPattern implements Pattern<InstructionMatch> {

    private final ir_opcode opcode;
    private final String command;
    private final Pattern<? extends OperandMatch<? extends Operand.Target>> operand;
    private final int offset;

    @Override
    public InstructionMatch match(Node node, MatcherState matcher) {
        if (node.getOpCode() == opcode) {
            assert node.getPredCount() == 1 + offset;
            var match = operand.match(node.getPred(offset), matcher);

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

    private Optional<Integer> getTarget(Operand.Target operand,
            Function<RegisterSize, Integer> register) {
        if (operand.getTargetRegister().isPresent()) {
            return Optional.of(register.apply(operand.getSize()));
        } else {
            return Optional.empty();
        }
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private final class UnaryInstructionMatch extends InstructionMatch.Basic {

        private final Node node;
        private final OperandMatch<? extends Operand.Target> source;
        private final Optional<Integer> targetRegister;

        @Override
        public Node getNode() {
            return node;
        }

        @Override
        public List<Instruction> getInstructions() {
            return List.of(Instructions.newUnary(command, source.getOperand().getSize(),
                    source.getOperand(), targetRegister));
        }

        @Override
        public Optional<Integer> getTargetRegister() {
            return targetRegister;
        }

        @Override
        public Stream<Node> getPredecessors() {
            return Stream.concat(
                    Util.streamPreds(node).limit(offset),
                    source.getPredecessors());
        }

        @Override
        public Stream<Operand> getOperands() {
            return Stream.of(source.getOperand());
        }
    }
}
