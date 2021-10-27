package edu.kit.compiler.parser;

import java.io.Reader;
import java.io.StringReader;

import org.junit.jupiter.api.Test;

import edu.kit.compiler.lexer.Lexer;
import edu.kit.compiler.io.BufferedLookaheadIterator;
import edu.kit.compiler.io.CharCounterLookaheadIterator;
import edu.kit.compiler.io.ReaderCharIterator;

public class ParserTest {
    @Test
    public void testEmptyInput() {
        var parser = new Parser(new Lexer(getIterator("")));
        parser.parse();
    }

    private static CharCounterLookaheadIterator getIterator(String input) {
        return getIterator(new StringReader(input));
    }

    private static CharCounterLookaheadIterator getIterator(Reader reader) {
        return new CharCounterLookaheadIterator(
            new BufferedLookaheadIterator<>(
                new ReaderCharIterator(reader)
            )
        );
    }
}
