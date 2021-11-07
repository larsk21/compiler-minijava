package edu.kit.compiler.parser;

import edu.kit.compiler.io.ReaderCharIterator;
import edu.kit.compiler.lexer.Lexer;
import org.junit.jupiter.api.Test;

import java.io.Reader;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertThrows;


public class ParserTest {
    @Test
    public void testEmptyInput() {
        var parser = new Parser(new Lexer(getIterator("")));
        parser.parse();
    }

    @Test
    public void testAttributes() {
        var parser = new Parser(new Lexer(getIterator(
            "class Test {public int x; public ctype[][] z;}"
        )));
        parser.parse();
    }

    @Test
    public void testMethods() {
        var parser = new Parser(new Lexer(getIterator(
            "class Test {"
            + "public void m1() {}"
            + "public void m2(int i, int j) {}"
            + "public static void main(String[] args){} }"
        )));
        parser.parse();
    }

    @Test
    public void testInvalidClass() {
        var parser = new Parser(new Lexer(getIterator(
                "public class Test {}"
        )));
        assertThrows(ParseException.class, (parser)::parse, "not part of language spec");
    }

    @Test
    public void testInvalidIdentifier() {
        var parser = new Parser(new Lexer(getIterator(
                "class int {}"
        )));
        assertThrows(ParseException.class, (parser)::parse, "not part of language spec");
    }

    private static ReaderCharIterator getIterator(String input) {
        return getIterator(new StringReader(input));
    }

    private static ReaderCharIterator getIterator(Reader reader) {
        return new ReaderCharIterator(reader);
    }
}
