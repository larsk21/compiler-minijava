package edu.kit.compiler.optimizations;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import firm.Graph;
import firm.Program;

public final class Optimizer {

    private final List<Optimization.Global> globalOptimizations;
    private final List<Optimization.Local> localOptimizations;

    public Optimizer() {
        this.localOptimizations = Collections.emptyList();
        this.globalOptimizations = Collections.emptyList();
    }

    public Optimizer(List<Optimization.Global> globalOptimizations,
            List<Optimization.Local> localOptimizations) {
        this.globalOptimizations = List.copyOf(globalOptimizations);
        this.localOptimizations = List.copyOf(localOptimizations);
    }

    public void optimize() {
        var changeSet = initialChangeSet();
        boolean hasChanged;

        do {
            var callGraph = CallGraph.create();
            Set<Graph> newChanges;
            do {
                newChanges = optimizeGlobal(callGraph);
                changeSet.addAll(newChanges);
            } while (!newChanges.isEmpty());

            hasChanged = optimizeLocal(changeSet);
            changeSet.clear();

        } while (hasChanged);
    }

    private Set<Graph> optimizeGlobal(CallGraph callGraph) {
        var allChanges = new HashSet<Graph>();
        for (var optimization : globalOptimizations) {
            var newChanges = optimization.optimize(callGraph);
            allChanges.addAll(newChanges);
            newChanges.forEach(callGraph::update);
        }

        return allChanges;
    }

    private boolean optimizeLocal(Set<Graph> graphs) {
        var hasChanged = false;
        for (var graph : graphs) {
            boolean newChanges;
            do {
                newChanges = false;
                for (var optimization : localOptimizations) {
                    newChanges |= optimization.optimize(graph);
                }
                hasChanged |= newChanges;
            } while (newChanges);
        }

        return hasChanged;
    }

    private HashSet<Graph> initialChangeSet() {
        return StreamSupport
                .stream(Program.getGraphs().spliterator(), false)
                .collect(Collectors.toCollection(HashSet::new));
    }
}
