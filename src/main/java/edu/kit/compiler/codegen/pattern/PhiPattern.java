package edu.kit.compiler.codegen.pattern;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import edu.kit.compiler.codegen.MatcherState;
import edu.kit.compiler.codegen.Operand;
import edu.kit.compiler.codegen.PhiInstruction;
import edu.kit.compiler.codegen.Util;
import firm.Mode;
import firm.bindings.binding_irnode.ir_opcode;
import firm.nodes.Node;
import firm.nodes.Phi;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

public final class PhiPattern implements Pattern<InstructionMatch> {

    public final Pattern<OperandMatch<Operand.Register>> pattern = OperandPattern.register();

    @Override
    public InstructionMatch match(Node node, MatcherState matcher) {
        if (node.getOpCode() != ir_opcode.iro_Phi) {
            return InstructionMatch.none();
        } else if (node.getMode().equals(Mode.getM())) {
            // I'm not sure this assertion holds.
            assert ((Phi) node).getLoop() > 0;

            return InstructionMatch.empty(node, StreamSupport
                    .stream(node.getPreds().spliterator(), false)
                    .collect(Collectors.toList()));
        } else {
            // Again, not sure this holds.
            assert ((Phi) node).getLoop() == 0;

            var preds = StreamSupport
                    .stream(node.getPreds().spliterator(), false)
                    .map(pred -> pattern.match(pred, matcher))
                    .collect(Collectors.toList());
            if (preds.stream().allMatch(match -> match.matches())) {
                var size = Util.getSize(node.getMode());
                var targetRegister = matcher.getPhiRegister(node, size);
                return new PhiMatch(node, preds, targetRegister);
            } else {
                return InstructionMatch.none();
            }
        }
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private static final class PhiMatch extends InstructionMatch.Phi {

        private final Node node;
        private final List<OperandMatch<Operand.Register>> preds;
        private final int targetRegister;

        @Override
        public Node getNode() {
            return node;
        }

        @Override
        public PhiInstruction getPhiInstruction() {
            var phi = new PhiInstruction(targetRegister, node.getMode());

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
            return Optional.of(targetRegister);
        }

        @Override
        public Stream<Node> getPredecessors() {
            return preds.stream().flatMap(match -> match.getPredecessors());
        }
    }
}
