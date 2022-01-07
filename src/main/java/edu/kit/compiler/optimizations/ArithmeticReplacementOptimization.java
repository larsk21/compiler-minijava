package edu.kit.compiler.optimizations;

import java.util.Optional;

import edu.kit.compiler.io.StackWorklist;
import edu.kit.compiler.io.Worklist;
import firm.BackEdges;
import firm.Graph;
import firm.Mode;
import firm.TargetValue;
import firm.bindings.binding_irnode.ir_opcode;
import firm.nodes.Const;
import firm.nodes.Conv;
import firm.nodes.Div;
import firm.nodes.Mod;
import firm.nodes.Mul;
import firm.nodes.Node;
import firm.nodes.NodeVisitor;
import firm.nodes.Proj;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Represents an optimization that implements strength reductions for a number
 * of arithmetic expression. Currently the follows replacements are made:
 * 
 * - Multiplication by any non-zero power of two is replaced with a left shift
 * (exception: 2, 4, and 8 are not replaced, as they also may be expressed
 * as scaling factor, using x86's addressing modes)
 * - Division by a any non-zero power of two is replaced with a right shift and
 * some more instructions to achieve correct rounding of the result
 * - Division by any other integer greater than 3 is replaced with something
 * with a kind of reciprocal of the divisor (in practice it is not quite as
 * simple as that, the algorithm is also implemented by LLVM and is taken from
 * "Hacker's Delight" by Henry S. Warren (2002))
 * - Any Modulo with a divisor that could be replaced if used in a division is
 * replaced with a division. Subsequently the remainder is calculated using
 * the relationship (a % b) = a - (a / b) * b
 */
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

        return visitor.hasChanged;
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private static final class Visitor extends NodeVisitor.Default {

        private final Worklist<Node> worklist = new StackWorklist<>(false);
        private final Graph graph;

        private boolean hasChanged = false;

        private void apply() {
            graph.walkTopological(new WorklistFiller(worklist));

            while (!worklist.isEmpty()) {
                worklist.dequeue().accept(this);
            }
        }

        @Override
        public void visit(Mul node) {
            var block = node.getBlock();
            getMulReplacement(block, node.getLeft(), node.getRight())
                    .or(() -> getMulReplacement(block, node.getRight(), node.getLeft()))
                    .ifPresent(newNode -> exchange(node, newNode));
        }

        @Override
        public void visit(Div node) {
            getDivOrModReplacement(node, node.getResmode())
                    .ifPresent(newNode -> exchangeDivOrMod(node, newNode, node.getMem()));
        }

        @Override
        public void visit(Mod node) {
            getDivOrModReplacement(node, node.getResmode())
                    .ifPresent(newNode -> exchangeDivOrMod(node, newNode, node.getMem()));
        }

        @Override
        public void visit(Conv node) {
            hasChanged |= Util.contractConv(node);
        }

        @Override
        public void visit(Proj node) {
            hasChanged |= Util.skipTuple(node);
        }

        /**
         * Returns a replacement for a multiplication if one is found using
         * ReplaceableMultiplier#of.
         */
        private Optional<Node> getMulReplacement(Node block, Node multiplicand, Node multiplier) {
            return ReplaceableMultiplier.of(multiplier)
                    .map(mult -> mult.getReplacement(block, multiplicand));
        }

        /**
         * Returns a replacement for a division or modulo operation if one is
         * found using ReplaceableDivisor#of
         */
        private Optional<Node> getDivOrModReplacement(Node node, Mode mode) {
            assert node.getOpCode() == ir_opcode.iro_Div || node.getOpCode() == ir_opcode.iro_Mod;

            // this code is quite specific to our solution to division and modulo,
            // i.e. the operation has mode Ls, but the operands always fit into Is.
            if (mode.equals(Mode.getLs())
                    && node.getPred(2).getOpCode() == ir_opcode.iro_Const
                    && node.getPred(1).getOpCode() == ir_opcode.iro_Conv) {

                return ReplaceableDivisor.of(node.getPred(2)).map(divisor -> {
                    var block = node.getBlock();
                    var dividend = node.getPred(1).getPred(0);

                    return node.getOpCode() == ir_opcode.iro_Div
                            ? divisor.getDivReplacement(block, dividend)
                            : divisor.getModReplacement(block, dividend);
                });
            } else {
                return Optional.empty();
            }
        }

        /**
         * Exchange the two nodes and set the hasChanged flag.
         */
        private void exchange(Node oldNode, Node newNode) {
            Graph.exchange(oldNode, newNode);
            this.hasChanged = true;
        }

        /**
         * Wraps around Util#exchangeDivOrMod and sets the hasChanged flag.
         */
        private void exchangeDivOrMod(Node node, Node newNode, Node newMem) {
            Graph.turnIntoTuple(node, new Node[] { newMem, newNode });
            this.hasChanged = true;
        }
    }

    /**
     * Base class for divisors that may be replaced with less expensive operations.
     */
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private static abstract class ReplaceableDivisor {

        /**
         * Returns the replacement for the encoded division.
         */
        public abstract Node getDivReplacement(Node block, Node dividend);

        /**
         * Returns the replacement for the encoded modulo operation.
         */
        public abstract Node getModReplacement(Node block, Node dividend);

        /**
         * Returns an instance of this class if a division by the given node
         * may be replaced with less expensive operations. If the node is a
         * Const and its value is not -1, 0, or 1, a replacement should always
         * be found.
         */
        public static Optional<ReplaceableDivisor> of(Node node) {
            if (node.getOpCode() == ir_opcode.iro_Const) {
                var divisor = ((Const) node).getTarval();

                // don't replace x/0, x/1, and x/-1
                // in Minijava the divisor should always fit in an integer
                if (!divisor.isNull() && !divisor.abs().isOne()
                        && divisor.asLong() <= Integer.MAX_VALUE
                        && divisor.asLong() >= Integer.MIN_VALUE) {
                    return Optional.of(of(divisor.convertTo(Mode.getIs())));
                } else {
                    return Optional.empty();
                }
            } else {
                return Optional.empty();
            }
        }

        private static ReplaceableDivisor of(TargetValue divisor) {
            assert divisor.getMode().equals(Mode.getIs());

            return PowerOfTwoDivisor.of(divisor)
                    .orElseGet(() -> ConstantDivisor.of(divisor));
        }
    }

    /**
     * Represents a divisor that may be replaced with a right shift (and some
     * operations to ensure correct rounding for negative numbers)
     */
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private static final class PowerOfTwoDivisor extends ReplaceableDivisor {

        private final TargetValue divisor;
        private final int shift;

        public static Optional<ReplaceableDivisor> of(TargetValue divisor) {
            var shift = getShiftWidth(divisor);
            if (shift > 0) {
                return Optional.of(new PowerOfTwoDivisor(divisor, shift));
            } else {
                return Optional.empty();
            }
        }

        @Override
        public Node getDivReplacement(Node block, Node dividend) {
            var quotient = getQuotientBase(block, dividend);
            var graph = block.getGraph();

            // shift the divided right n times
            quotient = graph.newShrs(block, quotient, shiftConst(graph, shift));

            // negate the quotient if necessary
            if (divisor.isNegative()) {
                quotient = graph.newMinus(block, quotient);
            }

            // convert the result to Ls, to keep correct modes
            return graph.newConv(block, quotient, Mode.getLs());
        }

        @Override
        public Node getModReplacement(Node block, Node dividend) {
            var quotient = getQuotientBase(block, dividend);
            var graph = block.getGraph();

            // combine right and left shift into a single And operation
            var mask = divisor.isNegative() ? divisor : divisor.neg();
            var product = graph.newAnd(block, quotient, graph.newConst(mask));

            // subtract product from dividend to get remainder
            var remainder = graph.newSub(block, dividend, product);

            // convert the result to Ls, to keep correct modes
            return graph.newConv(block, remainder, Mode.getLs());
        }

        private Node getQuotientBase(Node block, Node dividend) {
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
            return graph.newAdd(block, dividend, signValue);
        }
    }

    /**
     * Represents a divisor that may be replace with a multiplications by a
     * kind of fixed-point multiplication with the reciprocal of the divisor.
     */
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private static final class ConstantDivisor extends ReplaceableDivisor {

        private final TargetValue divisor;
        private final MagicNumber magic;

        private static ReplaceableDivisor of(TargetValue divisor) {
            assert !divisor.isNull();
            assert getShiftWidth(divisor) == -1;

            var magic = MagicNumber.of(divisor.asInt());
            return new ConstantDivisor(divisor, magic);
        }

        @Override
        public Node getDivReplacement(Node block, Node dividend) {
            var graph = block.getGraph();

            var quotient = getQuotientBase(block, dividend);

            // convert the result to Ls, to keep correct modes
            return graph.newConv(block, quotient, Mode.getLs());
        }

        @Override
        public Node getModReplacement(Node block, Node dividend) {
            var graph = block.getGraph();
            var quotient = getQuotientBase(block, dividend);

            // multiply the quotient with the divisor
            var product = graph.newMul(block, quotient, graph.newConst(divisor));

            // subtract the product from the dividend
            var remainder = graph.newSub(block, dividend, product);

            // convert the result to Ls, to keep correct modes
            return graph.newConv(block, remainder, Mode.getLs());
        }

        private Node getQuotientBase(Node block, Node dividend) {
            assert dividend.getMode().equals(Mode.getIs());
            var graph = block.getGraph();

            // Note: We actually need a Mulh here. However, as there is no
            // support for it during instruction selection, we instead cast up
            // to Ls multiply, shift right by 32 and cast back down to Is

            // multiply dividend with the magic number
            var longDiv = graph.newConv(block, dividend, Mode.getLs());
            var magicValue = new TargetValue(magic.getNumber(), Mode.getLs());
            var magicConst = graph.newConst(magicValue);
            var quotient = graph.newMul(block, longDiv, magicConst);

            // get the upper part of the result
            var shiftHigh = shiftConst(graph, Mode.getIs().getSizeBits());
            quotient = graph.newShr(block, quotient, shiftHigh);
            quotient = graph.newConv(block, quotient, Mode.getIs());

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
            return graph.newAdd(block, quotient, signBit);
        }
    }

    /**
     * Represents a multiplier that may be replaced with a left shift.
     */
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
        public static Optional<ReplaceableMultiplier> of(Node node) {
            if (node.getOpCode() == ir_opcode.iro_Const) {
                var value = ((Const) node).getTarval();
                var shift = getShiftWidth(value);

                if (shift > 0 && shouldReplace(value)) {
                    return Optional.of(new ReplaceableMultiplier(shift, value.isNegative()));
                } else {
                    return Optional.empty();
                }
            } else {
                return Optional.empty();
            }
        }

        /**
         * Returns a replacement for the multiplication represented by the
         * instance of this class.
         */
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
         * (1, 2, 4, or 8). The value is assumed to be a power of two.
         */
        private static final boolean shouldReplace(TargetValue multiplier) {
            var mask = new TargetValue(0b1111, multiplier.getMode());
            return multiplier.and(mask).isNull();
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
