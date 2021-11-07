package edu.kit.compiler.lexer;

/**
 * Exception thrown by the Lexer in case of unexpected characters.
 */
public class LexException extends RuntimeException {

    /**
     * Create a new LexException.
     * 
     * @param line Line position of the character that caused the exception.
     * @param column Column position of the character that caused the
     * exception.
     * @param message Message describing the exception further.
     */
    public LexException(int line, int column, String message) {
        super(message);

        this.line = line;
        this.column = column;
    }

    private int line;
    private int column;

    /**
     * Get the line position of the character that caused the exception.
     */
    public int getLine() {
        return line;
    }

    /**
     * Get the column position of the character that caused the exception.
     */
    public int getColumn() {
        return column;
    }

}
