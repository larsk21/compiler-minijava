package edu.kit.compiler.transform;


import edu.kit.compiler.data.AstVisitor;
import edu.kit.compiler.data.DataType;
import edu.kit.compiler.data.DataType.DataTypeClass;
import edu.kit.compiler.data.Operator.UnaryOperator;
import edu.kit.compiler.data.ast_nodes.ExpressionNode;
import edu.kit.compiler.data.ast_nodes.ExpressionNode.ArrayAccessExpressionNode;
import edu.kit.compiler.data.ast_nodes.ExpressionNode.BinaryExpressionNode;
import edu.kit.compiler.data.ast_nodes.ExpressionNode.FieldAccessExpressionNode;
import edu.kit.compiler.data.ast_nodes.ExpressionNode.IdentifierExpressionNode;
import edu.kit.compiler.data.ast_nodes.ExpressionNode.MethodInvocationExpressionNode;
import edu.kit.compiler.data.ast_nodes.ExpressionNode.ThisExpressionNode;
import edu.kit.compiler.data.ast_nodes.ExpressionNode.UnaryExpressionNode;
import edu.kit.compiler.data.ast_nodes.ExpressionNode.ValueExpressionNode;
import firm.Construction;
import firm.Mode;
import firm.Relation;
import firm.nodes.Block;
import firm.nodes.Cond;
import firm.nodes.Node;
import lombok.AllArgsConstructor;
import lombok.Getter;

public class IRBooleanExpressions {
    private static final DataType boolType = new DataType(DataTypeClass.Boolean);
    private static final ValueOrConditionalDecisionVisitor vocdVisitor = new ValueOrConditionalDecisionVisitor();

    public static Node asValue(TransformContext context, ExpressionNode expr) {
        assert expr.getResultType().equals(boolType);
        return expr.accept(new ValueVisitor(context));
    }

    public static void asConditional(TransformContext context, ExpressionNode expr,
                                     Block trueBranch, Block falseBranch) {
        assert expr.getResultType().equals(boolType);
        expr.accept(new ConditionalVisitor(context, trueBranch, falseBranch));
    }

    @AllArgsConstructor
    private static class ValueVisitor implements AstVisitor<Node> {
        private TransformContext context;

        @Override
        public Node visit(BinaryExpressionNode expr) {
            return switch (expr.getOperator()) {
                case Assignment -> {
                    ExpressionNode left = expr.getLeftSide();
                    ExpressionNode right = expr.getRightSide();
                    if (preferAsValue(right)) {
                        yield (new IRExpressionVisitor(context)).handleAssignment(left, right);
                    } else {
                        yield fromConditional(expr);
                    }
                }
                default -> fromConditional(expr);
            };
        }

        @Override
        public Node visit(UnaryExpressionNode expr) {
            assert expr.getOperator() == UnaryOperator.LogicalNegation;

            /**
             * We try to create some optimized code here, because
             * optimizing conditionals/branches afterwards is not that simple.
             */
            ExpressionNode inner = expr.getExpression();
            if (inner instanceof UnaryExpressionNode) {
                return evalExpression(context, ((UnaryExpressionNode)inner).getExpression());
            } else if (preferAsValue(inner)) {
                // calculate inverse via XOR
                // This assumes that a boolean one is always the integer '1'!
                Construction con = context.getConstruction();
                Node value = evalExpression(context, inner);
                Node constOne = con.newConst(1, Mode.getBu());
                return con.newEor(value, constOne);
            } else {
                return fromConditional(expr);
            }
        }

        @Override
        public Node visit(ValueExpressionNode expr) {
            Construction con = context.getConstruction();
            return switch (expr.getValueType()) {
                case True -> con.newConst(1, Mode.getBu());
                case False -> con.newConst(0, Mode.getBu());
                default -> throw new IllegalStateException();
            };
        }

        private Node fromConditional(ExpressionNode expr) {
            // manual phi (possibly)
            Construction con = context.getConstruction();
            Block trueBranch = con.newBlock();
            Block falseBranch = con.newBlock();
            IRBooleanExpressions.asConditional(context, expr, trueBranch, falseBranch);

            trueBranch.mature();
            con.setCurrentBlock(trueBranch);
            Node constOne = con.newConst(1, Mode.getBu());
            Node jmpA = con.newJmp();
            falseBranch.mature();
            con.setCurrentBlock(falseBranch);
            Node constZero = con.newConst(0, Mode.getBu());
            Node jmpB = con.newJmp();

            Block finalBlock = con.newBlock();
            finalBlock.addPred(jmpA);
            finalBlock.addPred(jmpB);
            finalBlock.mature();
            con.setCurrentBlock(finalBlock);
            return con.newPhi(new Node[] {constOne, constZero}, Mode.getBu());
        }
    }

    @AllArgsConstructor
    private static class ConditionalVisitor implements AstVisitor<Void> {
        private TransformContext context;
        private Block trueBranch;
        private Block falseBranch;

        @Override
        public Void visit(BinaryExpressionNode expr) {
            Construction con = context.getConstruction();
            ExpressionNode left = expr.getLeftSide();
            ExpressionNode right = expr.getRightSide();
            return switch (expr.getOperator()) {
                case Assignment -> {
                    // Create separate assignments for both branches
                    // Note: we need to create new blocks, because trueBranch and falseBranch
                    // might have more predecessors.
                    var expressionVisitor = new IRExpressionVisitor(context);
                    Block assignTrue = con.newBlock();
                    Block assignFalse = con.newBlock();
                    asConditional(right, assignTrue, assignFalse);
                    assignTrue.mature();
                    con.setCurrentBlock(assignTrue);
                    Node jmpTrue = con.newJmp();
                    trueBranch.addPred(jmpTrue);
                    trueBranch.mature();
                    expressionVisitor.handleAssignment(left, con.newConst(1, Mode.getBu()));
                    assignFalse.mature();
                    con.setCurrentBlock(assignFalse);
                    expressionVisitor.handleAssignment(left, con.newConst(0, Mode.getBu()));
                    Node jmpFalse = con.newJmp();
                    falseBranch.addPred(jmpFalse);
                    falseBranch.mature();
                    yield (Void)null;
                }
                case LogicalOr -> {
                    Block rightBranch = con.newBlock();
                    asConditional(left, trueBranch, rightBranch);
                    rightBranch.mature();
                    con.setCurrentBlock(rightBranch);
                    yield asConditional(right, trueBranch, falseBranch);
                }
                case LogicalAnd -> {
                    Block rightBranch = con.newBlock();
                    asConditional(left, rightBranch, falseBranch);
                    rightBranch.mature();
                    con.setCurrentBlock(rightBranch);
                    yield asConditional(right, trueBranch, falseBranch);
                }
                case Equal -> handleComparison(left, right, Relation.Equal);
                case NotEqual -> handleComparison(left, right, Relation.LessGreater);
                case LessThan -> handleComparison(left, right, Relation.Less);
                case LessThanOrEqual -> handleComparison(left, right, Relation.LessEqual);
                case GreaterThan -> handleComparison(left, right, Relation.Greater);
                case GreaterThanOrEqual -> handleComparison(left, right, Relation.GreaterEqual);
                default -> throw new IllegalStateException();
            };
        }

        @Override
        public Void visit(UnaryExpressionNode expr) {
            assert expr.getOperator() == UnaryOperator.LogicalNegation;
            // swap branches
            return asConditional(expr.getExpression(), falseBranch, trueBranch);
        }

        @Override
        public Void visit(MethodInvocationExpressionNode expr) {
            return fromValue(expr);
        }

        @Override
        public Void visit(FieldAccessExpressionNode expr) {
            return fromValue(expr);
        }

        @Override
        public Void visit(ArrayAccessExpressionNode expr) {
            return fromValue(expr);
        }

        @Override
        public Void visit(IdentifierExpressionNode expr) {
            return fromValue(expr);
        }

        @Override
        public Void visit(ValueExpressionNode expr) {
            Construction con = context.getConstruction();
            Node jmp = con.newJmp();
            switch (expr.getValueType()) {
                case True -> trueBranch.addPred(jmp);
                case False -> falseBranch.addPred(jmp);
                default -> throw new IllegalStateException();
            };
            return (Void)null;
        }

        private Void fromValue(ExpressionNode expr) {
            Construction con = context.getConstruction();
            Node left = evalExpression(context, expr);
            Node right = con.newConst(0, Mode.getBu());
            return fromCmp(con.newCmp(left, right, Relation.LessGreater));
        }

        private Void handleComparison(ExpressionNode left, ExpressionNode right, Relation rel) {
            Construction con = context.getConstruction();
            Node lhs = evalExpression(context, left);
            Node rhs = evalExpression(context, right);
            Node cmp = con.newCmp(lhs, rhs, rel);
            return fromCmp(cmp);
        }

        private Void fromCmp(Node cmp) {
            Construction con = context.getConstruction();
            Node getCond = con.newCond(cmp);
            Node projTrue = con.newProj(getCond, Mode.getX(), Cond.pnTrue);
            trueBranch.addPred(projTrue);
            Node projFalse = con.newProj(getCond, Mode.getX(), Cond.pnFalse);
            falseBranch.addPred(projFalse);
            return (Void)null;
        }

        private Void asConditional(ExpressionNode expr, Block trueBranch, Block falseBranch) {
            return expr.accept(new ConditionalVisitor(context, trueBranch, falseBranch));
        }
    }

    /**
     * Returns whether evaluation as value (instead of as conditional) is preferable,
     * in a context where a value should be returned.
     */
    private static class ValueOrConditionalDecisionVisitor implements AstVisitor<Boolean> {
        public Boolean visit(BinaryExpressionNode expr) {
            return switch (expr.getOperator()) {
                // recursive inspection of right side of assignment
                case Assignment -> expr.getRightSide().accept(this);
                // comparisons are always conditionals
                default -> false;
            };
        }

        public Boolean visit(UnaryExpressionNode expr) {
            // recursive inspection
            return expr.getExpression().accept(this);
        }

        public Boolean visit(MethodInvocationExpressionNode expr) { return true; }
        public Boolean visit(FieldAccessExpressionNode expr) { return true; }
        public Boolean visit(ArrayAccessExpressionNode expr) { return true; }
        public Boolean visit(IdentifierExpressionNode expr) { return true; }
        public Boolean visit(ValueExpressionNode expr) { return true; }
    }

    private static boolean preferAsValue(ExpressionNode expr) {
        return expr.accept(vocdVisitor);
    }

    private static Node evalExpression(TransformContext context, ExpressionNode expr) {
        return expr.accept(new IRExpressionVisitor(context));
    }
}
