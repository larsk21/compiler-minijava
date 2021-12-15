package edu.kit.compiler.codegen.pattern;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import edu.kit.compiler.codegen.MatcherState;
import edu.kit.compiler.codegen.Operand.Register;
import edu.kit.compiler.codegen.PhiInstruction;
import firm.bindings.binding_irnode.ir_opcode;
import firm.nodes.Node;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

public final class PhiPattern implements Pattern<InstructionMatch> {

    public final Pattern<OperandMatch<Register>> pattern = OperandPattern.register();

    @Override
    public InstructionMatch match(Node node, MatcherState matcher) {
        if (node.getOpCode() == ir_opcode.iro_Phi) {
            var preds = StreamSupport
                    .stream(node.getPreds().spliterator(), false)
                    .map(pred -> pattern.match(pred, matcher))
                    .collect(Collectors.toList());
            if (preds.stream().allMatch(match -> match.matches())) {
                return new PhiMatch(node, preds, matcher.getNewRegister());
            } else {
                return InstructionMatch.none();
            }
        } else {
            return InstructionMatch.none();
        }
    }

    // todo what about memory phi
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private static final class PhiMatch extends InstructionMatch.Phi {

        private final Node node;
        private final List<OperandMatch<Register>> preds;
        private final int destination;

        @Override
        public Node getNode() {
            return node;
        }

        @Override
        public PhiInstruction getPhiInstruction() {
            var phi = new PhiInstruction(destination, node.getMode());

            assert node.getPredCount() == node.getBlock().getPredCount();
            for (int i = 0; i < node.getPredCount(); ++i) {
                var register = preds.get(i).getOperand().get();
                var predBlock = node.getBlock().getPred(i);
                phi.addEntry(predBlock, register);
            }

            return phi;
        }

        @Override
        public Optional<Integer> getTargetRegister() {
            return Optional.of(destination);
        }

        @Override
        public Stream<Node> getPredecessors() {
            return preds.stream().flatMap(match -> match.getPredecessors());
        }
    }
}
