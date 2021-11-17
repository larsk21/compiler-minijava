package edu.kit.compiler.io;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;

import edu.kit.compiler.data.FileInputException;
import lombok.Getter;

/**
 * Wrapper for a Reader that counts the position (line, column) of the current character.
 */
public final class SourceLocationReader {

    private final Reader source;
    private int buffer;

    @Getter private int line = 1;
    @Getter private int column = 1;

    /**
     * Creates a new SourceLocationReader using the given Reader as source.
     * 
     * @param source the reader to use as source of characters.
     * @throws FileInputException if an I/O error occurs in the underlying reader.
     */
    public SourceLocationReader(Reader source) {
        // We need mark support for `previewNext`, therefore wrap source if necessary
        if (!source.markSupported()) {
            this.source = new BufferedReader(source);
        } else {
            this.source = source;
        }

        try  {
            buffer = source.read();
        } catch (IOException e) {
            throw new FileInputException(e);
        } 
    }

    /**
     * Returns the current character without advancing the reader.
     * 
     * @return the next character returned by the reader.
     */
    public int peek() {
        return buffer;
    }

    /**
     * Advances the reader by one character and returns the next character.
     * @return the character returned by the reader.
     */
    public int getNext() {
        next();
        return buffer;
    }

    /**
     * Advances the reader by one character.
     * 
     * @throws FileInputException if an I/O error occurs in the underlying reader.
     */
    public void next() {
        try {
            updatePosition(buffer);
            buffer = source.read();
        } catch (IOException e) {
            throw new FileInputException(e);
        }
    }

    /**
     * Returns the next character the reader will return. 
     * 
     * @return the next character returned by the reader.
     * @throws FileInputException if an I/O error occurs in the underlying reader.
     */
    public int previewNext() {
        assert source.markSupported();
        try {
            source.mark(1);
            int next = source.read();
            source.reset();
            return next;
        } catch (IOException e) {
            throw new FileInputException(e);
        }
    }

    private void updatePosition(int c) {
        if (c < 0) {
            // do nothing
        } else if (c == '\n') {
            line += 1;
            column = 1;
        } else {
            column += 1;
        }
    }

}
