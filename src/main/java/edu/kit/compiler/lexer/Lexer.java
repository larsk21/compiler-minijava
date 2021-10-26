package edu.kit.compiler.lexer;

import edu.kit.compiler.data.Token;
import edu.kit.compiler.io.CharCounterLookaheadIterator;

public class Lexer {

    private CharCounterLookaheadIterator charStream;
    private StringTable stringTable;

    public Lexer(CharCounterLookaheadIterator iterator) {
        this.charStream = iterator;
        this.stringTable = new StringTable();
    }

    public StringTable getStringTable() {
        return stringTable;
    }

    public Token getNextToken() throws LexException {
        while (Character.isWhitespace(charStream.get())) {
            charStream.next();
        }

        if (isDigit(charStream.get())) {
            return lexIntegerLiteral();
        } else if (isIdentifierStart(charStream.get())) {
            return lexKeywordOrIdentifier();
        } else {
            return lexOperator();
        }
    }

    private Token lexIntegerLiteral() throws LexException {
        throw new RuntimeException();
    }

    private Token lexKeywordOrIdentifier() throws LexException {
        throw new RuntimeException();
    }

    private Token lexOperator() throws LexException {
        throw new RuntimeException();
    }

    private static boolean isDigit(char c) {
        // The builtin 'isDigit' allows non ASCII digits,
        // which is not allowed in MiniJava.
        return '0' <= c && c <= '9';
    }

    private static boolean isIdentifierStart(char c) {
        return ('a' <= c && c <= 'z') || ('A' <= c && c <= 'Z') || (c == '_');
    }
}
