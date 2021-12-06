package edu.kit.compiler.io;

/**
 * Represents a worklist of elements of type T.
 * 
 * A worklist accepts elements and provides these elements in *some* order.
 */
public interface Worklist<T> {

    /**
     * Get whether elements that are inserted more than once are only returned
     * once.
     */
    boolean isUniqueElements();

    /**
     * Get whether this worklist is empty.
     */
    boolean isEmpty();

    /**
     * Insert a new element in the worklist.
     */
    void enqueue(T element);

    /**
     * Insert a new element in the worklist. All elements that are inserted
     * using this method are provided in the order of insertion (FIFO). The
     * order of the elements inserted using this method and the elements
     * inserted using other methods depends on the specific implementation.
     */
    void enqueueInOrder(T element);

    /**
     * Get the next element in the worklist.
     */
    T dequeue();

}
