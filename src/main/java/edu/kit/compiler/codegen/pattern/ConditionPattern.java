package edu.kit.compiler.codegen.pattern;

import java.util.Optional;
import java.util.stream.Stream;

import edu.kit.compiler.codegen.ExitCondition;
import edu.kit.compiler.codegen.MatcherState;
import firm.nodes.Node;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

public final class ConditionPattern implements Pattern<InstructionMatch> {

    @Override
    public InstructionMatch match(Node node, MatcherState matcher) {
        return switch (node.getOpCode()) {
            case iro_Jmp -> new ConditionMatch(node) {
                @Override
                public Stream<Node> getPredecessors() {
                    return Stream.empty();
                }

                @Override
                public ExitCondition getCondition() {
                    return ExitCondition.unconditional();
                }
            };
            case iro_Cond -> new ConditionMatch(node) {
                @Override
                public Stream<Node> getPredecessors() {
                    return Stream.of(node.getPred(0));
                }

                @Override
                public ExitCondition getCondition() {
                    var match = matcher.getMatch(node.getPred(0));

                    // todo so ... this is unfortunate
                    if (match instanceof InstructionMatch.Condition) {
                        return ((InstructionMatch.Condition) match).getCondition();
                    } else {
                        throw new IllegalStateException();
                    }
                }

            };
            default -> throw new UnsupportedOperationException(
                    "other exit conditions not supported");
        };
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private static abstract class ConditionMatch extends InstructionMatch.Condition {

        protected final Node node;

        @Override
        public Node getNode() {
            return node;
        }

        @Override
        public Optional<Integer> getTargetRegister() {
            return Optional.empty();
        }
    }
}
