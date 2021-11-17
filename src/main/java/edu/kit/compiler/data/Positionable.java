package edu.kit.compiler.data;

/**
 * Anything that can be associated with a location in the source code.
 */
public interface Positionable {
    /**
     * Characteristic line position of this object in the source file.
     */
    int getLine();
    /**
     * Characteristic column position of this node in the source file.
     */
    int getColumn();
}
