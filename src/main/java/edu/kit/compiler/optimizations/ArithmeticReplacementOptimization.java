package edu.kit.compiler.optimizations;

import edu.kit.compiler.io.StackWorklist;
import edu.kit.compiler.io.Worklist;
import firm.Graph;
import firm.Mode;
import firm.TargetValue;
import firm.bindings.binding_irnode.ir_opcode;
import firm.nodes.Const;
import firm.nodes.Mul;
import firm.nodes.Node;
import firm.nodes.NodeVisitor;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

public class ArithmeticReplacementOptimization implements Optimization {

    @Override
    public boolean optimize(Graph graph) {
        var visitor = new Visitor(graph);
        visitor.apply();

        return visitor.changes;
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private static final class Visitor extends NodeVisitor.Default {

        private final Worklist<Node> worklist = new StackWorklist<>();
        private final Graph graph;

        private boolean changes = false;

        private void apply() {
            graph.walkTopological(new WorklistFiller(worklist));

            while (!worklist.isEmpty()) {
                worklist.dequeue().accept(this);
            }
        }

        @Override
        public void visit(Mul node) {
            var multiplier = ReplaceableMultiplier.of(node.getLeft());
            if (multiplier != null) {
                multiplier.replace(node, node.getRight());
            } else if ((multiplier = ReplaceableMultiplier.of(node.getRight())) != null) {
                multiplier.replace(node, node.getLeft());
            }
        }
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private static final class ReplaceableMultiplier {

        @Getter
        private final int shift;
        @Getter
        private final boolean negative;

        /**
         * Returns an instance of this class if the given node is a Const,
         * whose TargetValue can (and should) be replace by a left shift,
         * otherwise returns `null`.
         */
        public static ReplaceableMultiplier of(Node node) {
            if (node.getOpCode() == ir_opcode.iro_Const) {
                var value = ((Const) node).getTarval();
                var shift = getShiftWidth(value);
                // System.err.println(shift + " " + shouldReplace(value));

                if (shift > 0 && shouldReplace(value)) {
                    return new ReplaceableMultiplier(shift, value.isNegative());
                } else {
                    return null;
                }
            } else {
                return null;
            }
        }

        public void replace(Mul mul, Node operand) {
            var graph = mul.getGraph();
            // todo ? is Bu correct here?
            var shiftConst = graph.newConst(new TargetValue(shift, Mode.getBu()));
            var shiftNode = graph.newShl(mul.getBlock(), operand, shiftConst);
            
            if (negative) {
                Graph.exchange(mul, graph.newMinus(mul.getBlock(), shiftNode));
            } else {
                Graph.exchange(mul, shiftNode);
            }
        }

        /**
         * Returns true if the given value is not one of x86 scaling factors
         * (1, 2, 4, or 8; positive or negative). The value is assumed to be
         * a power of two. 
         */
        private static final boolean shouldReplace(TargetValue multiplier) {
            var mask = new TargetValue(0b1111, multiplier.getMode());
            return multiplier.abs().and(mask).isNull();
        }
    }

    /**
     * Returns `n` if the given value is the n-th power of two (positive or
     * negative). Otherwise returns -1.
     */
    private static int getShiftWidth(TargetValue value) {
        int lowest = value.lowest_bit();

        int highest;
        if (!value.isNegative()) {
            // 0 ... 0 1 0 ... 0 = (2^n)
            // ________^
            highest = value.highest_bit();
        } else {
            // 1 ... 1 0 ... 0 = -(2^n)
            // ______^
            highest = value.not().highest_bit() + 1;
        }

        return lowest == highest ? lowest : -1;
    }
}
