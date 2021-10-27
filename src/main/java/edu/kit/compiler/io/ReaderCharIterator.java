package edu.kit.compiler.io;

import java.io.IOException;
import java.io.Reader;
import java.util.Iterator;

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
    private boolean hasEnded = false;

    @Override
    public boolean hasNext() {
        return true;
    }

    @Override
    public Character next() {
        try {
            int i = reader.read();

            while (i == '\r') {
                i = reader.read();
            }
            if (i < 0) {
                i = '\u0000';
                hasEnded = true;
            }

            return (char)i;
        } catch (IOException e) {
            return '\u0000';
        }
    }

    public class EndOfStreamProxy {
        public boolean hasEnded() {
            return ReaderCharIterator.this.hasEnded;
        }
    }
}
