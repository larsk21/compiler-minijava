package edu.kit.compiler.io;

import java.util.ArrayDeque;
import java.util.Deque;

public class DequeWorklist<T> implements Worklist<T> {
    private final Deque<T> deque = new ArrayDeque<>();

    @Override
    public boolean isEmpty() {
        return deque.isEmpty();
    }

    @Override
    public void enqueue(T element) {
        deque.addFirst(element);
    }

    @Override
    public void enqueueInOrder(T element) {
        deque.addLast(element);
    }

    @Override
    public T dequeue() {
        return deque.removeFirst();
    }
    
}
