package edu.kit.compiler.codegen.pattern;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import edu.kit.compiler.codegen.ExitCondition;
import edu.kit.compiler.codegen.MatcherState;
import edu.kit.compiler.codegen.Operand;
import edu.kit.compiler.codegen.Operand.Immediate;
import edu.kit.compiler.codegen.Operand.Register;
import firm.Relation;
import firm.bindings.binding_irnode.ir_opcode;
import firm.nodes.Cmp;
import firm.nodes.Node;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ConditionPattern {
    @RequiredArgsConstructor
    public static abstract class Conditional<S extends Operand.Source, T extends Operand.Source>
            implements Pattern<InstructionMatch> {

        private final Pattern<? extends OperandMatch<S>> left;
        private final Pattern<? extends OperandMatch<T>> right;
        protected final boolean swapOperands;

        @Override
        public InstructionMatch match(Node node, MatcherState matcher) {
            if (node.getOpCode() == ir_opcode.iro_Cond &&
                    node.getPred(0).getOpCode() == ir_opcode.iro_Cmp) {
                var offset = swapOperands ? 1 : 0;
                var cmp = (Cmp) node.getPred(0);
                var leftMatch = left.match(cmp.getPred(offset), matcher);
                var rightMatch = right.match(cmp.getPred((offset + 1) % 2), matcher);

                if (leftMatch.matches() && rightMatch.matches()) {
                    var relation = cmp.getRelation();
                    var condition = getCondition(relation,
                            leftMatch.getOperand(), rightMatch.getOperand());
                    var predecessors = Stream.concat(leftMatch.getPredecessors(),
                            rightMatch.getPredecessors());
                    return new ConditionMatch(node, condition,
                            predecessors.collect(Collectors.toList()),
                            List.of(leftMatch.getOperand(), rightMatch.getOperand()));
                } else {
                    return InstructionMatch.none();
                }
            } else {
                return InstructionMatch.none();
            }
        }

        public abstract ExitCondition getCondition(Relation relation, S left, T right);
    }

    public static final class Comparison<S extends Operand.Source, T extends Operand.Source> extends Conditional<S, T> {

        public Comparison(Pattern<? extends OperandMatch<S>> left, Pattern<? extends OperandMatch<T>> right,
                boolean swapOperands) {
            super(left, right, swapOperands);
        }

        @Override
        public ExitCondition getCondition(Relation relation, S left, T right) {
            return ExitCondition.comparison(
                    swapOperands ? relation.inversed() : relation,
                    left, right);
        }
    }

    public static final class Test extends Conditional<Register, Immediate> {

        private static final Pattern<OperandMatch<Register>> REGISTER = OperandPattern.register();
        private static final Pattern<OperandMatch<Immediate>> ZERO = OperandPattern.zero();

        public Test(boolean swapOperands) {
            super(REGISTER, ZERO, swapOperands);
        }

        @Override
        public ExitCondition getCondition(Relation relation, Register left, Immediate right) {
            return ExitCondition.test(swapOperands ? relation.inversed() : relation, left);
        }
    }

    @NoArgsConstructor
    public static final class Unconditional implements Pattern<InstructionMatch> {
        @Override
        public InstructionMatch match(Node node, MatcherState matcher) {
            if (node.getOpCode() == ir_opcode.iro_Jmp) {
                return new ConditionMatch(node, ExitCondition.unconditional(),
                        Collections.emptyList(), Collections.emptyList());
            } else {
                return InstructionMatch.none();
            }
        }
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private static final class ConditionMatch extends InstructionMatch.Condition {

        private final Node node;
        private final ExitCondition condition;
        private final List<Node> predecessors;
        private final List<Operand> operands;

        @Override
        public Node getNode() {
            return node;
        }

        @Override
        public Optional<Integer> getTargetRegister() {
            return Optional.empty();
        }

        @Override
        public ExitCondition getCondition() {
            return condition;
        }

        @Override
        public Stream<Node> getPredecessors() {
            return predecessors.stream();
        }

        @Override
        public Stream<Operand> getOperands() {
            return operands.stream();
        }
    }
}
