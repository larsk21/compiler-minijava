package edu.kit.compiler.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

public class CharCounterLookaheadIteratorTest {

    @Test
    public void getLineAfterInit() {
        LookaheadIterator<Character> source = new LookaheadIterator<Character>() {
            @Override
            public boolean has(int pos) { throw new RuntimeException(); }
            @Override
            public Character get(int pos) { throw new RuntimeException(); }
            @Override
            public void next(int steps) { throw new RuntimeException(); }
        };
        CharCounterLookaheadIterator iterator = new CharCounterLookaheadIterator(source);

        assertEquals(1, iterator.getLine());
    }

    @Test
    public void getColumnAfterInit() {
        LookaheadIterator<Character> source = new LookaheadIterator<Character>() {
            @Override
            public boolean has(int pos) { throw new RuntimeException(); }
            @Override
            public Character get(int pos) { throw new RuntimeException(); }
            @Override
            public void next(int steps) { throw new RuntimeException(); }
        };
        CharCounterLookaheadIterator iterator = new CharCounterLookaheadIterator(source);

        assertEquals(1, iterator.getColumn());
    }

    @Test
    public void hasTrue() {
        LookaheadIterator<Character> source = new LookaheadIterator<Character>() {
            @Override
            public boolean has(int pos) { return true; }
            @Override
            public Character get(int pos) { throw new RuntimeException(); }
            @Override
            public void next(int steps) { throw new RuntimeException(); }
        };
        CharCounterLookaheadIterator iterator = new CharCounterLookaheadIterator(source);

        assertTrue(iterator.has(0));
    }

    @Test
    public void hasFalse() {
        LookaheadIterator<Character> source = new LookaheadIterator<Character>() {
            @Override
            public boolean has(int pos) { return false; }
            @Override
            public Character get(int pos) { throw new RuntimeException(); }
            @Override
            public void next(int steps) { throw new RuntimeException(); }
        };
        CharCounterLookaheadIterator iterator = new CharCounterLookaheadIterator(source);

        assertFalse(iterator.has(0));
    }

    @Test
    public void getSome() {
        LookaheadIterator<Character> source = new LookaheadIterator<Character>() {
            @Override
            public boolean has(int pos) { throw new RuntimeException(); }
            @Override
            public Character get(int pos) { return 'b'; }
            @Override
            public void next(int steps) { throw new RuntimeException(); }
        };
        CharCounterLookaheadIterator iterator = new CharCounterLookaheadIterator(source);

        assertEquals('b', iterator.get(0));
    }

    @Test
    public void getSomeNewline() {
        LookaheadIterator<Character> source = new LookaheadIterator<Character>() {
            @Override
            public boolean has(int pos) { throw new RuntimeException(); }
            @Override
            public Character get(int pos) { return '\n'; }
            @Override
            public void next(int steps) { throw new RuntimeException(); }
        };
        CharCounterLookaheadIterator iterator = new CharCounterLookaheadIterator(source);

        assertEquals('\n', iterator.get(0));
    }

    @Test
    public void nextMove() {
        LookaheadIterator<Character> source = new BufferedLookaheadIterator<>(Arrays.asList('a', 'b', 'c').iterator());
        CharCounterLookaheadIterator iterator = new CharCounterLookaheadIterator(source);

        iterator.next(1);

        assertEquals('b', iterator.get(0));
    }

    @Test
    public void nextIncreaseColumn() {
        LookaheadIterator<Character> source = new BufferedLookaheadIterator<>(Arrays.asList('a', 'b', 'c').iterator());
        CharCounterLookaheadIterator iterator = new CharCounterLookaheadIterator(source);

        iterator.next(2);

        assertEquals(3, iterator.getColumn());
    }

    @Test
    public void nextKeepLine() {
        LookaheadIterator<Character> source = new BufferedLookaheadIterator<>(Arrays.asList('a', 'b', 'c').iterator());
        CharCounterLookaheadIterator iterator = new CharCounterLookaheadIterator(source);

        iterator.next(2);

        assertEquals(1, iterator.getLine());
    }

    @Test
    public void nextResetColumn() {
        LookaheadIterator<Character> source = new BufferedLookaheadIterator<>(Arrays.asList('a', '\n', 'c').iterator());
        CharCounterLookaheadIterator iterator = new CharCounterLookaheadIterator(source);

        iterator.next(2);

        assertEquals(1, iterator.getColumn());
    }

    @Test
    public void nextIncreaseLine() {
        LookaheadIterator<Character> source = new BufferedLookaheadIterator<>(Arrays.asList('a', '\n', 'c').iterator());
        CharCounterLookaheadIterator iterator = new CharCounterLookaheadIterator(source);

        iterator.next(2);

        assertEquals(2, iterator.getLine());
    }

    @Test
    public void nextIncreaseLineAndColumn() {
        LookaheadIterator<Character> source = new BufferedLookaheadIterator<>(Arrays.asList('a', 'b', 'c', '\n', 'd', 'e', 'f').iterator());
        CharCounterLookaheadIterator iterator = new CharCounterLookaheadIterator(source);

        iterator.next(6);

        assertEquals('f', iterator.get(0));
        assertEquals(2, iterator.getLine());
        assertEquals(3, iterator.getColumn());
    }

}
