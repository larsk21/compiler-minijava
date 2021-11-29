package edu.kit.compiler.transform;


import edu.kit.compiler.data.AstVisitor;
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
import firm.nodes.Node;
import lombok.AllArgsConstructor;
import lombok.Getter;

public class IRBooleanExpressions {
    public static Node asValue(TransformContext context, ExpressionNode expr) {
        throw new UnsupportedOperationException();
    }

    public static void asConditional(TransformContext context, ExpressionNode expr,
                                     Block trueBranch, Block falseBranch) {
        expr.accept(new ConditionalVisitor(context, trueBranch, falseBranch));
    }

    @AllArgsConstructor
    private static class ConditionalVisitor implements AstVisitor<Void> {
        private TransformContext context;
        private Block trueBranch;
        private Block falseBranch;

        @Override
        public Void visit(BinaryExpressionNode expr) {
            ExpressionNode left = expr.getLeftSide();
            ExpressionNode right = expr.getRightSide();
            return switch (expr.getOperator()) {
                case Assignment -> throw new UnsupportedOperationException();
                case LogicalOr -> {
                    Construction con = context.getConstruction();
                    Block rightBranch = con.newBlock();
                    asConditional(left, trueBranch, rightBranch);
                    rightBranch.mature();
                    con.setCurrentBlock(rightBranch);
                    yield asConditional(right, trueBranch, falseBranch);
                }
                case LogicalAnd -> {
                    Construction con = context.getConstruction();
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
            if (expr.getOperator() != UnaryOperator.LogicalNegation) {
                throw new IllegalArgumentException();
            }
            // swap branches
            return asConditional(expr, falseBranch, trueBranch);
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
            Node cmp = con.newCmp(lhs, rhs, Relation.Equal);
            return fromCmp(cmp);
        }

        private Void fromCmp(Node cmp) {
            Construction con = context.getConstruction();
            Node getCond = con.newCond(cmp);
            Node projTrue = con.newProj(getCond, Mode.getX(), 1);
            trueBranch.addPred(projTrue);
            Node projFalse = con.newProj(getCond, Mode.getX(), 0);
            falseBranch.addPred(projFalse);
            return (Void)null;
        }

        private Void asConditional(ExpressionNode expr, Block trueBranch, Block falseBranch) {
            return expr.accept(new ConditionalVisitor(context, trueBranch, falseBranch));
        }
    }

    private static Node evalExpression(TransformContext context, ExpressionNode expr) {
        return context.getConstruction().newConst(1, Mode.getBu());
    }
}
