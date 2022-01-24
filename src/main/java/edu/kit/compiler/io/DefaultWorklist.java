package edu.kit.compiler.io;

import java.util.ArrayList;
import java.util.List;

public class DefaultWorklist<T> implements Worklist<T>{

    private final List<T> list = new ArrayList<>();

    @Override
    public boolean isUniqueElements() {
        return false;
    }

    @Override
    public boolean isEmpty() {
        return list.isEmpty();
    }

    @Override
    public void enqueue(T element) {
        list.add(element);
    }

    @Override
    public void enqueueInOrder(T element) {
        list.add(element);
    }

    @Override
    public T dequeue() {
        return list.remove(0);
    }
}
