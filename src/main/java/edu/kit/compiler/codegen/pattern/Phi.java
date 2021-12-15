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

public final class Phi implements Pattern<InstructionMatch> {

    public final Pattern<OperandMatch<Register>> pattern = OperandPattern.register();

    @Override
    public InstructionMatch match(Node node, MatcherState matcher) {
        if (node.getOpCode() == ir_opcode.iro_Phi) {
            var preds = StreamSupport
                .stream(node.getPreds().spliterator(), false)
                .map(pred -> pattern.match(pred, matcher))
                .collect(Collectors.toList());
            if (preds.stream().allMatch(match -> match.matches())) {
                return new PhiMatch(preds, matcher.getNewRegister());
            } else {
                return InstructionMatch.none();
            }
        } else {
            return InstructionMatch.none();
        }
    }

    // todo what about memory phi
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class PhiMatch extends InstructionMatch.Phi {

        private final List<OperandMatch<Register>> preds;
        private final int destination;

        @Override
        public PhiInstruction getPhiInstruction() {
            throw new UnsupportedOperationException("todo");
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
