package edu.kit.compiler.optimizations;

import java.util.Set;

import firm.Graph;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Optimization {

    /**
     * Represents an optimization on a Firm graph.
     */
    public interface Local {
        /**
         * Optimize the given graph and return true iff there were changes made to
         * the graph.
         * 
         * Any global option that is changed in this method has to be reset
         * to its previous state before returning.
         */
        boolean optimize(Graph graph, OptimizationState state);
    }


    /**
     * Represents an optimization on the entire Firm program.
     */
    public interface Global {
        /**
         * Optimize the program and return the a set containing all graphs that
         * have been changed.
         */
        Set<Graph> optimize(CallGraph callGraph);
    }
}
