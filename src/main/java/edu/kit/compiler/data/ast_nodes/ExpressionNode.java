package edu.kit.compiler.data.ast_nodes;

import java.util.List;
import java.util.Optional;

import edu.kit.compiler.data.AstNode;
import edu.kit.compiler.data.AstVisitor;
import edu.kit.compiler.data.DataType;
import edu.kit.compiler.data.Literal;
import edu.kit.compiler.data.Operator.BinaryOperator;
import edu.kit.compiler.data.Operator.UnaryOperator;
import edu.kit.compiler.semantic.Definition;
import edu.kit.compiler.semantic.Reference;

import lombok.Getter;
import lombok.Setter;

public abstract class ExpressionNode extends AstNode {

    public ExpressionNode(int line, int column, boolean hasError) {
        super(line, column, hasError);
    }

    @Getter
    @Setter
    private DataType resultType;

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
            Optional<ExpressionNode> object, int name, List<ExpressionNode> arguments,
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
        private List<ExpressionNode> arguments;

        @Getter
        @Setter
        private MethodNode definition;

        @Override
        public <T> T accept(AstVisitor<T> visitor) {
            return visitor.visit(this);
        }

        /**
         * Remove the object of this method invocation.
         * 
         * This can be useful if the object is not needed for calling the
         * method, for example if the method is in the standard library.
         */
        public void removeObject() {
            object = Optional.empty();
        }

    }

    public static class FieldAccessExpressionNode extends ExpressionNode implements Reference {

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

        @Getter
        @Setter
        private Definition definition;

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

    public static class IdentifierExpressionNode extends ExpressionNode implements Reference {

        public IdentifierExpressionNode(int line, int column, int identifier, boolean hasError) {
            super(line, column, hasError);

            this.identifier = identifier;
        }

        @Getter
        private int identifier;

        @Getter
        @Setter
        private Definition definition;

        @Override
        public <T> T accept(AstVisitor<T> visitor) {
            return visitor.visit(this);
        }

    }

    public static class ThisExpressionNode extends ExpressionNode {

        public ThisExpressionNode(int line, int column, boolean hasError) {
            super(line, column, hasError);
        }

        @Getter
        @Setter
        private ClassNode definition;

        @Override
        public <T> T accept(AstVisitor<T> visitor) {
            return visitor.visit(this);
        }

    }

    public static class ValueExpressionNode extends ExpressionNode {

        public ValueExpressionNode(
            int line, int column,
            ValueExpressionType valueType,
            boolean hasError
        ) {
            super(line, column, hasError);

            this.valueType = valueType;
            this.literalValue = Optional.empty();
        }

        public ValueExpressionNode(
            int line, int column,
            ValueExpressionType valueType, Literal value,
            boolean hasError
        ) {
            super(line, column, hasError);

            this.valueType = valueType;
            this.literalValue = Optional.of(value);
        }

        @Getter
        private ValueExpressionType valueType;
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
        IntegerLiteral
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
            DataType elementType, ExpressionNode length, int dimensions,
            boolean hasError
        ) {
            super(line, column, hasError);

            this.elementType = elementType;
            this.length = length;
            this.dimensions = dimensions;
        }

        @Getter
        private DataType elementType;
        @Getter
        private ExpressionNode length;

        /**
         * 1-dimensional array => dimensions = 1,
         * 2-dimensional array => dimensions = 2,
         * ...
         */
        @Getter
        private int dimensions;

        @Override
        public <T> T accept(AstVisitor<T> visitor) {
            return visitor.visit(this);
        }

    }

}
