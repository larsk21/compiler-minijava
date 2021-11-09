package edu.kit.compiler.data;

import edu.kit.compiler.parser.ParseException;

/**
 * Represents the supported operators in MiniJava.
 */
public final class Operator {

    private Operator() {}

    /**
     * Represents the supported binary operators in MiniJava.
     */
    public static enum BinaryOperator {
        Assignment,
        LogicalOr,
        LogicalAnd,
        Equal,
        NotEqual,
        LessThan,
        LessThanOrEqual,
        GreaterThan,
        GreaterThanOrEqual,
        Addition,
        Subtraction,
        Multiplication,
        Division,
        Modulo;

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
    }

    /**
     * Represents the supported unary operators in MiniJava.
     */
    public static enum UnaryOperator {
        LogicalNegation,
        ArithmeticNegation
    }

}
