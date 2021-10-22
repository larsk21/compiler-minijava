package edu.kit.compiler.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.CharArrayReader;
import java.io.IOException;
import java.util.NoSuchElementException;

import org.junit.jupiter.api.Test;

public class ReaderCharIteratorTest {

    @Test
    public void hasNextNonEmptyReader() {
        CharArrayReader reader = new CharArrayReader("abc".toCharArray());
        ReaderCharIterator iterator = new ReaderCharIterator(reader);

        assertTrue(iterator.hasNext());
    }

    @Test
    public void hasNextEmptyReader() {
        CharArrayReader reader = new CharArrayReader("".toCharArray());
        ReaderCharIterator iterator = new ReaderCharIterator(reader);

        assertFalse(iterator.hasNext());
    }

    @Test
    public void hasNextCharactersLeftReader() throws IOException {
        CharArrayReader reader = new CharArrayReader("abc".toCharArray());
        reader.read();

        ReaderCharIterator iterator = new ReaderCharIterator(reader);

        assertTrue(iterator.hasNext());
    }

    @Test
    public void hasNextNoCharactersLeftReader() throws IOException {
        CharArrayReader reader = new CharArrayReader("abc".toCharArray());
        reader.read(); reader.read(); reader.read();

        ReaderCharIterator iterator = new ReaderCharIterator(reader);

        assertFalse(iterator.hasNext());
    }

    @Test
    public void hasNextCharactersLeft() throws IOException {
        CharArrayReader reader = new CharArrayReader("abc".toCharArray());
        ReaderCharIterator iterator = new ReaderCharIterator(reader);

        iterator.next();

        assertTrue(iterator.hasNext());
    }

    @Test
    public void hasNextNoCharactersLeft() throws IOException {
        CharArrayReader reader = new CharArrayReader("abc".toCharArray());
        ReaderCharIterator iterator = new ReaderCharIterator(reader);

        iterator.next(); iterator.next(); iterator.next();

        assertFalse(iterator.hasNext());
    }

    @Test
    public void nextFirstAfterInit() throws IOException {
        CharArrayReader reader = new CharArrayReader("abc".toCharArray());
        ReaderCharIterator iterator = new ReaderCharIterator(reader);

        assertEquals('a', iterator.next());
    }

    @Test
    public void nextFirstRemaining() throws IOException {
        CharArrayReader reader = new CharArrayReader("abc".toCharArray());
        ReaderCharIterator iterator = new ReaderCharIterator(reader);

        iterator.next(); iterator.next();

        assertEquals('c', iterator.next());
    }

    @Test
    public void nextNoCharactersLeft() throws IOException {
        CharArrayReader reader = new CharArrayReader("abc".toCharArray());
        ReaderCharIterator iterator = new ReaderCharIterator(reader);

        iterator.next(); iterator.next(); iterator.next();

        assertThrows(NoSuchElementException.class, () -> iterator.next());
    }

}
