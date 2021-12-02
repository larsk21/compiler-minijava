package edu.kit.compiler.io;

import java.util.Stack;

/**
 * Implementation of a Worklist using a Stack.
 */
public class StackWorklist<T> implements Worklist<T> {

    /**
     * Create a new StackWorklist.
     */
    public StackWorklist() {
        stack = new Stack<>();
    }

    private Stack<T> stack;

    @Override
    public boolean isEmpty() {
        return stack.isEmpty();
    }

    @Override
    public void enqueue(T element) {
        stack.remove(element);
        stack.add(element);
    }

    @Override
    public void enqueueInOrder(T element) {
        stack.remove(element);
        stack.insertElementAt(element, 0);
    }

    @Override
    public T dequeue() {
        return stack.pop();
    }

}
