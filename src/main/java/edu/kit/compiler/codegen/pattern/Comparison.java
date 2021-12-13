package edu.kit.compiler.codegen.pattern;

import java.util.stream.Stream;

import edu.kit.compiler.codegen.ExitCondition;
import edu.kit.compiler.codegen.NodeRegisters;
import edu.kit.compiler.codegen.Operand;
import firm.Relation;
import firm.bindings.binding_irnode.ir_opcode;
import firm.nodes.Cmp;
import firm.nodes.Node;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

public class Comparison implements Pattern<ConditionMatch> {

    public final Pattern<OperandMatch<Operand.Register>> pattern = new RegisterPattern();

    @Override
    public ConditionMatch match(Node node, NodeRegisters registers) {
        if (node.getOpCode() == ir_opcode.iro_Cmp) {
            var cmp = (Cmp) node;
            var relation = cmp.getRelation();
            var left = pattern.match(cmp.getLeft(), registers);
            var right = pattern.match(cmp.getRight(), registers);

            if (left.matches() && right.matches()) {
                return new ComparisonMatch(relation, left, right);
            } else {
                return ConditionMatch.none();
            }
        } else {
            return ConditionMatch.none();
        }
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public final class ComparisonMatch extends ConditionMatch.Some {

        private final Relation relation;
        private final OperandMatch<Operand.Register> left;
        private final OperandMatch<Operand.Register> right;

        @Override
        public ExitCondition getCondition() {
            return ExitCondition.condition(relation, left.getOperand(), right.getOperand());
        }

        @Override
        public Stream<Node> getPredecessors() {
            return Stream.concat(left.getPredecessors(), right.getPredecessors());
        }

    }
}
