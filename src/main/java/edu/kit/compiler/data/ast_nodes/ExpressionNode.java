package edu.kit.compiler.data.ast_nodes;

import java.util.Optional;

import edu.kit.compiler.data.AstNode;
import edu.kit.compiler.data.AstVisitor;
import edu.kit.compiler.data.DataType;
import edu.kit.compiler.data.Literal;
import edu.kit.compiler.data.Operator.BinaryOperator;
import edu.kit.compiler.data.Operator.UnaryOperator;
import lombok.Getter;

public abstract class ExpressionNode extends AstNode {

    public ExpressionNode(int line, int column, boolean hasError) {
        super(line, column, hasError);
    }

    public static class BinaryExpressionNode extends ExpressionNode {

        public BinaryExpressionNode(
            int line, int column,
            BinaryOperator operator,
            ExpressionNode leftSide, ExpressionNode rightSide,
            boolean hasError
        ) {
            super(line, column, hasError);

            this.operator = operator;

            this.leftSide = leftSide;
            this.rightSide = rightSide;
        }

        @Getter
        private BinaryOperator operator;

        @Getter
        private ExpressionNode leftSide;
        @Getter
        private ExpressionNode rightSide;

        @Override
        public <T> T accept(AstVisitor<T> visitor) {
            return visitor.visit(this);
        }

    }

    public static class UnaryExpressionNode extends ExpressionNode {

        public UnaryExpressionNode(
            int line, int column,
            UnaryOperator operator, ExpressionNode expression,
            boolean hasError
        ) {
            super(line, column, hasError);

            this.operator = operator;

            this.expression = expression;
        }

        @Getter
        private UnaryOperator operator;

        @Getter
        private ExpressionNode expression;

        @Override
        public <T> T accept(AstVisitor<T> visitor) {
            return visitor.visit(this);
        }

    }

    public static class MethodInvocationExpressionNode extends ExpressionNode {

        public MethodInvocationExpressionNode(
            int line, int column,
            Optional<ExpressionNode> object, int name, Iterable<ExpressionNode> arguments,
            boolean hasError
        ) {
            super(line, column, hasError);

            this.object = object;
            this.name = name;
            this.arguments = arguments;
        }

        @Getter
        private Optional<ExpressionNode> object;
        @Getter
        private int name;
        @Getter
        private Iterable<ExpressionNode> arguments;

        @Override
        public <T> T accept(AstVisitor<T> visitor) {
            return visitor.visit(this);
        }

    }

    public static class FieldAccessExpressionNode extends ExpressionNode {

        public FieldAccessExpressionNode(
            int line, int column,
            ExpressionNode object, int name,
            boolean hasError
        ) {
            super(line, column, hasError);

            this.object = object;
            this.name = name;
        }

        @Getter
        private ExpressionNode object;
        @Getter
        private int name;

        @Override
        public <T> T accept(AstVisitor<T> visitor) {
            return visitor.visit(this);
        }

    }

    public static class ArrayAccessExpressionNode extends ExpressionNode {

        public ArrayAccessExpressionNode(
            int line, int column,
            ExpressionNode object, ExpressionNode expression,
            boolean hasError
        ) {
            super(line, column, hasError);

            this.object = object;
            this.expression = expression;
        }

        @Getter
        private ExpressionNode object;
        @Getter
        private ExpressionNode expression;

        @Override
        public <T> T accept(AstVisitor<T> visitor) {
            return visitor.visit(this);
        }

    }

    public static class IdentifierExpressionNode extends ExpressionNode {

        public IdentifierExpressionNode(int line, int column, int identifier, boolean hasError) {
            super(line, column, hasError);

            this.identifier = identifier;
        }

        @Getter
        private int identifier;

        @Override
        public <T> T accept(AstVisitor<T> visitor) {
            return visitor.visit(this);
        }

    }

    public static class ValueExpressionNode extends ExpressionNode {

        public ValueExpressionNode(
            int line, int column,
            ValueExpressionType type,
            boolean hasError
        ) {
            super(line, column, hasError);

            this.type = type;
            this.literalValue = Optional.empty();
        }

        public ValueExpressionNode(
            int line, int column,
            ValueExpressionType type, Literal value,
            boolean hasError
        ) {
            super(line, column, hasError);

            this.type = type;
            this.literalValue = Optional.of(value);
        }

        @Getter
        private ValueExpressionType type;
        @Getter
        private Optional<Literal> literalValue;

        @Override
        public <T> T accept(AstVisitor<T> visitor) {
            return visitor.visit(this);
        }

    }

    public static enum ValueExpressionType {
        Null,
        False,
        True,
        IntegerLiteral,
        This
    }

    public static class NewObjectExpressionNode extends ExpressionNode {

        public NewObjectExpressionNode(
            int line, int column,
            int typeName,
            boolean hasError
        ) {
            super(line, column, hasError);

            this.typeName = typeName;
        }

        @Getter
        private int typeName;

        @Override
        public <T> T accept(AstVisitor<T> visitor) {
            return visitor.visit(this);
        }

    }

    public static class NewArrayExpressionNode extends ExpressionNode {

        public NewArrayExpressionNode(
            int line, int column,
            DataType type, ExpressionNode length, int dimensions,
            boolean hasError
        ) {
            super(line, column, hasError);

            this.type = type;
            this.length = length;
            this.dimensions = dimensions;
        }

        @Getter
        private DataType type;
        @Getter
        private ExpressionNode length;
        @Getter
        private int dimensions;

        @Override
        public <T> T accept(AstVisitor<T> visitor) {
            return visitor.visit(this);
        }

    }

}
