package edu.kit.compiler.io;

import java.io.IOException;
import java.io.Reader;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Wraps a Reader to act like a character Iterator.
 */
public class ReaderCharIterator implements Iterator<Character> {

    /**
     * Create a new character Iterator from a Reader.
     */
    public ReaderCharIterator(Reader reader) {
        this.reader = reader;
    }

    private Reader reader;

    private char cache = '\u0000';
    private boolean useCache = false;

    private void update() {
        if (!useCache) {
            try {
                int i = reader.read();

                if (i >= 0) {
                    cache = (char)i;
                    useCache = true;
                }
            } catch (IOException e) { }
        }
    }

    @Override
    public boolean hasNext() {
        update();

        return useCache;
    }

    @Override
    public Character next() {
        update();

        if (!useCache) throw new NoSuchElementException();
        useCache = false;

        return cache;
    }

}
