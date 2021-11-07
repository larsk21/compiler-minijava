package edu.kit.compiler.parser;

import edu.kit.compiler.data.Token;

/**
 * Exception thrown by the Parser in case of a syntax error.
 */
public class ParseException extends RuntimeException {
    private Token token;

    /**
     * Create a new ParseException.
     * 
     * @param token Token of parser-defined error position.
     */
    public ParseException(Token token) {
        this(token, "");
    }

    /**
     * Create a new ParseException.
     * 
     * @param token Token of parser-defined error position.
     * @param message Message describing the exception further.
     */
    public ParseException(Token token, String message) {
        super(String.format("unexepected token %s%n; %s%n", token.getType().name(), message));
        this.token = token;
    }

    /**
     * Get the token that caused the exception.
     */
    public Token getToken() {
        return token;
    }

    /**
     * Get the line position of the character that caused the exception.
     */
    public int getLine() {
        return token.getLine();
    }

    /**
     * Get the column position of the character that caused the exception.
     */
    public int getColumn() {
        return token.getColumn();
    }
}

