package edu.kit.compiler.optimizations.constant_folding;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

import edu.kit.compiler.io.StackWorklist;
import edu.kit.compiler.io.Worklist;
import edu.kit.compiler.optimizations.WorklistFiller;

import firm.BackEdges;
import firm.Graph;
import firm.TargetValue;
import firm.BackEdges.Edge;
import firm.nodes.*;

import lombok.Getter;

import static edu.kit.compiler.optimizations.constant_folding.TargetValueLatticeElement.*;

/**
 * Analysis that finds constant values for value nodes where possible.
 */
public class ConstantAnalysis {

    /**
     * Create a new ConstantAnalysis on the given graph.
     */
    public ConstantAnalysis(Graph graph) {
        this.graph = graph;

        worklist = new StackWorklist<>();
        nodeValues = new HashMap<>();
    }

    private Graph graph;

    private Worklist<Node> worklist;
    /**
     * Get the mapping of nodes to lattice elements after the graph has been
     * analyzed.
     * 
     * The result of this method is undefined before the first call to
     * `analyze`.
     */
    @Getter
    private Map<Node, TargetValueLatticeElement> nodeValues;

    /**
     * Analyze the given graph to find constant values for value nodes.
     * 
     * This method temporarily enables BackEdges in the graph and resets the
     * value to the value before this method call.
     */
    public void analyze() {
        boolean backEdgesEnabled = BackEdges.enabled(graph);
        BackEdges.enable(graph);

        graph.walkTopological(new WorklistFiller(worklist));

        ConstantAnalysisVisitor visitor = new ConstantAnalysisVisitor();
        while (!worklist.isEmpty()) {
            Node node = worklist.dequeue();
            node.accept(visitor);
        }

        if (!backEdgesEnabled) {
            BackEdges.disable(graph);
        }
    }

    /**
     * Get the lattice element associated with the given node or `unknown` if
     * there is no element associated.
     */
    private TargetValueLatticeElement getValue(Node node) {
        return nodeValues.getOrDefault(node, unknown());
    }

    /**
     * Update the lattice element associated with the given node. If the given
     * element is not equal to the element associated with the given node
     * before, then the join of the old and new value is associated with the
     * given node and all nodes that depend on the given node are inserted in
     * the worklist.
     */
    private void updateValue(Node node, TargetValueLatticeElement value) {
        TargetValueLatticeElement oldValue = getValue(node);
        if (!oldValue.isEqualTo(value)) {
            nodeValues.put(node, oldValue.join(value));

            for (Edge edge : BackEdges.getOuts(node)) {
                worklist.enqueue(edge.node);
            }
        }
    }

    /**
     * Firm node visitor that finds value nodes with a constant value and
     * operation nodes with constant arguments. For operation node with
     * constant arguments, the result of the operation performed on the
     * constant values is associated with that node.
     */
    private class ConstantAnalysisVisitor extends NodeVisitor.Default {

        /**
         * Visit a unary operation node with a single operand, as well as an
         * operation to perform the unary operation on a constant value.
         * 
         * If the value associated with the operand is `conflicting`, then the
         * value associated with the operation is also `conflicting`. If the
         * value associated with the operand is constant, then the operation is
         * performed on that constant value. If the resulting TargetValue is
         * constant and finite, then that constant value is associated with the
         * operation, otherwise `conflicting` is associated with the operation
         * node.
         */
        private void visitUnary(Node node, Node operand, Function<TargetValue, TargetValue> operation) {
            TargetValueLatticeElement operandValue = getValue(operand);

            if (operandValue.isConflicting()) {
                updateValue(node, conflicting());
            } else if (operandValue.isConstant()) {
                TargetValue result = operation.apply(operandValue.getValue());
                if (result.isConstant() && result.isFinite()) {
                    updateValue(node, constant(result));
                } else {
                    updateValue(node, conflicting());
                }
            }
        }

        /**
         * Visit a binary operation node with two operands, as well as an
         * operation to perform the binary operation on two constant values.
         * 
         * If the value associated with at least one operand is `conflicting`,
         * then the value associated with the operation is also `conflicting`.
         * If the value associated with both operands is constant, then the
         * operation is performed on these constant values. If the resulting
         * TargetValue is constant and finite, then that constant value is
         * associated with the operation, otherwise `conflicting` is associated
         * with the operation node.
         */
        private void visitBinary(Node node, Node left, Node right, BiFunction<TargetValue, TargetValue, TargetValue> operation) {
            TargetValueLatticeElement leftValue = getValue(left);
            TargetValueLatticeElement rightValue = getValue(right);

            if (leftValue.isConflicting() || rightValue.isConflicting()) {
                updateValue(node, conflicting());
            } else if (leftValue.isConstant() && rightValue.isConstant()) {
                TargetValue result = operation.apply(leftValue.getValue(), rightValue.getValue());
                if (result.isConstant() && result.isFinite()) {
                    updateValue(node, constant(result));
                } else {
                    updateValue(node, conflicting());
                }
            }
        }

        /**
         * Visit a binary operation node with two operands and a boolean
         * result, as well as an operation to perform the binary operation on
         * two constant values.
         * 
         * If the value associated with at least one operand is `conflicting`,
         * then the value associated with the operation is also `conflicting`.
         * If the value associated with both operands is constant, then the
         * operation is performed on these constant values. If the resulting
         * TargetValue is constant and finite, then that constant value is
         * associated with the operation, otherwise `conflicting` is associated
         * with the operation node.
         */
        private void visitBooleanBinary(Node node, Node left, Node right, BiFunction<TargetValue, TargetValue, Boolean> operation) {
            TargetValueLatticeElement leftValue = getValue(left);
            TargetValueLatticeElement rightValue = getValue(right);

            if (leftValue.isConflicting() || rightValue.isConflicting()) {
                updateValue(node, conflicting());
            } else if (leftValue.isConstant() && rightValue.isConstant()) {
                boolean result = operation.apply(leftValue.getValue(), rightValue.getValue());
                updateValue(node, constant(result));
            }
        }

        @Override
        public void defaultVisit(Node node) {
            updateValue(node, conflicting());
        }

        @Override
        public void visit(Add node) {
            visitBinary(node, node.getLeft(), node.getRight(), (left, right) -> left.add(right));
        }

        @Override
        public void visit(And node) {
            visitBinary(node, node.getLeft(), node.getRight(), (left, right) -> left.and(right));
        }

        @Override
        public void visit(Cmp node) {
            visitBooleanBinary(node, node.getLeft(), node.getRight(), (left, right) -> node.getRelation().contains(left.compare(right)));
        }

        @Override
        public void visit(Cond node) {
            TargetValueLatticeElement result = getValue(node.getSelector());
            updateValue(node, result);
        }

        @Override
        public void visit(Const node) {
            TargetValue result = node.getTarval();
            updateValue(node, constant(result));
        }

        @Override
        public void visit(Div node) {
            visitBinary(node, node.getLeft(), node.getRight(), (left, right) -> left.div(right));
        }

        @Override
        public void visit(Eor node) {
            visitBinary(node, node.getLeft(), node.getRight(), (left, right) -> left.eor(right));
        }

        @Override
        public void visit(Id node) {
            visitUnary(node, node.getPred(), operand -> operand);
        }

        @Override
        public void visit(Minus node) {
            visitUnary(node, node.getOp(), operand -> operand.neg());
        }

        @Override
        public void visit(Mod node) {
            visitBinary(node, node.getLeft(), node.getRight(), (left, right) -> left.mod(right));
        }

        @Override
        public void visit(Mul node) {
            visitBinary(node, node.getLeft(), node.getRight(), (left, right) -> left.mul(right));
        }

        @Override
        public void visit(Not node) {
            visitUnary(node, node.getOp(), operand -> operand.not());
        }

        @Override
        public void visit(Or node) {
            visitBinary(node, node.getLeft(), node.getRight(), (left, right) -> left.or(right));
        }

        @Override
        public void visit(Phi node) {
            TargetValueLatticeElement value = unknown();
            for (Node pred : node.getPreds()) {
                value = value.join(getValue(pred));
            }
            updateValue(node, value);
        }

        @Override
        public void visit(Shl node) {
            visitBinary(node, node.getLeft(), node.getRight(), (left, right) -> left.shl(right));
        }

        @Override
        public void visit(Shr node) {
            visitBinary(node, node.getLeft(), node.getRight(), (left, right) -> left.shr(right));
        }

        @Override
        public void visit(Shrs node) {
            visitBinary(node, node.getLeft(), node.getRight(), (left, right) -> left.shrs(right));
        }

        @Override
        public void visit(Sub node) {
            visitBinary(node, node.getLeft(), node.getRight(), (left, right) -> left.sub(right));
        }

    }

}
