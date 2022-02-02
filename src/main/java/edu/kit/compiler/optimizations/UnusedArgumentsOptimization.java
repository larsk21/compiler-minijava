package edu.kit.compiler.optimizations;

import edu.kit.compiler.optimizations.analysis.UnusedArgumentsAnalysis;
import firm.Graph;

import java.util.Set;

public class UnusedArgumentsOptimization implements Optimization.Global {
    @Override
    public Set<Graph> optimize(CallGraph callGraph) {
        UnusedArgumentsAnalysis.run(callGraph);
        return Set.of();
    }
}
