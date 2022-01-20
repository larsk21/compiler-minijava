package edu.kit.compiler.optimizations;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import edu.kit.compiler.DebugFlags;
import firm.Dump;
import firm.Graph;
import firm.Program;

public final class Optimizer {

    private final List<Optimization.Global> globalOptimizations;
    private final List<Optimization.Local> localOptimizations;
    private final DebugFlags debugFlags;

    public Optimizer(List<Optimization.Global> globalOptimizations,
            List<Optimization.Local> localOptimizations, DebugFlags debugFlags) {
        this.globalOptimizations = List.copyOf(globalOptimizations);
        this.localOptimizations = List.copyOf(localOptimizations);
        this.debugFlags = debugFlags;
    }

    /**
     * Run all global and local optimizations in turns until a fix point is
     * reached.
     */
    public void optimize() {
        dumpGraphsIfEnabled("raw");

        var optimizationState = new OptimizationState();
        var changeSet = getAllGraphs();
        boolean hasChanged;

        do {
            var callGraph = CallGraph.create();
            hasChanged = optimizeLocal(callGraph, optimizationState, changeSet);
            changeSet.clear();

            // ? maybe only run global opts once per iteration
            Set<Graph> newChanges;
            do {
                newChanges = optimizeGlobal(callGraph);
                changeSet.addAll(newChanges);
            } while (!newChanges.isEmpty());

        } while (hasChanged && !changeSet.isEmpty());

        dumpGraphsIfEnabled("opt");
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
     * reached. Graphs are iterated in bottom up order as defined by the given
     * call graph. The call graph is updated if a graph has been changed.
     * Returns true if a change in any graph has occurred.
     */
    private boolean optimizeLocal(CallGraph callGraph, OptimizationState optimizationState, Set<Graph> graphs) {
        var orderedGraphs = getChangeSet(callGraph, graphs);

        var programChanged = false;
        for (var graph : orderedGraphs) {
            boolean graphChanged;
            do {
                graphChanged = false;
                for (var optimization : localOptimizations) {
                    graphChanged |= optimization.optimize(graph, optimizationState);
                }
                programChanged |= graphChanged;
            } while (graphChanged);

            if (graphChanged) {
                callGraph.update(graph);
                optimizationState.update(callGraph, graph);
            }
        }

        return programChanged;
    }

    /**
     * Expands the given set of function to include all (in-)direct callers,
     * sorts all functions in bottom-up order and returns the result.
     */
    private Collection<Graph> getChangeSet(CallGraph cg, Collection<Graph> graphs) {
        var callers = new ArrayList<Graph>();
        cg.getTransitiveCallers(() -> graphs.stream().map(Graph::getEntity).iterator())
                .forEachRemaining(caller -> {
                    var graph = caller.getGraph();
                    if (graph != null) {
                        callers.add(graph);
                    }
                });

        var orderedGraphs = new LinkedHashSet<Graph>(cg.getNumFunctions());
        cg.walkGraphsBottomUp(orderedGraphs::add);
        orderedGraphs.retainAll(callers);

        return orderedGraphs;
    }

    /**
     * Returns a set of all graphs in the program.
     */
    private HashSet<Graph> getAllGraphs() {
        return StreamSupport
                .stream(Program.getGraphs().spliterator(), false)
                .collect(Collectors.toCollection(HashSet::new));
    }

    private void dumpGraphsIfEnabled(String prefix) {
        if (debugFlags.isDumpGraphs()) {
            for (var graph : Program.getGraphs()) {
                Dump.dumpGraph(graph, prefix);
            }
        }
    }
}
