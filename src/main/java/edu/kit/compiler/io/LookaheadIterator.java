package edu.kit.compiler.io;

/**
 * Generalization of the Iterator interface with the posibility to look ahead
 * in the iterator without moving it.
 */
public interface LookaheadIterator<T> {

    /**
     * Return true if the lookahead iterator contains an element.
     */
    default boolean has() { return has(0); }

    /**
     * Return true if the lookahead iterator contains an element at the
     * specified relative position.
     */
    boolean has(int pos);

    /**
     * Return the first element.
     */
    default T get() { return get(0); }

    /**
     * Return the element at the specified relative position.
     */
    T get(int pos);

    /**
     * Move the lookahead iterator forward one step.
     */
    default void next() { next(1); }

    /**
     * Move the lookahead iterator forward for the given number of steps.
     */
    void next(int steps);

}
