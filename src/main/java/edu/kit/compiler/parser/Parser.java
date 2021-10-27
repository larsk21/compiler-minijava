package edu.kit.compiler.parser;

import edu.kit.compiler.data.Token;
import edu.kit.compiler.data.TokenType;
import edu.kit.compiler.io.BufferedLookaheadIterator;
import edu.kit.compiler.io.LookaheadIterator;
import edu.kit.compiler.lexer.Lexer;
import edu.kit.compiler.parser.OperatorInformation.Appearence;
import edu.kit.compiler.parser.OperatorInformation.Associativity;

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

    private void parseUnaryExpression() {

    }

    private void parsePostfixExpression() {

    }

    private void parseExpression() throws ParseException {
        parseExpression(OperatorInformation.MIN_PRECEDENCE);
    }
    private void parseExpression(int minPrecedence) throws ParseException {
        if (OperatorInformation.getOperatorInformation(tokenStream.get().getType(), Appearence.Prefix).isPresent()) {
            parseUnaryExpression();
            return;
        }

        parsePostfixExpression();

        OperatorInformation operator;
        while (
            (operator =
                OperatorInformation
                .getOperatorInformation(tokenStream.get().getType(), Appearence.Infix)
                .orElseThrow(() -> new ParseException(tokenStream.get(), "expected operator"))
            ).getPrecedence() >= minPrecedence
        ) {
            tokenStream.next();

            int precedence = operator.getPrecedence();
            Associativity associativity = operator.getAssociativity();
            if (associativity == Associativity.Left) {
                precedence++;
            } else if (associativity == Associativity.None) {
                // precedence = OperatorInformation.MAX_PRECEDENCE + 1;
                precedence++;
            }

            parseExpression(precedence);
        }
    }

}
