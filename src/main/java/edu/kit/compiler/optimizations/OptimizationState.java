package edu.kit.compiler.optimizations;

import edu.kit.compiler.optimizations.attributes.AttributeAnalysis;
import edu.kit.compiler.optimizations.inlining.InliningStateTracker;
import firm.Graph;
import lombok.Getter;

/**
 * Class that tracks common state for local optimizations.
 * Every local optimization pass gets access to it.
 *
 * For example, a local optimization can use the optimization state to track
 * its total number of passes for a specific function.
 */
public class OptimizationState {

    @Getter
    private final InliningStateTracker inlineStateTracker = new InliningStateTracker();

    @Getter
    private final AttributeAnalysis attributeAnalysis = new AttributeAnalysis();

    public OptimizationState() {
        attributeAnalysis.apply();
    }

    /**
     * Should be called each time a round of local optimizations for a specific
     * function has finished. Updates the state accordingly.
     */
    public void update(CallGraph callGraph, Graph updated) {
        inlineStateTracker.updateFunction(callGraph, updated.getEntity());
        attributeAnalysis.update(updated);
    }
}
