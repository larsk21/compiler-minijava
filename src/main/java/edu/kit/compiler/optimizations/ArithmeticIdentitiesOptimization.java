package edu.kit.compiler.optimizations;

import edu.kit.compiler.io.DequeWorklist;
import edu.kit.compiler.io.Worklist;
import firm.BackEdges;
import firm.Graph;
import firm.Mode;
import firm.TargetValue;
import firm.bindings.binding_irnode.ir_opcode;
import firm.nodes.*;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;



/**
 * Assumes that constants have already been eliminated.
 * 
 * 
 * Note: some optimizations like `0 * x --> 0` or `-(x - y) = (y - x)` may
 * seem like the might remove or reorder function calls. However, the memory
 * dependencies between these calls (should) ensure correctness.
 * 
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
 * - Replace division by constant -1 with negation (right operand only)
 * - Replace modulo constant 1 or -1 with constant 0 (right operand only)
 * 
 * 
 * Optimizations better done during instruction selection:
 * - Replace multiplication by constant power of two with shifts or lea
 * - Replace division by constant power of two with shifts or lea
 *   -> These and similar optimizations are more dependant on the target architecture
 */
public final class ArithmeticIdentitiesOptimization implements Optimization {
    @Override
    public void optimize(Graph graph) {
        var visitor = new Visitor(graph);
        visitor.apply();

        
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private static final class Visitor extends NodeVisitor.Default {
        private final Graph graph;
        
        private final Worklist<Node> worklist = new DequeWorklist<>();

        private final void apply() {
            graph.walkTopological(new WorklistFiller(worklist));
        
            while (!worklist.isEmpty()) {
                worklist.dequeue().accept(this);
            }
        }

        @Override
        public void visit(Add node) {
            if (isConstZero(node.getLeft())) {
                // 0 + x  -->  x
                Graph.exchange(node, node.getRight());
            } else if (isConstZero(node.getRight())) {
                // x + 0  -->  x
                Graph.exchange(node, node.getLeft());
            } else if (node.getLeft().getOpCode() == ir_opcode.iro_Minus) {
                // (-x) + y  -->  y - x
                assert node.getLeft().getPredCount() == 1;
                var left = node.getRight();
                var right = node.getLeft().getPred(0);
                var sub = graph.newSub(node.getBlock(), left, right);
                Graph.exchange(node, enqueued(sub));
            } else if (node.getRight().getOpCode() == ir_opcode.iro_Minus) {
                // x + (-y)  -->  x - y
                assert node.getRight().getPredCount() == 1;
                var left = node.getLeft();
                var right = node.getRight().getPred(0);
                var sub = graph.newSub(node.getBlock(), left, right);
                Graph.exchange(node, enqueued(sub));
            }
        }

        @Override
        public void visit(Sub node) {
            if (isConstZero(node.getLeft())) {
                // 0 - x  -->  -x
                var minusNode = graph.newMinus(node.getBlock(), node.getRight());
                worklist.enqueue(minusNode);
                Graph.exchange(node, minusNode);
            } else if (isConstZero(node.getRight())) {
                // x - 0  -->  x
                Graph.exchange(node, node.getLeft());
            } else if (node.getRight().getOpCode() == ir_opcode.iro_Minus) {
                // x - (-y)  -->  x + y
                assert node.getRight().getPredCount() == 1;
                var left = node.getLeft();
                var right = node.getRight().getPred(0);
                var add = graph.newAdd(node.getBlock(), left, right);
                Graph.exchange(node, enqueued(add));
            } else {
                var constValue = getConstValue(node.getRight());
                if (constValue != null) {
                    // x - c  -->  x + (-c)
                    var left = node.getLeft();
                    var right = graph.newConst(constValue.neg());
                    var add = graph.newAdd(node.getBlock(), left, right);
                    Graph.exchange(node, enqueued(add));
                }
            }
        }

        @Override
        public void visit(Minus node) {
            var operand = node.getOp();
            switch (operand.getOpCode()) {
                case iro_Minus -> {
                    // -(-x)  -->  x
                    assert operand.getPredCount() == 1;
                    Graph.exchange(node, operand.getPred(0));
                }
                case iro_Sub -> {
                    // -(x - y)  -->  y - x
                    System.out.println(operand.getPredCount());
                    assert operand.getPredCount() == 2;
                    var left = operand.getPred(0);
                    var right = operand.getPred(1);
                    var sub = graph.newSub(node.getBlock(), right, left);
                    Graph.exchange(node, sub);
                }
                default -> {
                }
            }
        }

        @Override
        public void visit(Mul node) {
            if (isConstOne(node.getLeft())) {
                // 1 * x  -->  x
                Graph.exchange(node, node.getRight());
            } else if (isConstOne(node.getRight())) {
                // x * 1  -->  x
                Graph.exchange(node, node.getLeft());

            } else if (isConstZero(node.getLeft())) {
                // 0 * x  -->  0
                var zero = node.getRight().getMode().getNull();
                Graph.exchange(node, graph.newConst(zero));
            } else if (isConstZero(node.getRight())) {
                // x * 0  -->  0
                var zero = node.getLeft().getMode().getNull();
                Graph.exchange(node, graph.newConst(zero));

            } else if (isConstNegOne(node.getLeft())) {
                // -1 * x  --> -x
                var minus = graph.newMinus(node.getBlock(), node.getRight());
                Graph.exchange(node, enqueued(minus));
            } else if (isConstNegOne(node.getRight())) {
                // x * -1  --> -x
                var minus = graph.newMinus(node.getBlock(), node.getLeft());
                Graph.exchange(node, enqueued(minus));
            }
        }

        @Override
        public void visit(Div node) {
            if (isConstOne(node.getRight())) {
                // x / 1   -->  x
                exchangeDivOrMod(node, node.getMem(), node.getLeft());
            } else if (isConstNegOne(node.getRight())) {
                // x / -1  -->  -x
                var minus = graph.newMinus(node.getBlock(), node.getLeft());
                exchangeDivOrMod(node, node.getMem(), enqueued(minus));
            }
        }

        @Override
        public void visit(Mod node) {
            if (isConstAbsOne(node.getRight())) {
                // x % 1   -->  0
                // x % -1  -->  0
                var zero = node.getLeft().getMode().getNull();
                exchangeDivOrMod(node, node.getMem(), graph.newConst(zero));
            }
        }

        private void exchangeDivOrMod(Node node, Node mem, Node val) {
            BackEdges.enable(graph);

            for (var edge : BackEdges.getOuts(node)) {
                if (edge.node.getMode().equals(Mode.getM())) {
                    Graph.exchange(edge.node, mem);
                } else if (edge.node.getMode().equals(val.getMode())) {
                    Graph.exchange(edge.node, val);
                } else {
                    throw new UnsupportedOperationException(
                        "Div control flow projections not supported"
                    );
                }
            }
            BackEdges.disable(graph);
        }

        private static boolean isConst(Node node) {
            return node.getOpCode() == ir_opcode.iro_Const;
        }

        private static TargetValue getConstValue(Node node) {
            return isConst(node) ? ((Const)node).getTarval() : null;
        }

        private static boolean isConstZero(Node node) {
            return isConst(node) && ((Const)node).getTarval().isNull();
        }

        private static boolean isConstOne(Node node) {
            return isConst(node) && ((Const)node).getTarval().isOne();
        }

        private static boolean isConstNegOne(Node node) {
            return isConst(node) && ((Const)node).getTarval().neg().isOne();
        }

        private static boolean isConstAbsOne(Node node) {
            return isConst(node) && ((Const)node).getTarval().abs().isOne();
        }

        private Node enqueued(Node node) {
            worklist.enqueue(node);
            return node;
        }


        // --- Maybe ---

        @Override
        public void visit(And arg0) {
            // TODO Auto-generated method stub
        }

        @Override
        public void visit(Cmp arg0) {
            // TODO Auto-generated method stub
            
        }

        @Override
        public void visit(Not arg0) {
            // TODO Auto-generated method stub
            
        }

        @Override
        public void visit(Or arg0) {
            // TODO Auto-generated method stub
            
        }


    }
}
