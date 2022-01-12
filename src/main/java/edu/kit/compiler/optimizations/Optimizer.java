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

    /**
     * Run all global and local optimizations in turns until a fix point is
     * reached.
     */
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

    /**
     * Run global optimizations on the program once. The given call graphs is
     * passed to each optimization and updated as needed. Returns the a set
     * containing all graphs that have changed.
     */
    private Set<Graph> optimizeGlobal(CallGraph callGraph) {
        var allChanges = new HashSet<Graph>();
        for (var optimization : globalOptimizations) {
            var newChanges = optimization.optimize(callGraph);
            allChanges.addAll(newChanges);
            newChanges.forEach(callGraph::update);
        }

        return allChanges;
    }

    /**
     * Run local optimizations on the given set of graphs until a fix point is
     * reached. Returns true if a change in any graph has occurred.
     */
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

    /**
     * Returns a set of all graphs in the program.
     */
    private HashSet<Graph> initialChangeSet() {
        return StreamSupport
                .stream(Program.getGraphs().spliterator(), false)
                .collect(Collectors.toCollection(HashSet::new));
    }
}
