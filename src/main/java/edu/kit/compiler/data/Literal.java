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
     * Create a literal using the textual representation of the given integer.
     * 
     * @param value the integer to be represented.
     * @return a literal with the given integer value.
     */
    public static Literal ofValue(int value) {
        return new Literal(Integer.toString(value));
    }
}
