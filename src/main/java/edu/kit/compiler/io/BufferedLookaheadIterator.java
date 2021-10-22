package edu.kit.compiler.io;

import java.util.Iterator;
import java.util.LinkedList;

/**
 * Implementation of the LookaheadIterator interface using a buffer.
 */
public class BufferedLookaheadIterator<T> implements LookaheadIterator<T> {

    /**
     * Create a buffered LookaheadIterator from an Iterator.
     */
    public BufferedLookaheadIterator(Iterator<T> source) {
        this.source = source;
    }

    private Iterator<T> source;

    private LinkedList<T> buffer = new LinkedList<T>();

    private void fill(int count) {
        while (buffer.size() <= count && source.hasNext()) {
            buffer.add(source.next());
        }
    }

    @Override
    public boolean has(int pos) {
        fill(pos);

        return buffer.size() > pos;
    }

    @Override
    public T get(int pos) {
        fill(pos);

        return buffer.get(pos);
    }

    @Override
    public void next(int steps) {
        int remaining = steps;

        while (remaining > 0 && buffer.size() > 0) {
            buffer.removeFirst();
            remaining--;
        }
        while (remaining > 0) {
            source.next();
            remaining--;
        }
    }

}
