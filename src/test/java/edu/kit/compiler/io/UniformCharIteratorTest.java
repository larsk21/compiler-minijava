package edu.kit.compiler.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

public class UniformCharIteratorTest {

    @Test
    public void hasNextNonEmpty() {
        List<Character> source = Arrays.asList('a', 'b', 'c');
        UniformCharIterator iterator = new UniformCharIterator(source.iterator());

        assertTrue(iterator.hasNext());
    }

    @Test
    public void hasNextEmpty() {
        List<Character> source = Arrays.asList();
        UniformCharIterator iterator = new UniformCharIterator(source.iterator());

        assertTrue(iterator.hasNext());
    }

    @Test
    public void nextFirstAfterInit() {
        List<Character> source = Arrays.asList('a', 'b', 'c');
        UniformCharIterator iterator = new UniformCharIterator(source.iterator());

        assertEquals('a', iterator.next());
    }

    @Test
    public void nextFirstRemaining() {
        List<Character> source = Arrays.asList('a', 'b', 'c');
        UniformCharIterator iterator = new UniformCharIterator(source.iterator());

        iterator.next();

        assertEquals('b', iterator.next());
    }

    @Test
    public void nextFirstSkipSingleCR() {
        List<Character> source = Arrays.asList('\r', 'b', 'c');
        UniformCharIterator iterator = new UniformCharIterator(source.iterator());

        assertEquals('b', iterator.next());
    }

    @Test
    public void nextFirstSkipMultipleCR() {
        List<Character> source = Arrays.asList('\r', '\r', 'c');
        UniformCharIterator iterator = new UniformCharIterator(source.iterator());

        assertEquals('c', iterator.next());
    }

    @Test
    public void nextEmpty() {
        List<Character> source = Arrays.asList();
        UniformCharIterator iterator = new UniformCharIterator(source.iterator());

        assertEquals('\u0000', iterator.next());
    }

    @Test
    public void nextOnlyCR() {
        List<Character> source = Arrays.asList('\r', '\r');
        UniformCharIterator iterator = new UniformCharIterator(source.iterator());

        assertEquals('\u0000', iterator.next());
    }

}
