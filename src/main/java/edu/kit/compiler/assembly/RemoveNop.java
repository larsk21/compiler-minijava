package edu.kit.compiler.assembly;

import java.util.Optional;

import edu.kit.compiler.assembly.AssemblyOptimizer.AssemblyOptimization;

/**
 * A basic optimization to remove `nop` instructions, potentially introduced
 * by Unknown nodes in the Firm graph.
 */
public class RemoveNop extends AssemblyOptimization {

    public RemoveNop() {
        super(1);
    }

    @Override
    public Optional<String[]> optimize(String[] instructions) {
        if (instructions[0] == "nop") {
            return Optional.of(new String[0]);
        } else {
            return Optional.empty();
        }
    }
}
