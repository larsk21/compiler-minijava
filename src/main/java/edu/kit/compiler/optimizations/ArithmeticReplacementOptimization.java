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
import lombok.Data;
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
            Node multiplicand = null;
            if (multiplier != null) {
                multiplicand = node.getRight();
            } else {
                multiplier = ReplaceableMultiplier.of(node.getRight());
                multiplicand = node.getLeft();
            }

            if (multiplier != null) {
                var block = node.getBlock();
                exchange(node, multiplier.getReplacement(block, multiplicand));
            }
        }

        @Override
        public void visit(Div node) {
            if (node.getResmode().equals(Mode.getLs())
                    && node.getRight().getOpCode() == ir_opcode.iro_Const
                    && node.getLeft().getOpCode() == ir_opcode.iro_Conv) {
                var divisor = ReplaceableDivisor.of((Const) node.getRight());
                if (divisor != null) {
                    var block = node.getBlock();
                    var dividend = node.getLeft().getPred(0);
                    var newNode = divisor.getReplacement(block, dividend);
                    replaceDiv(node, newNode);
                }
            }
        }

        private void replaceDiv(Div node, Node newNode) {
            assert newNode.getMode().equals(Mode.getLs());

            for (var edge : BackEdges.getOuts(node)) {
                if (edge.node.getMode().equals(Mode.getM())) {
                    exchange(edge.node, node.getMem());
                } else if (edge.node.getMode().equals(Mode.getLs())) {
                    exchange(edge.node, newNode);
                } else {
                    throw new UnsupportedOperationException(
                            "Div control flow projections not supported");
                }
            }
        }

        private void exchange(Node oldNode, Node newNode) {
            Graph.exchange(oldNode, newNode);
            this.changes = true;
        }
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private static abstract class ReplaceableDivisor {

        public abstract Node getReplacement(Node block, Node dividend);

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
            if (powerOfTwo != null) {
                return powerOfTwo;
            } else {
                return ConstantDivisor.of(divisor);
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
        public Node getReplacement(Node block, Node dividend) {
            assert dividend.getMode().equals(Mode.getIs());

            var graph = block.getGraph();
            Node signValue;

            // extract (and replicate) the sign bit as needed
            if (shift > 1) {
                var signShift = shiftConst(graph, shift - 1);
                var restShift = shiftConst(graph, Mode.getIs().getSizeBits() - shift);

                signValue = graph.newShrs(block, dividend, signShift);
                signValue = graph.newShr(block, signValue, restShift);
            } else {
                var signShift = shiftConst(graph, Mode.getIs().getSizeBits() - 1);
                signValue = graph.newShr(block, dividend, signShift);
            }

            // add the (replicated) sign bit to the dividend
            var quotient = graph.newAdd(block, dividend, signValue);

            // shift the divided right n times
            quotient = graph.newShrs(block, quotient, shiftConst(graph, shift));

            // negate the quotient if necessary
            if (negative) {
                quotient = graph.newMinus(block, quotient);
            }

            // convert the result to Ls, to keep correct modes
            return graph.newConv(block, quotient, Mode.getLs());
        }
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private static final class ConstantDivisor extends ReplaceableDivisor {

        private final TargetValue divisor;
        private final MagicNumber magic;

        private static ConstantDivisor of(TargetValue divisor) {
            assert !divisor.isNull();
            assert getShiftWidth(divisor) == -1;

            var magic = MagicNumber.of(divisor.asInt());
            return new ConstantDivisor(divisor, magic);
        }

        @Override
        public Node getReplacement(Node block, Node dividend) {
            assert dividend.getMode().equals(Mode.getIs());

            var graph = block.getGraph();
            var magicValue = new TargetValue(magic.getNumber(), Mode.getIs());

            // multiply dividend with the magic number
            // ? how is Mulh handeled?
            var magicConst = graph.newConst(magicValue);
            var quotient = graph.newMulh(block, dividend, magicConst);

            // add or subtract the dividend from the result
            if (divisor.neg().isNegative() && magic.getNumber() < 0) {
                quotient = graph.newAdd(block, quotient, dividend);
            } else if (divisor.isNegative() && magic.getNumber() > 0) {
                quotient = graph.newSub(block, quotient, dividend);
            }

            // shift the result right by the amount required by the magic number
            if (magic.getShift() > 0) {
                var magicShift = shiftConst(graph, magic.getShift());
                quotient = graph.newShrs(block, quotient, magicShift);
            }

            // extract the sign bit and it to the quotient
            var signShift = shiftConst(graph, Mode.getIs().getSizeBits() - 1);
            var signBit = graph.newShr(block, quotient, signShift);
            quotient = graph.newAdd(block, quotient, signBit);

            // convert the result to Ls, to keep correct modes
            return graph.newConv(block, quotient, Mode.getLs());
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

                if (shift > 0 && shouldReplace(value)) {
                    return new ReplaceableMultiplier(shift, value.isNegative());
                } else {
                    return null;
                }
            } else {
                return null;
            }
        }

        public Node getReplacement(Node block, Node operand) {
            var graph = block.getGraph();

            var shiftConst = shiftConst(graph, shift);
            var shiftNode = graph.newShl(block, operand, shiftConst);

            if (negative) {
                return graph.newMinus(block, shiftNode);
            } else {
                return shiftNode;
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

    /**
     * Source: "Hackers Delight", Chapter 10.6 (Henry S. Warren, 2002)
     * as well as LLVM's implementation of the algorithm
     */
    @Data
    private static final class MagicNumber {

        private static final int TWO_31 = 0x80000000; // = 2^31

        private final int number;
        private final int shift;

        public static MagicNumber of(int divisor) {
            // factors of 2^32 + 2 (= 2 * 3 * 715.827.883) are special cased
            if (divisor == -3) {
                return new MagicNumber(-1431655765, 0);
            } else if (divisor == -715_827_883) {
                return new MagicNumber(-5, 0);
            } else {
                return computeMagic(divisor);
            }
        }

        private static MagicNumber computeMagic(int divisor) {
            int absDiv = Math.abs(divisor);
            int t = TWO_31 + (divisor >> 31);
            int absNc = t - 1 - Integer.remainderUnsigned(t, absDiv);

            int q1 = Integer.divideUnsigned(TWO_31, absNc);
            int r1 = TWO_31 - q1 * absNc;
            int q2 = Integer.divideUnsigned(TWO_31, absDiv);
            int r2 = TWO_31 - q2 * absDiv;

            int p = 31;
            int delta;
            do {
                p += 1;
                q1 *= 2;
                r1 *= 2;
                if (Integer.compareUnsigned(r1, absNc) >= 0) {
                    q1 += 1;
                    r1 -= absNc;
                }

                q2 *= 2;
                r2 *= 2;
                if (Integer.compareUnsigned(r2, absDiv) >= 0) {
                    q2 += 1;
                    r2 -= absDiv;
                }

                delta = absDiv - r2;
            } while (Integer.compareUnsigned(q1, delta) < 0 || (q1 == delta && r1 == 0));

            int magic = divisor < 0 ? -(q2 + 1) : (q2 + 1);
            return new MagicNumber(magic, p - 32);
        }
    }
}
