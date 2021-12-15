package edu.kit.compiler.codegen.pattern;

import java.util.Optional;
import java.util.stream.Stream;

import edu.kit.compiler.codegen.ExitCondition;
import edu.kit.compiler.codegen.MatcherState;
import edu.kit.compiler.codegen.Operand;
import firm.Relation;
import firm.bindings.binding_irnode.ir_opcode;
import firm.nodes.Cmp;
import firm.nodes.Node;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

public class ComparisonPattern implements Pattern<InstructionMatch> {

    public final Pattern<OperandMatch<Operand.Register>> pattern = OperandPattern.register();

    @Override
    public InstructionMatch match(Node node, MatcherState matcher) {
        if (node.getOpCode() == ir_opcode.iro_Cmp) {
            var cmp = (Cmp) node;
            var relation = cmp.getRelation();
            var left = pattern.match(cmp.getLeft(), matcher);
            var right = pattern.match(cmp.getRight(), matcher);

            if (left.matches() && right.matches()) {
                return new ComparisonMatch(node, relation, left, right);
            } else {
                return InstructionMatch.none();
            }
        } else {
            return InstructionMatch.none();
        }
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class ComparisonMatch extends InstructionMatch.Condition {

        private final Node node;
        private final Relation relation;
        private final OperandMatch<Operand.Register> left;
        private final OperandMatch<Operand.Register> right;

        @Override
        public Node getNode() {
            return node;
        }

        @Override
        public ExitCondition getCondition() {
            return ExitCondition.condition(relation,
                    left.getOperand(), right.getOperand());
        }

        @Override
        public Stream<Node> getPredecessors() {
            return Stream.concat(left.getPredecessors(), right.getPredecessors());
        }

        @Override
        public Optional<Integer> getTargetRegister() {
            return Optional.empty();
        }
    }
}
