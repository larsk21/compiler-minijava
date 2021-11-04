package edu.kit.compiler.parser;

import java.util.Map;
import java.util.Optional;

import edu.kit.compiler.data.TokenType;

/**
 * Represents precedence and associativity of an operator.
 */
public class OperatorInformation {

    /**
     * Minimal possible precedence of an operator.
     */
    public static final int MIN_PRECEDENCE = 1;

    /**
     * Maximal possible precedence of an operator.
     */
    public static final int MAX_PRECEDENCE = 16;

    /**
     * Create information for an operator.
     * 
     * @param precedence Precedence of the operator, must be between
     * MIN_PRECEDENCE and MAX_PRECEDENCE (both incl.).
     * @param associativity Associativity of the operator.
     */
    public OperatorInformation(int precedence, Associativity associativity) {
        this.precedence = precedence;
        this.associativity = associativity;
    }

    private int precedence;
    private Associativity associativity;

    /**
     * Get the precedence of the operator.
     */
    public int getPrecedence() {
        return precedence;
    }

    /**
     * Get the associativity of the operator.
     */
    public Associativity getAssociativity() {
        return associativity;
    }

    /**
     * Represents the associativity of an operator.
     */
    public static enum Associativity {
        None,
        Left,
        Right
    }

    /**
     * Return the OperatorInformation for the given TokenType with the given
     * Appearence if it is defined (i.e. the given TokenType is a valid
     * operator for the given position).
     * 
     * @param type Type of the token.
     * @return Information about the operator if valid, otherwise empty.
     */
    public static Optional<OperatorInformation> getInfixOperatorInformation(TokenType type) {
        if (infixOperatorInformations.containsKey(type)) {
            return Optional.of(infixOperatorInformations.get(type));
        } else {
            return Optional.empty();
        }
    }

    private static Map<TokenType, OperatorInformation> infixOperatorInformations = Map.ofEntries(
        Map.entry(TokenType.Operator_Equal, new OperatorInformation(1, Associativity.Right)),
        Map.entry(TokenType.Operator_BarBar, new OperatorInformation(3, Associativity.Left)),
        Map.entry(TokenType.Operator_AndAnd, new OperatorInformation(4, Associativity.Left)),
        Map.entry(TokenType.Operator_EqualEqual, new OperatorInformation(8, Associativity.Left)),
        Map.entry(TokenType.Operator_NotEqual, new OperatorInformation(8, Associativity.Left)),
        Map.entry(TokenType.Operator_Smaller, new OperatorInformation(9, Associativity.None)),
        Map.entry(TokenType.Operator_SmallerEqual, new OperatorInformation(9, Associativity.None)),
        Map.entry(TokenType.Operator_Greater, new OperatorInformation(9, Associativity.None)),
        Map.entry(TokenType.Operator_GreaterEqual, new OperatorInformation(9, Associativity.None)),
        Map.entry(TokenType.Operator_Plus, new OperatorInformation(11, Associativity.Left)),
        Map.entry(TokenType.Operator_Minus, new OperatorInformation(11, Associativity.Left)),
        Map.entry(TokenType.Operator_Star, new OperatorInformation(12, Associativity.Left)),
        Map.entry(TokenType.Operator_Slash, new OperatorInformation(12, Associativity.Left)),
        Map.entry(TokenType.Operator_Percent, new OperatorInformation(12, Associativity.Left))
    );

}

