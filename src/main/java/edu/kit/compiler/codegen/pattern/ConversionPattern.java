package edu.kit.compiler.codegen.pattern;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import edu.kit.compiler.codegen.MatcherState;
import edu.kit.compiler.codegen.Operand;
import edu.kit.compiler.codegen.Util;
import edu.kit.compiler.intermediate_lang.Instruction;
import firm.bindings.binding_irnode.ir_opcode;
import firm.nodes.Node;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

public class ConversionPattern implements Pattern<InstructionMatch> {

    private static final Pattern<OperandMatch<Operand.Register>> REGISTER = OperandPattern.register();

    @Override
    public InstructionMatch match(Node node, MatcherState matcher) {
        if (node.getOpCode() == ir_opcode.iro_Conv) {
            var pred = node.getPred(0);
            var match = REGISTER.match(pred, matcher);
            if (match.matches()) {
                var targetSize = Util.getSize(node.getMode());
                var targetRegister = matcher.getNewRegister(targetSize);
                return new ConversionMatch(node, match, targetRegister);
            } else {
                return InstructionMatch.none();
            }
        } else {
            return InstructionMatch.none();
        }
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private static final class ConversionMatch extends InstructionMatch.Basic {

        private final Node node;
        private final OperandMatch<Operand.Register> source;
        private final int targetRegister;

        @Override
        public Node getNode() {
            return node;
        }

        @Override
        public List<Instruction> getInstructions() {
            var sourceRegister = source.getOperand().get();
            // I have no idea whatsoever if this condition is sufficient
            if (node.getMode().isSigned()) {
                return List.of(Instruction.newSignedMov(sourceRegister, targetRegister));
            } else {
                return List.of(Instruction.newUnsignedMov(sourceRegister, targetRegister));
            }
        }

        @Override
        public Optional<Integer> getTargetRegister() {
            return Optional.of(targetRegister);
        }

        @Override
        public Stream<Node> getPredecessors() {
            return source.getPredecessors();
        }

        @Override
        public Stream<Operand> getOperands() {
            return Stream.of(source.getOperand());
        }
    }
}
