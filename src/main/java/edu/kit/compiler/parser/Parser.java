package edu.kit.compiler.parser;

import edu.kit.compiler.data.Token;
import edu.kit.compiler.data.TokenType;
import edu.kit.compiler.io.BufferedLookaheadIterator;
import edu.kit.compiler.io.LookaheadIterator;
import edu.kit.compiler.lexer.Lexer;

import static edu.kit.compiler.data.TokenType.*;

import java.util.Iterator;

/**
 * Parses a token stream.
 */
public class Parser {
    private LookaheadIterator<Token> tokenStream;

    /**
     * Creates a parser from a LookaheadIterator.
     * 
     * @param tokenStream LookaheadIterator of tokens.
     */
    public Parser(LookaheadIterator<Token> tokenStream) {
        this.tokenStream = tokenStream;
    }

    /**
     * Creates a parser that reads input tokens from the given lexer.
     * 
     * @param lexer The lexer.
     */
    public Parser(Lexer lexer) {
        Iterator<Token> tokenIterator = new Iterator<Token>() {
            private Lexer innerLexer = lexer;

            @Override
            public boolean hasNext() {
                return true;
            }

            @Override
            public Token next() {
                return innerLexer.getNextToken();
            }
        };
        this.tokenStream = new BufferedLookaheadIterator<>(tokenIterator);
    }

    /**
     * Parses the input.
     * 
     * @throws ParseException if a syntax error is encountered.
     */
    public void parse() {

    }

    private void parsePrimaryExpression() {

    }
}
