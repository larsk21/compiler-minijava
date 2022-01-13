package edu.kit.compiler.optimizations;

import java.util.function.BinaryOperator;

import edu.kit.compiler.io.StackWorklist;
import edu.kit.compiler.io.Worklist;
import firm.Graph;
import firm.Mode;
import firm.TargetValue;
import firm.bindings.binding_irnode.ir_opcode;
import firm.nodes.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 
 * This optimization should be run after constants have been propagated. One may
 * also benefit from propagating constants again after running this
 * optimization, as new constants may be introduced.
 * 
 * 
 * Important Note: Some optimizations like `0 * x --> 0` or `-(x - y) = (y - x)`
 * may seem like the might remove or reorder function calls. However, the memory
 * dependencies between these calls (should) ensure correctness.
 * 
 * Implemented Optimizations:
 * - Remove addition / subtraction of constant 0 (left or right operand)
 * - Remove double arithmetic negation of any value
 * - Replace addition with negated operand (left or right operand)
 * - Replace subtraction with negated operand (right operand only)
 * - Replace subtraction of constant as right operand (right operand only)
 * - Remove multiplication with constant 1 (left or right operand)
 * - Replace multiplication with constant 0 (left or right operand)
 * - Replace multiplication with constant -1 (left or right operand)
 * - Remove division by constant 1 (right operand only)
 * - Remove division with dividend 0
 * - Replace division by constant -1 with negation (right operand only)
 * - Replace modulo constant 1 or -1 with constant 0 (right operand only)
 * - Perform the following normalizations:
 * --> In Add with Const, the Const is always the right operand (i.e. x + c)
 * --> In Sub with Const, the Const is always the left operand (i.e. c - x)
 * --> In Mul with Const, the Const is always the right operand (i.e. x * c)
 * - Fold nested associative (and distributive) expressions with const operands
 * --> e.g. 5 - (x + 10) --> -5 - x
 * --> e.g. 2 * (x * 21) --> x * 42
 * --> e.g. (x + 4) * 4 -> x * 4 + 16
 * - Fold negation of addition, subtraction or multiplication with constant
 */
public final class ArithmeticIdentitiesOptimization implements Optimization {
    @Override
    public boolean optimize(Graph graph) {
        var visitor = new Visitor(graph);
        visitor.apply();

        return visitor.isChanges();
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private static final class Visitor extends NodeVisitor.Default {
        private final Graph graph;

        private final Worklist<Node> worklist = new StackWorklist<>(false);

        @Getter
        private boolean changes = false;

        private final void apply() {
            graph.walkTopological(new WorklistFiller(worklist));

            while (!worklist.isEmpty()) {
                worklist.dequeue().accept(this);
            }
        }

        @Override
        public void visit(Add node) {
            if (isConstZero(node.getLeft())) {
                // 0 + x --> x
                exchange(node, node.getRight());
            } else if (isConstZero(node.getRight())) {
                // x + 0 --> x
                exchange(node, node.getLeft());

            } else if (node.getLeft().getOpCode() == ir_opcode.iro_Minus) {
                // (-x) + y --> y - x
                assert node.getLeft().getPredCount() == 1;
                var left = node.getRight();
                var right = node.getLeft().getPred(0);
                var sub = graph.newSub(node.getBlock(), left, right);
                exchange(node, enqueued(sub));
            } else if (node.getRight().getOpCode() == ir_opcode.iro_Minus) {
                // x + (-y) --> x - y
                assert node.getRight().getPredCount() == 1;
                var left = node.getLeft();
                var right = node.getRight().getPred(0);
                var sub = graph.newSub(node.getBlock(), left, right);
                exchange(node, enqueued(sub));

            } else if (isOnlyLeftConst(node.getLeft(), node.getRight())) {
                // Normalization: c + x --> x + c
                var add = graph.newAdd(node.getBlock(), node.getRight(), node.getLeft());
                exchange(node, enqueued(add));

            } else if (node.getRight().getOpCode() == ir_opcode.iro_Const) {
                // (x + c) + d --> x + (c + d)
                // (c - x) + d --> (c + d) - x
                foldAdditive(node, node.getLeft(), node.getRight(), TargetValue::add);
            }
        }

        @Override
        public void visit(Sub node) {
            if (isConstZero(node.getLeft())) {
                // 0 - x --> -x
                var minusNode = graph.newMinus(node.getBlock(), node.getRight());
                exchange(node, enqueued(minusNode));
            } else if (isConstZero(node.getRight())) {
                // x - 0 --> x
                exchange(node, node.getLeft());
            } else if (node.getRight().getOpCode() == ir_opcode.iro_Minus) {
                // x - (-y) --> x + y
                assert node.getRight().getPredCount() == 1;
                var left = node.getLeft();
                var right = node.getRight().getPred(0);
                var add = graph.newAdd(node.getBlock(), left, right);
                exchange(node, enqueued(add));

            } else if (isOnlyLeftConst(node.getRight(), node.getLeft())) {
                // Normalization: x - c --> x + (-c)
                var constValue = getConstValue(node.getRight());
                assert constValue != null;
                var left = node.getLeft();
                var right = graph.newConst(constValue.neg());
                var add = graph.newAdd(node.getBlock(), left, right);
                exchange(node, enqueued(add));

            } else if (node.getLeft().getOpCode() == ir_opcode.iro_Const) {
                // c - (x + d) --> (c - d) - x
                // c - (d - x) --> x + (c - d)
                foldAdditive(node, node.getRight(), node.getLeft(), (l, r) -> r.sub(l));
            }
            // (x + c) * d = x * d + (c * d)
        }

        @Override
        public void visit(Minus node) {
            var operand = node.getOp();
            switch (operand.getOpCode()) {
                case iro_Minus -> {
                    // -(-x) --> x
                    assert operand.getPredCount() == 1;
                    exchange(node, operand.getPred(0));
                }
                case iro_Add -> {
                    assert operand.getPredCount() == 2;
                    var constValue = getConstValue(operand.getPred(1));
                    if (constValue != null) {
                        // - (x + c) --> -c - x
                        var constNode = graph.newConst(constValue.neg());
                        var subNode = graph.newSub(node.getBlock(), constNode, operand.getPred(0));
                        exchange(node, enqueued(subNode));
                    }
                }
                case iro_Sub -> {
                    // -(x - y) --> y - x
                    assert operand.getPredCount() == 2;
                    var left = operand.getPred(0);
                    var right = operand.getPred(1);
                    var sub = graph.newSub(node.getBlock(), right, left);
                    exchange(node, enqueued(sub));
                }
                case iro_Mul -> {
                    assert operand.getPredCount() == 2;
                    var constValue = getConstValue(operand.getPred(1));
                    if (constValue != null) {
                        // - (x * c) --> x * -c
                        var constNode = graph.newConst(constValue.neg());
                        var mulNode = graph.newMul(node.getBlock(), operand.getPred(0), constNode);
                        exchange(node, enqueued(mulNode));
                    }
                }
                default -> {
                }
            }
        }

        @Override
        public void visit(Mul node) {
            if (isConstOne(node.getLeft())) {
                // 1 * x --> x
                exchange(node, node.getRight());
            } else if (isConstOne(node.getRight())) {
                // x * 1 --> x
                exchange(node, node.getLeft());

            } else if (isConstZero(node.getLeft())) {
                // 0 * x --> 0
                var zero = node.getRight().getMode().getNull();
                exchange(node, graph.newConst(zero));
            } else if (isConstZero(node.getRight())) {
                // x * 0 --> 0
                var zero = node.getLeft().getMode().getNull();
                exchange(node, graph.newConst(zero));

            } else if (isConstNegOne(node.getLeft())) {
                // -1 * x --> -x
                var minus = graph.newMinus(node.getBlock(), node.getRight());
                exchange(node, enqueued(minus));
            } else if (isConstNegOne(node.getRight())) {
                // x * -1 --> -x
                var minus = graph.newMinus(node.getBlock(), node.getLeft());
                exchange(node, enqueued(minus));

            } else if (isOnlyLeftConst(node.getLeft(), node.getRight())) {
                // Normalization: c * x --> x * c
                var mul = graph.newMul(node.getBlock(), node.getRight(), node.getLeft());
                exchange(node, enqueued(mul));

            } else if (node.getRight().getOpCode() == ir_opcode.iro_Const) {
                // (x * c) * d --> x * (c * d)
                // (x + c) * d --> (x * d) + (c * d)
                foldMultiplication(node);
            }
        }

        @Override
        public void visit(Div node) {
            if (isConstOne(node.getRight())) {
                // x / 1 --> x
                exchangeDivOrMod(node, node.getLeft(), node.getMem());
            } else if (isConstNegOne(node.getRight())) {
                // x / -1 --> -x
                var minus = graph.newMinus(node.getBlock(), node.getLeft());
                exchangeDivOrMod(node, enqueued(minus), node.getMem());
            } else if (isConstZero(node.getLeft())) {
                // 0 / x --> 0
                exchangeDivOrMod(node, node.getLeft(), node.getMem());
            }
        }

        @Override
        public void visit(Mod node) {
            if (isConstAbsOne(node.getRight())) {
                // x % 1 --> 0
                // x % -1 --> 0
                var zero = node.getLeft().getMode().getNull();
                exchangeDivOrMod(node, graph.newConst(zero), node.getMem());
            }
        }

        @Override
        public void visit(Conv node) {
            // this works in combination with distributive transformation of
            // multiplications to better use x86 addressing modes
            if (node.getMode().equals(Mode.getLs())
                    && node.getOp().getOpCode() == ir_opcode.iro_Add
                    && isConst(node.getOp().getPred(1))) {
                // (x + c):Ls --> x:Ls + c:Ls
                exchangeConv(node, node.getOp().getPred(0), node.getOp().getPred(1));
            } else if (node.getMode().equals(Mode.getLs())
                    && node.getOp().getOpCode() == ir_opcode.iro_Conv) {
                Conv luNode = (Conv) node.getOp();
                if (luNode.getOp().getOpCode() == ir_opcode.iro_Add
                        && isConst(luNode.getOp().getPred(1))) {
                    // (x + c):Lu:Ls --> x:Ls + c:Ls
                    exchangeConv(node, luNode.getOp().getPred(0), luNode.getOp().getPred(1));
                }
            }
            // Note: We can not apply the same optimization for Lu as for Ls,
            // because this could change the program semantics
        }

        @Override
        public void visit(Proj node) {
            changes |= Util.skipTuple(node);
        }

        /**
         * If possible, simplify the given node using the associative properties
         * of addition and subtraction. `nestedNode` and `constNode` must be
         * predecessors of `node`, where `constNode` must be a Const. If
         * `nestedNode` has a Const predecessor, the given operator is used to
         * fold the Tarvals of the two Consts.
         */
        private void foldAdditive(Node node, Node nestedNode, Node constNode,
                BinaryOperator<TargetValue> accOperator) {
            assert isAdditiveOperator(node);
            assert constNode.getOpCode() == ir_opcode.iro_Const;

            var restNode = getRestNode(nestedNode);
            var leftConst = getConstOperand(nestedNode);
            var rightConst = getConstValue(constNode);

            if (restNode != null && leftConst != null && isAdditiveOperator(nestedNode)) {
                // nestedNode is an additive operation with a Const predecessor
                var accConst = accOperator.apply(leftConst, rightConst);
                var newConst = graph.newConst(accConst);
                var newNode = node.getOpCode() != nestedNode.getOpCode()
                        ? graph.newSub(node.getBlock(), newConst, restNode)
                        : graph.newAdd(node.getBlock(), restNode, newConst);

                exchange(node, enqueued(newNode));
            }
        }

        /**
         * If possible, simplify the given multiplication using its associative
         * property, or its distributive property with addition. The right
         * predecessor of the node must be a Const.
         */
        private void foldMultiplication(Mul node) {
            assert node.getRight().getOpCode() == ir_opcode.iro_Const;

            var restNode = getRestNode(node.getLeft());
            var leftConst = getConstOperand(node.getLeft());
            var rightConst = getConstValue(node.getRight());

            if (restNode != null && leftConst != null) {
                var block = node.getBlock();

                switch (node.getLeft().getOpCode()) {
                    case iro_Mul -> {
                        // (x * c) * d --> x * (c * d)
                        var constNode = graph.newConst(leftConst.mul(rightConst));
                        var newNode = graph.newMul(block, restNode, constNode);

                        exchange(node, enqueued(newNode));
                    }
                    case iro_Add -> {
                        // (x + c) * d --> x * d + c * d
                        var oldConst = graph.newConst(rightConst);
                        var leftNode = graph.newMul(block, restNode, oldConst);
                        var rightNode = graph.newConst(leftConst.mul(rightConst));
                        var newNode = graph.newAdd(block, leftNode, rightNode);

                        exchange(node, enqueued(newNode));
                        enqueued(leftNode);
                    }
                    default -> {
                    }
                }
            }
        }

        /**
         * Exchanges a conv node which operand is an add with a constant as rhs.
         */
        private void exchangeConv(Node node, Node opNode, Node offsetNode) {
            var block = node.getBlock();
            var op = graph.newConv(block, opNode, Mode.getLs());
            var offsetValue = ((Const) offsetNode).getTarval();
            var offset = graph.newConst(offsetValue.convertTo(Mode.getLs()));
            var addNode = graph.newAdd(block, op, offset);
            exchange(node, addNode);
        }

        /**
         * Returns the first const predecessor of `node`.
         */
        private static TargetValue getConstOperand(Node node) {
            for (var pred : node.getPreds()) {
                if (pred.getOpCode() == ir_opcode.iro_Const) {
                    return ((Const) pred).getTarval();
                }
            }
            return null;
        }

        /**
         * Returns the first non-const predecessor of `node`.
         */
        private static Node getRestNode(Node node) {
            for (var pred : node.getPreds()) {
                if (pred.getOpCode() != ir_opcode.iro_Const) {
                    return pred;
                }
            }
            return null;
        }

        /**
         * Returns true if the given node is a Const node.
         */
        private static boolean isConst(Node node) {
            return node.getOpCode() == ir_opcode.iro_Const;
        }

        /**
         * Returns true if `left` is const and `right` ist not const. This is
         * needed to prevent infinite loops, where predecessors are repeatedly
         * swapped.
         */
        private static boolean isOnlyLeftConst(Node left, Node right) {
            return left.getOpCode() == ir_opcode.iro_Const
                    && right.getOpCode() != ir_opcode.iro_Const;
        }

        /**
         * If the given node is a Const, return its Tarval, otherwise null.
         */
        private static TargetValue getConstValue(Node node) {
            return isConst(node) ? ((Const) node).getTarval() : null;
        }

        /**
         * Returns true if the given node is a Const with value null.
         */
        private static boolean isConstZero(Node node) {
            return isConst(node) && ((Const) node).getTarval().isNull();
        }

        /**
         * Returns true if the given node is a Const with value one.
         */
        private static boolean isConstOne(Node node) {
            return isConst(node) && ((Const) node).getTarval().isOne();
        }

        /**
         * Returns true if the given node is a Const with value negative one.
         */
        private static boolean isConstNegOne(Node node) {
            return isConst(node) && ((Const) node).getTarval().neg().isOne();
        }

        /**
         * Returns true if the given node is a Const with absolute value one.
         */
        private static boolean isConstAbsOne(Node node) {
            return isConst(node) && ((Const) node).getTarval().abs().isOne();
        }

        /**
         * Returns true if the given node is an Add or Sub.
         */
        private static boolean isAdditiveOperator(Node node) {
            var opcode = node.getOpCode();
            return opcode == ir_opcode.iro_Add || opcode == ir_opcode.iro_Sub;
        }

        /**
         * Enqueue the given node in the worklist and return the node.
         */
        private Node enqueued(Node node) {
            worklist.enqueue(node);
            return node;
        }

        /**
         * Wrapper for `Graph.exchange` to set the `changes` field.
         */
        private void exchange(Node oldNode, Node newNode) {
            Graph.exchange(oldNode, newNode);
            this.changes = true;
        }

        /**
         * Wrapper for `Util#exchangeDirOrMod` to set the `changes` field.
         */
        private void exchangeDivOrMod(Node node, Node newNode, Node newMem) {
            Util.exchangeDivOrMod(node, newNode, newMem);
            this.changes = true;
        }
    }
}
