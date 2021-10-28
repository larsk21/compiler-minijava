package edu.kit.compiler.lexer;

/**
 * Provides static methods to classify characters in MiniJava programs.
 */
public class Character {
    /**
     * A white space as described in the MiniJava language specification may be
     * an ASCII space, new line, carriage return or (horizontal) tab.
     * 
     * @return true if c is a MiniJava white space.
     */
    public static boolean isWhiteSpace(int c) {
        return c == ' ' || c == '\n' || c == '\r' || c == '\t';
    }

    /**
     * A digit in MiniJava must be an ASCII digit.
     * 
     * @return true if c is a MiniJava digit.
     */
    public static boolean isDigit(int c) {
        return '0' <= c && c <= '9';
    }

    /**
     * The first character of a MiniJava identifier must be an ASCII letter or
     * underscore.
     * 
     * @return true if c is permissible as first character in a MiniJava identifier.
     */
    public static boolean isIdentifierStart(int c) {
        return ('a' <= c && c <= 'z') || ('A' <= c && c <= 'Z') || (c == '_');
    }

    /**
     * Any character in a MiniJava identifier must be an ASCII letter, digit or
     * underscore.
     * 
     * @return true if c is permissible as part of a MiniJava identifier as other than the first character.
     */
    public static boolean isIdentifierPart(int c) {
        return isIdentifierStart(c) || isDigit(c);
    }

    /**
     * @return true if c is a NUL (\0) character.
     */
    public static boolean isNull(int c) {
        return c == '\u0000';
    }

    /**
     * A comment in MiniJava is opened by the characters '/*'.
     * 
     * @return true if c1 and c2 are the start of a comment.
     */
    public static boolean isCommentStart(int c1, int c2) {
        return c1 == '/' && c2 == '*';
    }

    /**
     * A comment in MiniJava is opened by the characters '*\/'.
     *
     * @return true if c1 and c2 are the end of a comment.
     */
    public static boolean isCommentEnd(int c1, int c2) {
        return c1 == '*' && c2 == '/';
    }

    /**
     * @return true if c is smaller than zero.
     */
    public static boolean isEndOfStream(int c) {
        return c < 0;
    }

}
