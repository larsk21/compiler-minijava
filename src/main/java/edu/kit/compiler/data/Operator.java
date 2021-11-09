package edu.kit.compiler.data;

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
        Modulo
    }

    /**
     * Represents the supported unary operators in MiniJava.
     */
    public static enum UnaryOperator {
        LogicalNegation,
        ArithmeticNegation
    }

}
