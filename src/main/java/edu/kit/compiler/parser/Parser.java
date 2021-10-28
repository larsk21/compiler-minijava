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
import java.util.Optional;

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
        while (tokenStream.get().getType() == TokenType.Keyword_Class) {
            tokenStream.next();
            expect(TokenType.Identifier);
            expect(TokenType.Operator_BraceL);
            parseClassMembers();
            expect(TokenType.Operator_BraceR);
        }
        expect(TokenType.EndOfStream);
    }

    private void parseClassMembers() {
        while (tokenStream.get().getType() == TokenType.Keyword_Public) {
            tokenStream.next();
            if (tokenStream.get().getType() == TokenType.Keyword_Static) {
                // MainMethod
                tokenStream.next();
                expect(TokenType.Keyword_Void);
                expect(TokenType.Identifier);
                expect(TokenType.Operator_ParenL);
                parseType();
                expect(TokenType.Identifier);
                expect(TokenType.Operator_ParenR);
                parseMethodRest();
                parseBlock();
            } else {
                parseType();
                expect(TokenType.Identifier);
                if (tokenStream.get().getType() == TokenType.Operator_Semicolon) {
                    // Field
                    tokenStream.next();
                } else {
                    // Method
                    expect(TokenType.Operator_ParenL);
                    if (tokenStream.get().getType()!= TokenType.Operator_ParenR) {
                        parseParameters();
                    }
                    expect(TokenType.Operator_ParenR);
                    parseMethodRest();
                    parseBlock();
                }
            }
        }
    }

    private void parseType() {
        parseBasicType();
        while (tokenStream.get().getType() == TokenType.Operator_BracketL) {
            tokenStream.next();
            expect(TokenType.Operator_BracketR);
        }
    }

    private void parseMethodRest() {
        if (tokenStream.get().getType() == TokenType.Keyword_Throws) {
            tokenStream.next();
            expect(TokenType.Identifier);
        }
    }

    private void parseParameters() {
        parseParameter();
        while (tokenStream.get().getType() == TokenType.Operator_Comma) {
            tokenStream.next();
            parseParameter();
        }
    }

    private void parseParameter() {
        parseType();
        expect(TokenType.Identifier);
    }

    private void parseBlock() {
        expect(TokenType.Operator_BraceL);
        while (tokenStream.get().getType() != TokenType.Operator_BraceR) {
            TokenType type = tokenStream.get().getType();
            if (type == TokenType.Keyword_Int ||
                type == TokenType.Keyword_Boolean ||
                type == TokenType.Keyword_Void ||
                check(TokenType.Identifier, TokenType.Identifier)) {
                // LocalVariableDeclarationStatement
                parseType();
                expect(TokenType.Identifier);
                if (tokenStream.get().getType() == TokenType.Operator_Equal) {
                    tokenStream.next();
                    parseExpression();
                }
                expect(TokenType.Operator_Semicolon);
            } else {
                // Statement
                parseStatement();
            }
        }
        expect(TokenType.Operator_BraceR);
    }

    private void parseStatement() {
        Token token = tokenStream.get();
        switch(token.getType()) {
            case Operator_BraceL: {
                parseBlock();
                break;
            }
            case Operator_Semicolon: {
                // EmptyStatement
                tokenStream.next();
                break;
            }
            case Keyword_If: {
                tokenStream.next();
                expect(TokenType.Operator_ParenL);
                parseExpression();
                expect(TokenType.Operator_ParenR);
                parseStatement();
                if (tokenStream.get().getType() == TokenType.Keyword_Else) {
                    tokenStream.next();
                    parseStatement();
                }
                break;
            }
            case Keyword_While: {
                tokenStream.next();
                expect(TokenType.Operator_ParenL);
                parseExpression();
                expect(TokenType.Operator_ParenR);
                parseStatement();
                break;
            }
            case Keyword_Return: {
                tokenStream.next();
                if (tokenStream.get().getType() != TokenType.Operator_Semicolon) {
                    parseExpression();
                }
                expect(TokenType.Operator_Semicolon);
                break;
            }
            default: {
                // Probably an ExpressionStatement
                parseExpression();
                expect(TokenType.Operator_Semicolon);
            }
        }

    }

    private void parsePrimaryExpression() {
        Token token = tokenStream.get();
        switch(token.getType()) {
            case Keyword_Null: {
                tokenStream.next();
                break;
            }
            case Keyword_False: {
                tokenStream.next();
                break;
            }
            case Keyword_True: {
                tokenStream.next();
                break;
            }
            case IntegerLiteral: {
                tokenStream.next();
                break;
            }
            case Identifier: {
                tokenStream.next();
                if (tokenStream.get().getType() == TokenType.Operator_ParenL) {
                    tokenStream.next();
                    parseArguments();
                    expect(TokenType.Operator_ParenR);
                }
                break;
            }
            case Keyword_This: {
                tokenStream.next();
                break;
            }
            case Operator_ParenL: {
                tokenStream.next();
                parseExpression();
                expect(TokenType.Operator_ParenR);
                break;
            }
            case Keyword_New: {
                parseNewExpression();
                break;
            }
            default: {
                throw new ParseException(token, "expected primary expression");
            }
        }
    }

    private void parseNewExpression() {
        expect(TokenType.Keyword_New);
        if (check(TokenType.Identifier, TokenType.Operator_ParenL)) {
            // NewObjectExpression
            tokenStream.next(2);
            expect(TokenType.Operator_ParenR);
        } else {
            // NewArrayExpression
            parseBasicType();
            expect(TokenType.Operator_BracketL);
            parseExpression();
            expect(TokenType.Operator_BracketR);

            while (tokenStream.get().getType() == TokenType.Operator_BracketL) {
                if (tokenStream.get(1).getType() == TokenType.Operator_BracketR) {
                    tokenStream.next(2);
                } else {
                    // This is not part of the NewArrayExpression, but possibly an ArrayAccess
                    return;
                }
            }
        }
    }

    private void parseBasicType() {
        Token token = tokenStream.get();
        switch(token.getType()) {
            case Identifier: {
                tokenStream.next();
                break;
            }
            case Keyword_Int: {
                tokenStream.next();
                break;
            }
            case Keyword_Boolean: {
                tokenStream.next();
                break;
            }
            case Keyword_Void: {
                tokenStream.next();
                break;
            }
            default: {
                throw new ParseException(token, "expected int, boolean, void or identifier");
            }
        }
        
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

        Optional<OperatorInformation> operator;
        while (
            (operator =
                OperatorInformation
                .getOperatorInformation(tokenStream.get().getType(), Appearence.Infix)
            ).isPresent() &&
            operator.get().getPrecedence() >= minPrecedence
        ) {
            tokenStream.next();

            int precedence = operator.get().getPrecedence();
            Associativity associativity = operator.get().getAssociativity();
            if (associativity == Associativity.Left) {
                precedence++;
            } else if (associativity == Associativity.None) {
                // precedence = OperatorInformation.MAX_PRECEDENCE + 1;
                precedence++;
            }

            parseExpression(precedence);
        }
    }

    private void parseUnaryExpression() {
        switch (tokenStream.get().getType()) {
        case Operator_Not:
            tokenStream.next();

            parseUnaryExpression();
            break;
        case Operator_Minus:
            tokenStream.next();

            parseUnaryExpression();
            break;
        default:
            parsePostfixExpression();
            break;
        }
    }

    private void parsePostfixExpression() {
        parsePrimaryExpression();

        Token token = tokenStream.get(0);
        while (token.getType() == Operator_Dot || token.getType() == Operator_BracketL) {
            switch (token.getType()) {
                case Operator_Dot:
                    parseMethodInvocationOrFieldAccess();
                    break;
                case Operator_BracketL:
                    parseArrayAccess();
                    break;
                default:
                    // never reached, see while condition
                    throw new ParseException(token, "expected dot or array access");
            }
        }
    }

    private void parseMethodInvocationOrFieldAccess() {
        expect(Operator_Dot);
        expect(Identifier);

        if (tokenStream.get(0).getType() == Operator_BraceL) {
            tokenStream.next();

            parseArguments();

            expect(Operator_BraceR);
        } // else field access
    }

    private void parseArrayAccess() {
        expect(Operator_BracketL);

        parseExpression();

        expect(Operator_BracketR);
    }

    private void parseArguments() {
        if (tokenStream.get().getType() == TokenType.Operator_ParenR) {
            // empty arguments
            return;
        }

        parseExpression();
        while (tokenStream.get().getType() == TokenType.Operator_Comma) {
            tokenStream.next();
            parseExpression();
        }
    }

    // helper functions
    private boolean check(TokenType... types) {
        for (int i = 0; i < types.length; i++) {
            if (tokenStream.get(i).getType() != types[i]) {
                return false;
            }
        }

        return true;
    }

    private Token expect(TokenType type) {
        Token token = tokenStream.get();
        if (token.getType() != type) {
            throw new ParseException(tokenStream.get(), "expected " + type.name());
        }

        tokenStream.next();
        return token;
    }

}
