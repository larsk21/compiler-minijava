package edu.kit.compiler.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

public class BufferedLookaheadIteratorTest {

    @Test
    public void hasFirst() {
        List<Character> source = Arrays.asList('a', 'b', 'c');
        LookaheadIterator<Character> iterator = new BufferedLookaheadIterator<Character>(source.iterator());

        assertTrue(iterator.has(0));
    }

    @Test
    public void hasNoFirst() {
        List<Character> source = Arrays.asList();
        LookaheadIterator<Character> iterator = new BufferedLookaheadIterator<Character>(source.iterator());

        assertFalse(iterator.has(0));
    }

    @Test
    public void hasSome() {
        List<Character> source = Arrays.asList('a', 'b', 'c');
        LookaheadIterator<Character> iterator = new BufferedLookaheadIterator<Character>(source.iterator());

        assertTrue(iterator.has(2));
    }

    @Test
    public void hasNotSome() {
        List<Character> source = Arrays.asList('a');
        LookaheadIterator<Character> iterator = new BufferedLookaheadIterator<Character>(source.iterator());

        assertFalse(iterator.has(2));
    }

    @Test
    public void hasDoesNotMove() {
        List<Character> source = Arrays.asList('a', 'b', 'c');
        LookaheadIterator<Character> iterator = new BufferedLookaheadIterator<Character>(source.iterator());

        iterator.has(1);

        assertEquals('a', iterator.get(0));
    }

    @Test
    public void getFirst() {
        List<Character> source = Arrays.asList('a', 'b', 'c');
        LookaheadIterator<Character> iterator = new BufferedLookaheadIterator<Character>(source.iterator());

        assertEquals('a', iterator.get(0));
    }

    @Test
    public void getSome() {
        List<Character> source = Arrays.asList('a', 'b', 'c');
        LookaheadIterator<Character> iterator = new BufferedLookaheadIterator<Character>(source.iterator());

        assertEquals('c', iterator.get(2));
    }

    @Test
    public void getAfterHas() {
        List<Character> source = Arrays.asList('a', 'b', 'c');
        LookaheadIterator<Character> iterator = new BufferedLookaheadIterator<Character>(source.iterator());

        iterator.has(2);

        assertEquals('b', iterator.get(1));
    }

    @Test
    public void getDoesNotMove() {
        List<Character> source = Arrays.asList('a', 'b', 'c');
        LookaheadIterator<Character> iterator = new BufferedLookaheadIterator<Character>(source.iterator());

        iterator.get(2);

        assertEquals('a', iterator.get(0));
    }

    @Test
    public void nextMove() {
        List<Character> source = Arrays.asList('a', 'b', 'c');
        LookaheadIterator<Character> iterator = new BufferedLookaheadIterator<Character>(source.iterator());

        iterator.next(1);

        assertEquals('b', iterator.get(0));
    }

    @Test
    public void nextMoveAfterHas() {
        List<Character> source = Arrays.asList('a', 'b', 'c');
        LookaheadIterator<Character> iterator = new BufferedLookaheadIterator<Character>(source.iterator());

        iterator.has(0);
        iterator.next(1);

        assertEquals('b', iterator.get(0));
    }

    @Test
    public void nextMoveAfterGet() {
        List<Character> source = Arrays.asList('a', 'b', 'c');
        LookaheadIterator<Character> iterator = new BufferedLookaheadIterator<Character>(source.iterator());

        iterator.get(1);
        iterator.next(1);

        assertEquals('b', iterator.get(0));
    }

    @Test
    public void nextMoveMultipleSteps() {
        List<Character> source = Arrays.asList('a', 'b', 'c');
        LookaheadIterator<Character> iterator = new BufferedLookaheadIterator<Character>(source.iterator());

        iterator.next(2);

        assertEquals('c', iterator.get(0));
    }

    @Test
    public void nextMoveMultipleCalls() {
        List<Character> source = Arrays.asList('a', 'b', 'c');
        LookaheadIterator<Character> iterator = new BufferedLookaheadIterator<Character>(source.iterator());

        iterator.next(1); iterator.next(1);

        assertEquals('c', iterator.get(0));
    }

}
