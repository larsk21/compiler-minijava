package edu.kit.compiler.io;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.Reader;
import java.io.StringReader;

import org.junit.jupiter.api.Test;

public class SourceLocationReaderTest {

    @Test
    public void getLineAfterInit() {
        SourceLocationReader reader = new SourceLocationReader(stringReader(""));
        assertEquals(1, reader.getLine());
    }

    @Test
    public void getColumnAfterInit() {
        SourceLocationReader reader = new SourceLocationReader(stringReader(""));

        assertEquals(1, reader.getColumn());
    }

    @Test
    public void peekSome() {
        SourceLocationReader reader = new SourceLocationReader(stringReader("b"));

        assertEquals('b', reader.peek());
    }

    @Test
    public void peekNewline() {
        SourceLocationReader reader = new SourceLocationReader(stringReader("\n"));

        assertEquals('\n', reader.peek());
    }

    @Test
    public void peekNull() {
        SourceLocationReader reader = new SourceLocationReader(stringReader("\u0000"));

        assertEquals('\u0000', reader.peek());
    }

    @Test
    public void peekEndOfStream() {
        SourceLocationReader reader = new SourceLocationReader(stringReader(""));

        assertEquals(-1, reader.peek());
    }

    @Test
    public void testNext() {
        SourceLocationReader reader = new SourceLocationReader(stringReader("abc"));

        reader.next();
        assertEquals('b', reader.peek());
        reader.next();
        assertEquals('c', reader.peek());
    }

    @Test
    public void testPeekNext() {
        SourceLocationReader reader = new SourceLocationReader(stringReader("abc"));

        assertEquals('b', reader.peekNext());
        assertEquals('c', reader.peekNext());
    }

    @Test
    public void nextIncreaseColumn() {
        SourceLocationReader reader = new SourceLocationReader(stringReader("abc"));

        reader.next();
        assertEquals(2, reader.getColumn());
        reader.next();
        assertEquals(3, reader.getColumn());
    }

    @Test
    public void peekNextIncreaseColumn() {
        SourceLocationReader reader = new SourceLocationReader(stringReader("abc"));

        reader.peekNext();
        assertEquals(2, reader.getColumn());
        reader.peekNext();
        assertEquals(3, reader.getColumn());
    }

    @Test
    public void nextKeepLine() {
        SourceLocationReader reader = new SourceLocationReader(stringReader("abc"));

        reader.next();
        assertEquals(1, reader.getLine());
        reader.next();
        assertEquals(1, reader.getLine());
    }

    @Test
    public void nextResetColumn() {
        SourceLocationReader reader = new SourceLocationReader(stringReader("a\nc"));

        reader.next();
        reader.next();
        assertEquals(1, reader.getColumn());
    }

    @Test
    public void nextIncreaseLine() {
        SourceLocationReader reader = new SourceLocationReader(stringReader("a\nc"));

        reader.next();
        reader.next();
        assertEquals(2, reader.getLine());
    }

    @Test
    public void previewNext() {
        SourceLocationReader reader = new SourceLocationReader(stringReader("/*"));
        assertEquals('*', reader.previewNext());
    }

    @Test
    public void previewEndOfStream() {
        SourceLocationReader reader = new SourceLocationReader(stringReader("/"));
        assertEquals(-1, reader.previewNext());
    }

    @Test
    public void markNotSupported() {
        Reader source = new StringReader("/*") {
            @Override
            public boolean markSupported() {
                return false;
            }
        };
        SourceLocationReader reader = new SourceLocationReader(source);
        assertEquals('*', reader.previewNext());
    }

    private static Reader stringReader(String input) {
        return new StringReader(input);
    }
}
