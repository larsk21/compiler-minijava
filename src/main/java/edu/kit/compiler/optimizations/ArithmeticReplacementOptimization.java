package edu.kit.compiler.optimizations;

import java.util.Objects;

import edu.kit.compiler.io.StackWorklist;
import edu.kit.compiler.io.Worklist;
import firm.BackEdges;
import firm.Graph;
import firm.Mode;
import firm.TargetValue;
import firm.bindings.binding_irnode.ir_opcode;
import firm.nodes.Const;
import firm.nodes.Div;
import firm.nodes.Mul;
import firm.nodes.Node;
import firm.nodes.NodeVisitor;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

public class ArithmeticReplacementOptimization implements Optimization {

    @Override
    public boolean optimize(Graph graph) {
        boolean backEdgesEnabled = BackEdges.enabled(graph);
        BackEdges.enable(graph);

        var visitor = new Visitor(graph);
        visitor.apply();

        if (!backEdgesEnabled) {
            BackEdges.disable(graph);
        }

        return visitor.changes;
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private static final class Visitor extends NodeVisitor.Default {

        private final Worklist<Node> worklist = new StackWorklist<>();
        private final Graph graph;

        // todo actually set this value
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

        @Override
        public void visit(Div node) {
            if (node.getMode().equals(Mode.getLs())
                    && node.getRight().getOpCode() == ir_opcode.iro_Const
                    && node.getLeft().getOpCode() == ir_opcode.iro_Conv) {
                // var divisor = ((Const) node.getRight()).getTarval();
                var divisor = ReplaceableDivisor.of((Const) node.getRight());
                if (divisor != null) {
                    divisor.replace(node, node.getLeft().getPred(0));
                }
            }
        }

        // todo
        private void exchange(Node oldNode, Node newNode) {
            Graph.exchange(oldNode, newNode);
            this.changes = true;
        }
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private static abstract class ReplaceableDivisor {

        public abstract void replace(Div div, Node operand);

        public static ReplaceableDivisor of(Const node) {
            if (node.getOpCode() == ir_opcode.iro_Const) {
                var divisor = node.getTarval();

                // don't replace x/0, x/1, and x/-1
                if (!divisor.isNull() && !divisor.abs().isOne()
                        && divisor.asLong() <= Integer.MAX_VALUE
                        && divisor.asLong() >= Integer.MIN_VALUE) {
                    return Objects.requireNonNull(of(divisor.convertTo(Mode.getIs())));
                } else {
                    return null;
                }
            } else {
                return null;
            }
        }

        private static ReplaceableDivisor of(TargetValue divisor) {
            assert divisor.getMode().equals(Mode.getIs());

            var powerOfTwo = PowerOfTwoDivisor.of(divisor);
            if (powerOfTwo == null) {
                return powerOfTwo;
            } else {
                return AnyDivisor.of(divisor);
            }
        }

        private void replaceDiv(Div node, Node newNode) {
            assert newNode.getMode().equals(Mode.getIs());

            for (var edge : BackEdges.getOuts(node)) {
                if (edge.node.getMode().equals(Mode.getM())) {
                    Graph.exchange(edge.node, node.getMem());
                } else if (edge.node.getMode().equals(Mode.getLs())) {
                    var graph = node.getGraph();
                    var conv = graph.newConv(node.getBlock(), newNode, Mode.getLs());
                    Graph.exchange(edge.node, conv);
                } else {
                    throw new UnsupportedOperationException(
                            "Div control flow projections not supported");
                }
            }
        }
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private static final class PowerOfTwoDivisor extends ReplaceableDivisor {

        private final int shift;
        private final boolean negative;

        public static PowerOfTwoDivisor of(TargetValue divisor) {
            var shift = getShiftWidth(divisor);
            if (shift > 0) {
                return new PowerOfTwoDivisor(shift, divisor.isNegative());
            } else {
                return null;
            }
        }

        @Override
        public void replace(Div div, Node operand) {
            assert operand.getMode().equals(Mode.getIs());
            assert div.getResmode().equals(Mode.getLs());

            var block = div.getBlock();
            var graph = div.getGraph();
            Node signValue;

            // extract (and replicate) the sign bit as needed
            if (shift > 1) {
                var signShift = shiftConst(graph, shift - 1);
                var restShift = shiftConst(graph, Mode.getIs().getSizeBits() - shift);

                signValue = graph.newShrs(block, operand, signShift);
                signValue = graph.newShr(block, signValue, restShift);
            } else {
                var signShift = shiftConst(graph, Mode.getIs().getSizeBits() - 1);
                signValue = graph.newShr(block, operand, signShift);
            }

            // add the (replicated) sign bit to the dividend
            var quotient = graph.newAdd(block, operand, signValue);

            // shift the divided right n times
            quotient = graph.newShrs(block, quotient, shiftConst(graph, shift));

            // negate the quotient if necessary
            if (negative) {
                quotient = graph.newMinus(block, quotient);
            }

            super.replaceDiv(div, quotient);
        }
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private static final class AnyDivisor extends ReplaceableDivisor {

        private final TargetValue divisor;
        private final int magicNumber;
        private final int shift;

        private static AnyDivisor of(TargetValue divisor) {
            throw new IllegalStateException();
        }

        @Override
        public void replace(Div div, Node operand) {
            // TODO Auto-generated method stub
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
            var shiftNode = graph.newShl(mul.getBlock(),
                    operand, shiftConst(graph, shift));

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

    private static Node shiftConst(Graph graph, int shift) {
        return graph.newConst(new TargetValue(shift, Mode.getBu()));
    }
}
