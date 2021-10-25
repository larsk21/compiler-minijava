package edu.kit.compiler.data;

import java.util.Optional;

/**
 * Represents a Token created by the lexer. A Token can have a value of type
 * Integer or String associated with it, depending on the type of the Token.
 */
public class Token {

    /**
     * Create a new Token without any value.
     * 
     * @param type Type of this Token.
     * @param line Line position in the file.
     * @param column Column position in the file.
     */
    public Token(TokenType type, int line, int column) {
        this.line = line;
        this.column = column;

        intValue = Optional.empty();
        stringValue = Optional.empty();
    }

    /**
     * Create a new Token with a value of type integer.
     * 
     * @param type Type of this Token.
     * @param line Line position in the file.
     * @param column Column position in the file.
     * @param intValue Integer value associated with this Token.
     */
    public Token(TokenType type, int line, int column, int intValue) {
        this(type, line, column);

        this.intValue = Optional.of(intValue);
    }

    /**
     * Create a new Token with a value of type string.
     * 
     * @param type Type of this Token.
     * @param line Line position in the file.
     * @param column Column position in the file.
     * @param intValue Integer value associated with this Token.
     */
    public Token(TokenType type, int line, int column, String stringValue) {
        this(type, line, column);

        this.stringValue = Optional.of(stringValue);
    }

    private TokenType type;
    private int line;
    private int column;

    private Optional<Integer> intValue;
    private Optional<String> stringValue;

    /**
     * Get the type of this Token.
     */
    public TokenType getType() {
        return type;
    }

    /**
     * Get the line position in the file.
     */
    public int getLine() {
        return line;
    }

    /**
     * Get the column position in the file.
     */
    public int getColumn() {
        return column;
    }

    /**
     * Get the optional integer value associated with this Token.
     */
    public Optional<Integer> getIntValue() {
        return intValue;
    }

    /**
     * Get the optional string value associated with this Token.
     * @return
     */
    public Optional<String> getStringValue() {
        return stringValue;
    }

}
