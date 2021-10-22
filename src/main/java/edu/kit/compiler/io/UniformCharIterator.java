package edu.kit.compiler.io;

import java.util.Iterator;

/**
 * Character iterator that returns the characters from the source character
 * iterator and unlimited \u0000 characters afterwards. \r characters are
 * skipped.
 */
public class UniformCharIterator implements Iterator<Character> {

    /**
     * Create a new uniform character Iterator from a character Iterator.
     */
    public UniformCharIterator(Iterator<Character> source) {
        this.source = source;
    }

    private Iterator<Character> source;

    @Override
    public boolean hasNext() {
        return true;
    }

    @Override
    public Character next() {
        if (source.hasNext()) {
            char c = source.next();

            if (c == '\r')
                return next();
            else
                return c;
        } else {
            return '\u0000';
        }
    }

}
