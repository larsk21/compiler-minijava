package edu.kit.compiler.data;

import edu.kit.compiler.parser.ParseException;
import edu.kit.compiler.data.DataType.DataTypeClass;

/**
 * Represents the supported operators in MiniJava.
 */
public final class Operator {

    private Operator() {}

    /**
     * Represents the supported binary operators in MiniJava.
     */
    public static enum BinaryOperator {
        Assignment("="),
        LogicalOr("||"),
        LogicalAnd("&&"),
        Equal("=="),
        NotEqual("!="),
        LessThan("<"),
        LessThanOrEqual("<="),
        GreaterThan(">"),
        GreaterThanOrEqual(">="),
        Addition("+"),
        Subtraction("-"),
        Multiplication("*"),
        Division("/"),
        Modulo("%");

        /**
         * Get the BinaryOperator from its Token representation.
         */
        public static BinaryOperator fromToken(Token token) {
            switch(token.getType()) {
                case Operator_Equal:
                    return BinaryOperator.Assignment;
                case Operator_BarBar:
                    return BinaryOperator.LogicalOr;
                case Operator_AndAnd:
                    return BinaryOperator.LogicalAnd;
                case Operator_EqualEqual:
                    return BinaryOperator.Equal;
                case Operator_NotEqual:
                    return BinaryOperator.NotEqual;
                case Operator_Smaller:
                    return BinaryOperator.LessThan;
                case Operator_SmallerEqual:
                    return BinaryOperator.LessThanOrEqual;
                case Operator_Greater:
                    return BinaryOperator.GreaterThan;
                case Operator_GreaterEqual:
                    return BinaryOperator.GreaterThanOrEqual;
                case Operator_Plus:
                    return BinaryOperator.Addition;
                case Operator_Minus:
                    return BinaryOperator.Subtraction;
                case Operator_Star:
                    return BinaryOperator.Multiplication;
                case Operator_Slash:
                    return BinaryOperator.Division;
                case Operator_Percent:
                    return BinaryOperator.Modulo;
                default:
                    throw new ParseException(token, "expected binary operator");
            }
        }

        private BinaryOperator(String representation) {
            this.representation = representation;
        }

        private String representation;

        /**
         * Get the expected argument type for all operators that have a fixed
         * argument type.
         * 
         * Examples of operators without fixed argument types: Assignment,
         * Equal, NotEqual.
         */
        public DataType getExpectedArgumentType() {
            switch (this) {
            case Addition:
            case Division:
            case GreaterThan:
            case GreaterThanOrEqual:
            case LessThan:
            case LessThanOrEqual:
            case Modulo:
            case Multiplication:
            case Subtraction:
                return new DataType(DataTypeClass.Int);
            case LogicalAnd:
            case LogicalOr:
                return new DataType(DataTypeClass.Boolean);
            default:
                throw new IllegalArgumentException("operator has no fixed argument type");
            }
        }

        /**
         * Get the result type for all operators that have a fixed result type.
         * 
         * Examples of operators without a fixed result type: Assignment.
         */
        public DataType getResultType() {
            switch (this) {
            case Addition:
            case Division:
            case Modulo:
            case Multiplication:
            case Subtraction:
                return new DataType(DataTypeClass.Int);
            case Equal:
            case GreaterThan:
            case GreaterThanOrEqual:
            case LessThan:
            case LessThanOrEqual:
            case LogicalAnd:
            case LogicalOr:
            case NotEqual:
                return new DataType(DataTypeClass.Boolean);
            default:
                throw new IllegalArgumentException("operator has no fixed result type");
            }
        }

        @Override
        public String toString() {
            return representation;
        }

    }

    /**
     * Represents the supported unary operators in MiniJava.
     */
    public static enum UnaryOperator {
        LogicalNegation("!"),
        ArithmeticNegation("-");

        private UnaryOperator(String representation) {
            this.representation = representation;
        }

        private String representation;

        /**
         * Get the expected argument type for all operators that have a fixed
         * argument type.
         */
        public DataType getExpectedArgumentType() {
            switch (this) {
            case ArithmeticNegation:
                return new DataType(DataTypeClass.Int);
            case LogicalNegation:
                return new DataType(DataTypeClass.Boolean);
            default:
                throw new IllegalArgumentException("operator has no fixed argument type");
            }
        }

        /**
         * Get the result type for all operators that have a fixed result type.
         */
        public DataType getResultType() {
            switch (this) {
            case ArithmeticNegation:
                return new DataType(DataTypeClass.Int);
            case LogicalNegation:
                return new DataType(DataTypeClass.Boolean);
            default:
                throw new IllegalArgumentException("operator has no fixed result type");
            }
        }

        @Override
        public String toString() {
            return representation;
        }

    }

}
