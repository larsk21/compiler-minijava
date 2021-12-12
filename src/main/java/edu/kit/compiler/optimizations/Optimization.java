package edu.kit.compiler.optimizations;

import firm.Graph;

/**
 * Represents an optimization on a Firm graph.
 */
public interface Optimization {

    /**
     * Optimize the given graph and return true iff there were changes made to
     * the graph.
     * 
     * Any global option that is changed in this method has to be reset
     * to its previous state before returning.
     */
    boolean optimize(Graph graph);

}
