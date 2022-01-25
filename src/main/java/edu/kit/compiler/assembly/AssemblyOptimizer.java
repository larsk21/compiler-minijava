package edu.kit.compiler.assembly;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import lombok.RequiredArgsConstructor;

/**
 * Represents a collection of assembly optimization that can be collectively
 * applied to a list of instructions.
 */
@RequiredArgsConstructor
public final class AssemblyOptimizer {

    private final Iterable<AssemblyOptimization> optimizations;

    public List<String> apply(List<String> instructions) {
        var result = new ArrayList<String>(instructions.size());
        var optimized = getOptimizedInstructions(instructions);
        optimized.forEachRemaining(result::add);

        return result;
    }

    private Iterator<String> getOptimizedInstructions(List<String> instructions) {
        var iterator = instructions.iterator();
        for (var optimization : optimizations) {
            iterator = optimization.optimize(iterator);
        }

        return iterator;
    }

    /**
     * Represents an optimization that can be applied to assembly instructions.
     */
    public interface AssemblyOptimization {
        Iterator<String> optimize(Iterator<String> instructions);
    }
}
