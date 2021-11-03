package edu.kit.compiler.parser;

import java.io.Reader;
import java.io.StringReader;

import org.junit.jupiter.api.Test;

import edu.kit.compiler.lexer.Lexer;
import edu.kit.compiler.io.ReaderCharIterator;


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

    private static ReaderCharIterator getIterator(String input) {
        return getIterator(new StringReader(input));
    }

    private static ReaderCharIterator getIterator(Reader reader) {
        return new ReaderCharIterator(reader);
    }
}
