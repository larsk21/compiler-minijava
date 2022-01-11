package edu.kit.compiler.semantic;

/**
 * Represents a reference to a definition.
 */
public interface Reference {

    /**
     * Get the definition of this reference.
     */
    Definition getDefinition();

    /**
     * Set the definition of this reference.
     */
    void setDefinition(Definition definition);

}
