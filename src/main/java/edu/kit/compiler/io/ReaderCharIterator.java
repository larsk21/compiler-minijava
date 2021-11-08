package edu.kit.compiler.io;

import java.io.IOException;
import java.io.Reader;
import java.util.Iterator;

import edu.kit.compiler.data.FileInputException;

/**
 * Wraps a Reader to act like a character Iterator.
 * 
 * This Iterator will always return true for hasNext and instead output -1 for
 * next when the underlying Reader has no more elements.
 */
public class ReaderCharIterator implements Iterator<Integer> {

    /**
     * Create a new character Iterator from a Reader.
     */
    public ReaderCharIterator(Reader reader) {
        this.reader = reader;
    }

    private Reader reader;

    @Override
    public boolean hasNext() {
        return true;
    }

    @Override
    public Integer next() {
        try {
            return reader.read();
        } catch (IOException e) {
            throw new FileInputException(e);
        }
    }

}
