package edu.kit.compiler.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class StackWorklistTest {

    @Test
    public void testIsEmptyInit() {
        StackWorklist<Integer> stack = new StackWorklist<>();

        assertTrue(stack.isEmpty());
    }

    @Test
    public void testIsEmptyNonEmpty() {
        StackWorklist<Integer> stack = new StackWorklist<>();

        stack.enqueue(1);

        assertFalse(stack.isEmpty());
    }

    @Test
    public void testIsEmptyEmpty() {
        StackWorklist<Integer> stack = new StackWorklist<>();

        stack.enqueue(1);
        stack.dequeue();

        assertTrue(stack.isEmpty());
    }

    @Test
    public void testEnqueueDequeue() {
        StackWorklist<Integer> stack = new StackWorklist<>();

        stack.enqueue(1);
        stack.enqueue(2);

        assertEquals(2, stack.dequeue());
        assertEquals(1, stack.dequeue());
    }

    @Test
    public void testEnqueueDequeueInOrder() {
        StackWorklist<Integer> stack = new StackWorklist<>();

        stack.enqueueInOrder(1);
        stack.enqueueInOrder(2);

        assertEquals(1, stack.dequeue());
        assertEquals(2, stack.dequeue());
    }

    @Test
    public void testUniqueElements() {
        StackWorklist<Integer> stack = new StackWorklist<>();

        stack.setUniqueElements(true);

        stack.enqueue(1);
        stack.enqueue(1);

        stack.dequeue();

        assertTrue(stack.isEmpty());
    }

    @Test
    public void testNoUniqueElements() {
        StackWorklist<Integer> stack = new StackWorklist<>();

        stack.setUniqueElements(false);

        stack.enqueue(1);
        stack.enqueue(1);

        stack.dequeue();

        assertFalse(stack.isEmpty());
    }

}
