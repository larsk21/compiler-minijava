package edu.kit.compiler.lexer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;

import org.junit.jupiter.api.Test;


import static edu.kit.compiler.data.TokenType.*;

import edu.kit.compiler.data.Token;
import edu.kit.compiler.io.ReaderCharIterator;

public class LexerTest {

    private ClassLoader classLoader = getClass().getClassLoader();

    @Test
    public void testEmptyInput() throws LexException {
        var lexer = new Lexer(getIterator(""));
        assertEquals(EndOfStream, lexer.getNextToken().getType());
        assertEquals(EndOfStream, lexer.getNextToken().getType());
    }

    @Test
    public void testWhiteSpace() throws LexException {
        var lexer = new Lexer(getIterator("  42\r\n+\t1"));
        assertEquals(new Token(IntegerLiteral, 1, 3, 42), lexer.getNextToken());
        assertEquals(new Token(Operator_Plus, 2, 1), lexer.getNextToken());
        assertEquals(new Token(IntegerLiteral, 2, 3, 1), lexer.getNextToken());
    }

    @Test
    public void testComment() throws LexException {
        var lexer = new Lexer(getIterator("/* comment */x+=/**/1/* *//* */"));
        assertEquals(new Token(Identifier, 1, 14, 0), lexer.getNextToken());
        assertEquals(new Token(Operator_PlusEqual, 1, 15), lexer.getNextToken());
        assertEquals(new Token(IntegerLiteral, 1, 21, 1), lexer.getNextToken());
        assertEquals(new Token(EndOfStream, 1, 32), lexer.getNextToken());
    }

    @Test
    public void testUnclosedComment() throws LexException {
        var lexer = new Lexer(getIterator("/* open comment "));
        assertThrows(LexException.class, () -> lexer.getNextToken());
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

    @Test
    public void testKeyword() throws LexException {
        var lexer = new Lexer(getIterator("abstract"));
        assertEquals(new Token(Keyword_Abstract, 1, 1), lexer.getNextToken());
    }

    @Test
    public void testBasicIdentifiers() throws LexException {
        var lexer = new Lexer(getIterator("foo"));
        assertEquals(new Token(Identifier, 1, 1, 0), lexer.getNextToken());
        lexer = new Lexer(getIterator("_true"));
        assertEquals(new Token(Identifier, 1, 1, 0), lexer.getNextToken());
        lexer = new Lexer(getIterator("Ab_c1__234a"));
        assertEquals(new Token(Identifier, 1, 1, 0), lexer.getNextToken());
        assertEquals("Ab_c1__234a", lexer.getStringTable().retrieve(0));
    }

    @Test
    public void testWebsiteExample() throws LexException {
        var stream = classLoader.getResourceAsStream("edu/kit/compiler/lexer/example.java");
        var lexer = new Lexer(getIterator(new InputStreamReader(stream)));
        var stringTable = lexer.getStringTable();
        int classic = stringTable.insert("classic");
        int method = stringTable.insert("method");
        int arg = stringTable.insert("arg");
        int res = stringTable.insert("res");

        assertEquals(new Token(Keyword_Class, 5, 1), lexer.getNextToken());
        assertEquals(new Token(Identifier, 5, 7, classic), lexer.getNextToken());
        assertEquals(new Token(Operator_BraceL, 5, 15), lexer.getNextToken());
        assertEquals(new Token(Keyword_Public, 6, 2),  lexer.getNextToken());
        assertEquals(new Token(Keyword_Int, 6, 9), lexer.getNextToken());
        assertEquals(new Token(Identifier, 6, 13, method), lexer.getNextToken());
        assertEquals(new Token(Operator_ParenL, 6, 19), lexer.getNextToken());
        assertEquals(new Token(Keyword_Int, 6, 20), lexer.getNextToken());
        assertEquals(new Token(Identifier, 6, 24, arg), lexer.getNextToken());
        assertEquals(new Token(Operator_ParenR, 6, 27), lexer.getNextToken());
        assertEquals(new Token(Operator_BraceL, 6, 29), lexer.getNextToken());
        assertEquals(new Token(Keyword_Int, 7, 3), lexer.getNextToken());
        assertEquals(new Token(Identifier, 7, 7, res), lexer.getNextToken());
        assertEquals(new Token(Operator_Equal, 7, 11), lexer.getNextToken());
        assertEquals(new Token(Identifier, 7, 13, arg), lexer.getNextToken());
        assertEquals(new Token(Operator_Plus, 7, 16), lexer.getNextToken());
        assertEquals(new Token(IntegerLiteral, 7, 17, 42), lexer.getNextToken());
        assertEquals(new Token(Operator_Semicolon, 7, 19), lexer.getNextToken());
        assertEquals(new Token(Identifier, 8, 3, res), lexer.getNextToken());
        assertEquals(new Token(Operator_GreaterGreaterEqual, 8, 7), lexer.getNextToken());
        assertEquals(new Token(IntegerLiteral, 8, 11, 4), lexer.getNextToken());
        assertEquals(new Token(Operator_Semicolon, 8, 12), lexer.getNextToken());
        assertEquals(new Token(Keyword_Return, 9, 6), lexer.getNextToken());
        assertEquals(new Token(Identifier, 9, 13, res), lexer.getNextToken());
        assertEquals(new Token(Operator_Semicolon, 9, 16), lexer.getNextToken());
        assertEquals(new Token(Operator_BraceR, 10, 2), lexer.getNextToken());
        assertEquals(new Token(Operator_BraceR, 11, 1), lexer.getNextToken());
        assertEquals(new Token(EndOfStream, 12, 1), lexer.getNextToken());
    }

    private static ReaderCharIterator getIterator(String input) {
        return getIterator(new StringReader(input));
    }

    private static ReaderCharIterator getIterator(Reader reader) {
        return new ReaderCharIterator(reader);
    }
}
