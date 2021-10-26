package edu.kit.compiler.lexer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.StringReader;

import org.junit.jupiter.api.Test;


import static edu.kit.compiler.data.TokenType.*;

import edu.kit.compiler.data.Token;
import edu.kit.compiler.io.BufferedLookaheadIterator;
import edu.kit.compiler.io.CharCounterLookaheadIterator;
import edu.kit.compiler.io.ReaderCharIterator;

public class LexerTest {

    @Test
    public void testEmptyInput() throws LexException {
        var lexer = new Lexer(getIterator(""));
        assertEquals(lexer.getNextToken().getType(), EndOfStream);
        assertEquals(lexer.getNextToken().getType(), EndOfStream);
    }

    @Test
    public void testDelimiters() throws LexException {
        var lexer = new Lexer(getIterator(",.:;?~()[]{}"));
        assertEquals(lexer.getNextToken().getType(), Operator_Comma);
        assertEquals(lexer.getNextToken().getType(), Operator_Dot);
        assertEquals(lexer.getNextToken().getType(), Operator_Colon);
        assertEquals(lexer.getNextToken().getType(), Operator_Semicolon);
        assertEquals(lexer.getNextToken().getType(), Operator_Questionmark);
        assertEquals(lexer.getNextToken().getType(), Operator_Tilde);
        assertEquals(lexer.getNextToken().getType(), Operator_ParenL);
        assertEquals(lexer.getNextToken().getType(), Operator_ParenR);
        assertEquals(lexer.getNextToken().getType(), Operator_BracketL);
        assertEquals(lexer.getNextToken().getType(), Operator_BracketR);
        assertEquals(lexer.getNextToken().getType(), Operator_BraceL);
        assertEquals(lexer.getNextToken().getType(), Operator_BraceR);
    }

    @Test
    public void testArithmeticOperators() throws LexException {
        var lexer = new Lexer(getIterator("+-*/%"));
        assertEquals(lexer.getNextToken().getType(), Operator_Plus);
        assertEquals(lexer.getNextToken().getType(), Operator_Minus);
        assertEquals(lexer.getNextToken().getType(), Operator_Star);
        assertEquals(lexer.getNextToken().getType(), Operator_Slash);
        assertEquals(lexer.getNextToken().getType(), Operator_Percent);
    }

    @Test
    public void testLogicOperators() throws LexException {
        var lexer = new Lexer(getIterator("!!===>=<=><&&||"));
        assertEquals(lexer.getNextToken().getType(), Operator_Not);
        assertEquals(lexer.getNextToken().getType(), Operator_NotEqual);
        assertEquals(lexer.getNextToken().getType(), Operator_EqualEqual);
        assertEquals(lexer.getNextToken().getType(), Operator_GreaterEqual);
        assertEquals(lexer.getNextToken().getType(), Operator_SmallerEqual);
        assertEquals(lexer.getNextToken().getType(), Operator_Greater);
        assertEquals(lexer.getNextToken().getType(), Operator_Smaller);
        assertEquals(lexer.getNextToken().getType(), Operator_AndAnd);
        assertEquals(lexer.getNextToken().getType(), Operator_BarBar);
    }

    @Test
    public void testBinaryOperators() throws LexException {
        var lexer = new Lexer(getIterator("&^|~<<>>>>>"));
        assertEquals(lexer.getNextToken().getType(), Operator_And);
        assertEquals(lexer.getNextToken().getType(), Operator_Circum);
        assertEquals(lexer.getNextToken().getType(), Operator_Bar);
        assertEquals(lexer.getNextToken().getType(), Operator_Tilde);
        assertEquals(lexer.getNextToken().getType(), Operator_SmallerSmaller);
        assertEquals(lexer.getNextToken().getType(), Operator_GreaterGreaterGreater);
        assertEquals(lexer.getNextToken().getType(), Operator_GreaterGreater);
    }

    @Test
    public void testAssignmentOperators() throws LexException {
        var lexer = new Lexer(getIterator("+=-=*=/=%=&=|=^=<<=>>=>>>="));
        assertEquals(lexer.getNextToken().getType(), Operator_PlusEqual);
        assertEquals(lexer.getNextToken().getType(), Operator_MinusEqual);
        assertEquals(lexer.getNextToken().getType(), Operator_StarEqual);
        assertEquals(lexer.getNextToken().getType(), Operator_SlashEqual);
        assertEquals(lexer.getNextToken().getType(), Operator_PercentEqual);
        assertEquals(lexer.getNextToken().getType(), Operator_AndEqual);
        assertEquals(lexer.getNextToken().getType(), Operator_BarEqual);
        assertEquals(lexer.getNextToken().getType(), Operator_CircumEqual);
        assertEquals(lexer.getNextToken().getType(), Operator_SmallerSmallerEqual);
        assertEquals(lexer.getNextToken().getType(), Operator_GreaterGreaterEqual);
        assertEquals(lexer.getNextToken().getType(), Operator_GreaterGreaterGreaterEqual);
    }

    @Test
    public void testIncDecOperators() throws LexException {
        var lexer = new Lexer(getIterator("+++---"));
        assertEquals(lexer.getNextToken().getType(), Operator_PlusPlus);
        assertEquals(lexer.getNextToken().getType(), Operator_Plus);
        assertEquals(lexer.getNextToken().getType(), Operator_MinusMinus);
        assertEquals(lexer.getNextToken().getType(), Operator_Minus);
    }

    @Test
    public void testIntegerLiteral() throws LexException {
        var lexer = new Lexer(getIterator("42"));
        assertEquals(lexer.getNextToken(), new Token(IntegerLiteral, 1, 1, 42));
    }

    @Test
    public void testZeroLiteral() throws LexException {
        var lexer = new Lexer(getIterator("0"));
        assertEquals(lexer.getNextToken(), new Token(IntegerLiteral, 1, 1, 0));
    }

    @Test
    public void testIllegalLiteral() throws LexException {
        var lexer = new Lexer(getIterator("01234"));
        assertThrows(LexException.class, () -> lexer.getNextToken());
    }

    @Test
    public void testIntegerOverflow() throws LexException {
        var lexer = new Lexer(getIterator("1234567891234"));
        assertThrows(LexException.class, () -> lexer.getNextToken());
    }

    private static CharCounterLookaheadIterator getIterator(String input) {
        return new CharCounterLookaheadIterator(
            new BufferedLookaheadIterator<>(
                new ReaderCharIterator(new StringReader(input))
            )
        );
    }
}
