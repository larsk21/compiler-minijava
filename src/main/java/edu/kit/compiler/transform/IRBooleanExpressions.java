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
import firm.nodes.Node;
import lombok.AllArgsConstructor;
import lombok.Getter;

public class IRBooleanExpressions {
    private static final DataType boolType = new DataType(DataTypeClass.Boolean);

    public static Node asValue(TransformContext context, ExpressionNode expr) {
        if (!expr.getResultType().equals(boolType)) {
            throw new IllegalArgumentException();
        }
        return expr.accept(new ValueVisitor(context));
    }

    public static void asConditional(TransformContext context, ExpressionNode expr,
                                     Block trueBranch, Block falseBranch) {
        if (!expr.getResultType().equals(boolType)) {
            throw new IllegalArgumentException();
        }
        expr.accept(new ConditionalVisitor(context, trueBranch, falseBranch));
    }

    @AllArgsConstructor
    private static class ValueVisitor implements AstVisitor<Node> {
        private TransformContext context;

        public Node visit(BinaryExpressionNode expr) { 
            return fromConditional(expr);
        }

        public Node visit(UnaryExpressionNode expr) {
            if (expr.getOperator() != UnaryOperator.LogicalNegation) {
                throw new IllegalArgumentException();
            }

            /**
             * We try to create some optimized code here, because
             * optimizing conditionals/branches afterwards is not that simple.
             */
            ExpressionNode inner = expr.getExpression();
            if (inner instanceof BinaryExpressionNode) {
                return fromConditional(expr);
            } else if (inner instanceof UnaryExpressionNode) {
                return evalExpression(context, ((UnaryExpressionNode)inner).getExpression());
            } else {
                // calculate inverse via XOR
                // This assumes that a boolean one is always the integer '1'!
                Construction con = context.getConstruction();
                Node value = evalExpression(context, expr);
                Node constOne = con.newConst(1, Mode.getBu());
                return con.newEor(value, constOne);
            }
        }

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
            finalBlock.addPred(trueBranch);
            finalBlock.addPred(falseBranch);
            finalBlock.mature();
            return con.newPhi(new Node[] {trueBranch, falseBranch}, Mode.getBu());
        }
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
