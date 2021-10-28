package edu.kit.compiler.io;

/**
 * Wrapper for a character LookaheadIterator that counts the position (line,
 * column) of the current character.
 */
public class CharCounterLookaheadIterator implements LookaheadIterator<Integer> {

    /**
     * Create a new character counting LookaheadIterator from a
     * LookaheadIterator.
     */
    public CharCounterLookaheadIterator(LookaheadIterator<Integer> source) {
        this.source = source;
    }

    private LookaheadIterator<Integer> source;

    private int line = 1;
    private int column = 1;

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

    /**
     * Get the line position of the current (get(0)) character.
     */
    public int getLine() {
        return line;
    }

    /**
     * Get the column position of the current (get(0)) character.
     */
    public int getColumn() {
        return column;
    }

    @Override
    public boolean has(int pos) {
        return source.has(pos);
    }

    @Override
    public Integer get(int pos) {
        return source.get(pos);
    }

    @Override
    public void next(int steps) {
        for (int i = 0; i < steps; i++) {
            int c = source.get(0);
            source.next(1);

            updatePosition(c);
        }
    }

}
