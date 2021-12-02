package edu.kit.compiler.optimizations;

import firm.Graph;

/**
 * Represents an optimization on a Firm graph.
 */
public interface Optimization {

    /**
     * Optimize the given graph.
     * 
     * This includes analyzing the given graph, as well as making changes to
     * it. Any global option that is changed in this method has to be reset
     * to its previous state before returning.
     */
    void optimize(Graph graph);

}
