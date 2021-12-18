package edu.kit.compiler.io;

import java.util.ArrayDeque;
import java.util.Deque;

import lombok.Getter;
import lombok.Setter;

/**
 * Implementation of a LIFO Worklist using a Deque.
 */
public class StackWorklist<T> implements Worklist<T> {

    /**
     * Create a new StackWorklist.
     */
    public StackWorklist() {
        this(true);
    }

    public StackWorklist(boolean uniqueElements) {
        stack = new ArrayDeque<>();
        this.uniqueElements = uniqueElements;
    }

    private Deque<T> stack;

    @Getter
    @Setter
    private boolean uniqueElements;

    @Override
    public boolean isEmpty() {
        return stack.isEmpty();
    }

    @Override
    public void enqueue(T element) {
        if (uniqueElements) {
            stack.remove(element);
        }
        stack.push(element);
    }

    @Override
    public void enqueueInOrder(T element) {
        if (uniqueElements) {
            stack.remove(element);
        }
        stack.add(element);
    }

    @Override
    public T dequeue() {
        return stack.pop();
    }

}
