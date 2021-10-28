package edu.kit.compiler.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

public class CharCounterLookaheadIteratorTest {

    @Test
    public void getLineAfterInit() {
        LookaheadIterator<Integer> source = new LookaheadIterator<Integer>() {
            @Override
            public boolean has(int pos) { throw new RuntimeException(); }
            @Override
            public Integer get(int pos) { throw new RuntimeException(); }
            @Override
            public void next(int steps) { throw new RuntimeException(); }
        };
        CharCounterLookaheadIterator iterator = new CharCounterLookaheadIterator(source);

        assertEquals(1, iterator.getLine());
    }

    @Test
    public void getColumnAfterInit() {
        LookaheadIterator<Integer> source = new LookaheadIterator<Integer>() {
            @Override
            public boolean has(int pos) { throw new RuntimeException(); }
            @Override
            public Integer get(int pos) { throw new RuntimeException(); }
            @Override
            public void next(int steps) { throw new RuntimeException(); }
        };
        CharCounterLookaheadIterator iterator = new CharCounterLookaheadIterator(source);

        assertEquals(1, iterator.getColumn());
    }

    @Test
    public void hasTrue() {
        LookaheadIterator<Integer> source = new LookaheadIterator<Integer>() {
            @Override
            public boolean has(int pos) { return true; }
            @Override
            public Integer get(int pos) { throw new RuntimeException(); }
            @Override
            public void next(int steps) { throw new RuntimeException(); }
        };
        CharCounterLookaheadIterator iterator = new CharCounterLookaheadIterator(source);

        assertTrue(iterator.has(0));
    }

    @Test
    public void hasFalse() {
        LookaheadIterator<Integer> source = new LookaheadIterator<Integer>() {
            @Override
            public boolean has(int pos) { return false; }
            @Override
            public Integer get(int pos) { throw new RuntimeException(); }
            @Override
            public void next(int steps) { throw new RuntimeException(); }
        };
        CharCounterLookaheadIterator iterator = new CharCounterLookaheadIterator(source);

        assertFalse(iterator.has(0));
    }

    @Test
    public void getSome() {
        LookaheadIterator<Integer> source = new LookaheadIterator<Integer>() {
            @Override
            public boolean has(int pos) { throw new RuntimeException(); }
            @Override
            public Integer get(int pos) { return (int)'b'; }
            @Override
            public void next(int steps) { throw new RuntimeException(); }
        };
        CharCounterLookaheadIterator iterator = new CharCounterLookaheadIterator(source);

        assertEquals('b', iterator.get(0));
    }

    @Test
    public void getSomeNewline() {
        LookaheadIterator<Integer> source = new LookaheadIterator<Integer>() {
            @Override
            public boolean has(int pos) { throw new RuntimeException(); }
            @Override
            public Integer get(int pos) { return (int)'\n'; }
            @Override
            public void next(int steps) { throw new RuntimeException(); }
        };
        CharCounterLookaheadIterator iterator = new CharCounterLookaheadIterator(source);

        assertEquals('\n', iterator.get(0));
    }

    @Test
    public void getSomeNull() {
        LookaheadIterator<Integer> source = new LookaheadIterator<Integer>() {
            @Override
            public boolean has(int pos) { throw new RuntimeException(); }
            @Override
            public Integer get(int pos) { return (int)'\u0000'; }
            @Override
            public void next(int steps) { throw new RuntimeException(); }
        };
        CharCounterLookaheadIterator iterator = new CharCounterLookaheadIterator(source);

        assertEquals('\u0000', iterator.get(0));
    }

    @Test
    public void getSomeNegative() {
        LookaheadIterator<Integer> source = new LookaheadIterator<Integer>() {
            @Override
            public boolean has(int pos) { throw new RuntimeException(); }
            @Override
            public Integer get(int pos) { return -1; }
            @Override
            public void next(int steps) { throw new RuntimeException(); }
        };
        CharCounterLookaheadIterator iterator = new CharCounterLookaheadIterator(source);

        assertEquals(-1, iterator.get(0));
    }

    @Test
    public void nextMove() {
        LookaheadIterator<Integer> source = new BufferedLookaheadIterator<>(Arrays.asList((int)'a', (int)'b', (int)'c').iterator());
        CharCounterLookaheadIterator iterator = new CharCounterLookaheadIterator(source);

        iterator.next(1);

        assertEquals('b', iterator.get(0));
    }

    @Test
    public void nextIncreaseColumn() {
        LookaheadIterator<Integer> source = new BufferedLookaheadIterator<>(Arrays.asList((int)'a', (int)'b', (int)'c').iterator());
        CharCounterLookaheadIterator iterator = new CharCounterLookaheadIterator(source);

        iterator.next(2);

        assertEquals(3, iterator.getColumn());
    }

    @Test
    public void nextKeepLine() {
        LookaheadIterator<Integer> source = new BufferedLookaheadIterator<>(Arrays.asList((int)'a', (int)'b', (int)'c').iterator());
        CharCounterLookaheadIterator iterator = new CharCounterLookaheadIterator(source);

        iterator.next(2);

        assertEquals(1, iterator.getLine());
    }

    @Test
    public void nextResetColumn() {
        LookaheadIterator<Integer> source = new BufferedLookaheadIterator<>(Arrays.asList((int)'a', (int)'\n', (int)'c').iterator());
        CharCounterLookaheadIterator iterator = new CharCounterLookaheadIterator(source);

        iterator.next(2);

        assertEquals(1, iterator.getColumn());
    }

    @Test
    public void nextIncreaseLine() {
        LookaheadIterator<Integer> source = new BufferedLookaheadIterator<>(Arrays.asList((int)'a', (int)'\n', (int)'c').iterator());
        CharCounterLookaheadIterator iterator = new CharCounterLookaheadIterator(source);

        iterator.next(2);

        assertEquals(2, iterator.getLine());
    }

    @Test
    public void nextIncreaseLineAndColumn() {
        LookaheadIterator<Integer> source = new BufferedLookaheadIterator<>(Arrays.asList((int)'a', (int)'b', (int)'c', (int)'\n', (int)'d', (int)'e', (int)'f').iterator());
        CharCounterLookaheadIterator iterator = new CharCounterLookaheadIterator(source);

        iterator.next(6);

        assertEquals('f', iterator.get(0));
        assertEquals(2, iterator.getLine());
        assertEquals(3, iterator.getColumn());
    }

}
