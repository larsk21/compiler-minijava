package edu.kit.compiler.io;

import java.util.Stack;

import lombok.Getter;
import lombok.Setter;

/**
 * Implementation of a Worklist using a Stack.
 */
public class StackWorklist<T> implements Worklist<T> {

    /**
     * Create a new StackWorklist.
     */
    public StackWorklist() {
        stack = new Stack<>();
        uniqueElements = true;
    }

    private Stack<T> stack;

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
        stack.add(element);
    }

    @Override
    public void enqueueInOrder(T element) {
        if (uniqueElements) {
            stack.remove(element);
        }
        stack.insertElementAt(element, 0);
    }

    @Override
    public T dequeue() {
        return stack.pop();
    }

}
