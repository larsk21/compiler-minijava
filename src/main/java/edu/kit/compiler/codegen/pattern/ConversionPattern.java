package edu.kit.compiler.codegen.pattern;

import java.util.List;
import java.util.Optional;
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

public class ConversionPattern implements Pattern<InstructionMatch> {

    public final Pattern<OperandMatch<Operand.Register>> pattern = OperandPattern.register();

    @Override
    public InstructionMatch match(Node node, MatcherState matcher) {
        if (node.getOpCode() == ir_opcode.iro_Conv) {
            var pred = node.getPred(0);
            var match = pattern.match(pred, matcher);
            if (match.matches()) {
                var from = pred.getMode();
                var to = node.getMode();
                var targetRegister = matcher.getNewRegister(Util.getSize(to));
                return new ConversionMatch(node, match, targetRegister, from, to);
            } else {
                return InstructionMatch.none();
            }
        } else {
            return InstructionMatch.none();
        }
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class ConversionMatch extends InstructionMatch.Basic {

        private final Node node;
        private final OperandMatch<Operand.Register> match;
        private final int targetRegister;
        private final Mode from;
        private final Mode to;

        @Override
        public Node getNode() {
            return node;
        }

        @Override
        public List<Instruction> getInstructions() {
            var target = Operand.register(to, targetRegister);
            return List.of(Instruction.newOp(
                    Util.formatCmd(getCmd(), Util.getSize(to), match.getOperand(), target),
                    List.of(match.getOperand().get()),
                    Optional.empty(),
                    targetRegister));
        }

        @Override
        public Optional<Integer> getTargetRegister() {
            return Optional.of(targetRegister);
        }

        @Override
        public Stream<Node> getPredecessors() {
            return match.getPredecessors();
        }

        public String getCmd() {
            // So far only Is -> Ls and Ls -> Is is implemented
            if (from.equals(Mode.getIs()) && to.equals(Mode.getLs())) {
                return "movsl";
            } else if (from.equals(Mode.getLs()) && to.equals(Mode.getIs())) {
                return "mov";
            } else {
                throw new UnsupportedOperationException("not supported yet");
            }
        }
    }
}
