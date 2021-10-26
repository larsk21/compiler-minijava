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
        assertEquals(EndOfStream, lexer.getNextToken().getType());
        assertEquals(EndOfStream, lexer.getNextToken().getType());
    }

    @Test
    public void testDelimiters() throws LexException {
        var lexer = new Lexer(getIterator(",.:;?~()[]{}"));
        assertEquals(Operator_Comma, lexer.getNextToken().getType());
        assertEquals(Operator_Dot, lexer.getNextToken().getType());
        assertEquals(Operator_Colon, lexer.getNextToken().getType());
        assertEquals(Operator_Semicolon, lexer.getNextToken().getType());
        assertEquals(Operator_Questionmark, lexer.getNextToken().getType());
        assertEquals(Operator_Tilde, lexer.getNextToken().getType());
        assertEquals(Operator_ParenL, lexer.getNextToken().getType());
        assertEquals(Operator_ParenR, lexer.getNextToken().getType());
        assertEquals(Operator_BracketL, lexer.getNextToken().getType());
        assertEquals(Operator_BracketR, lexer.getNextToken().getType());
        assertEquals(Operator_BraceL, lexer.getNextToken().getType());
        assertEquals(Operator_BraceR, lexer.getNextToken().getType());
    }

    @Test
    public void testArithmeticOperators() throws LexException {
        var lexer = new Lexer(getIterator("+-*/%"));
        assertEquals(Operator_Plus, lexer.getNextToken().getType());
        assertEquals(Operator_Minus, lexer.getNextToken().getType());
        assertEquals(Operator_Star, lexer.getNextToken().getType());
        assertEquals(Operator_Slash, lexer.getNextToken().getType());
        assertEquals(Operator_Percent, lexer.getNextToken().getType());
    }

    @Test
    public void testLogicOperators() throws LexException {
        var lexer = new Lexer(getIterator("!!===>=<=><&&||"));
        assertEquals(Operator_Not, lexer.getNextToken().getType());
        assertEquals(Operator_NotEqual, lexer.getNextToken().getType());
        assertEquals(Operator_EqualEqual, lexer.getNextToken().getType());
        assertEquals(Operator_GreaterEqual, lexer.getNextToken().getType());
        assertEquals(Operator_SmallerEqual, lexer.getNextToken().getType());
        assertEquals(Operator_Greater, lexer.getNextToken().getType());
        assertEquals(Operator_Smaller, lexer.getNextToken().getType());
        assertEquals(Operator_AndAnd, lexer.getNextToken().getType());
        assertEquals(Operator_BarBar, lexer.getNextToken().getType());
    }

    @Test
    public void testBinaryOperators() throws LexException {
        var lexer = new Lexer(getIterator("&^|~<<>>>>>"));
        assertEquals(Operator_And, lexer.getNextToken().getType());
        assertEquals(Operator_Circum, lexer.getNextToken().getType());
        assertEquals(Operator_Bar, lexer.getNextToken().getType());
        assertEquals(Operator_Tilde, lexer.getNextToken().getType());
        assertEquals(Operator_SmallerSmaller, lexer.getNextToken().getType());
        assertEquals(Operator_GreaterGreaterGreater, lexer.getNextToken().getType());
        assertEquals(Operator_GreaterGreater, lexer.getNextToken().getType());
    }

    @Test
    public void testAssignmentOperators() throws LexException {
        var lexer = new Lexer(getIterator("+=-=*=/=%=&=|=^=<<=>>=>>>="));
        assertEquals(Operator_PlusEqual, lexer.getNextToken().getType());
        assertEquals(Operator_MinusEqual, lexer.getNextToken().getType());
        assertEquals(Operator_StarEqual, lexer.getNextToken().getType());
        assertEquals(Operator_SlashEqual, lexer.getNextToken().getType());
        assertEquals(Operator_PercentEqual, lexer.getNextToken().getType());
        assertEquals(Operator_AndEqual, lexer.getNextToken().getType());
        assertEquals(Operator_BarEqual, lexer.getNextToken().getType());
        assertEquals(Operator_CircumEqual, lexer.getNextToken().getType());
        assertEquals(Operator_SmallerSmallerEqual, lexer.getNextToken().getType());
        assertEquals(Operator_GreaterGreaterEqual, lexer.getNextToken().getType());
        assertEquals(Operator_GreaterGreaterGreaterEqual, lexer.getNextToken().getType());
    }

    @Test
    public void testIncDecOperators() throws LexException {
        var lexer = new Lexer(getIterator("+++---"));
        assertEquals(Operator_PlusPlus, lexer.getNextToken().getType());
        assertEquals(Operator_Plus, lexer.getNextToken().getType());
        assertEquals(Operator_MinusMinus, lexer.getNextToken().getType());
        assertEquals(Operator_Minus, lexer.getNextToken().getType());
    }

    @Test
    public void testIntegerLiteral() throws LexException {
        var lexer = new Lexer(getIterator("42"));
        assertEquals(new Token(IntegerLiteral, 1, 1, 42), lexer.getNextToken());
    }

    @Test
    public void testZeroLiteral() throws LexException {
        var lexer = new Lexer(getIterator("0"));
        assertEquals(new Token(IntegerLiteral, 1, 1, 0), lexer.getNextToken());
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
