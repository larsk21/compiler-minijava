package edu.kit.compiler.data;

import lombok.EqualsAndHashCode;

/**
 * Represents a literal value in its textual form.
 */
@EqualsAndHashCode(callSuper = false)
public final class Literal {
    private String value;

    /**
     * Create a literal with the given textual representation.
     * 
     * @param value the literal's textual representation.
     */
    public Literal(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return value;
    }

    /**
     * Returns the value of the literal when parsed as an integer.
     * 
     * @return the integer value represented by the literal.
     * @throws NumberFormatException if the literal does not represent a valid integer.
     */
    public int intValue() {
        return Integer.parseInt(value);
    }

    /**
     * Returns whether the value of the literal can be parsed as an integer
     * (i.e. it fits in in a 32-bit signed value).
     */
    public boolean isIntValue() {
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Returns the same literal, but with a prepended unary minus.
     */
    public Literal negated() {
        return new Literal("-" + value);
    }

    /**
     * Create a literal using the textual representation of the given integer.
     * 
     * @param value the integer to be represented.
     * @return a literal with the given integer value.
     */
    public static Literal ofValue(int value) {
        return new Literal(Integer.toString(value));
    }
}
