package edu.kit.compiler.optimizations;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import edu.kit.compiler.DebugFlags;
import firm.Dump;
import firm.Entity;
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
     *
     * Returns the set of all living functions.
     */
    public Set<Graph> optimize(Entity main) {
        dumpGraphsIfEnabled("raw");

        var optimizationState = new OptimizationState();
        var changeSet = getAllGraphs();
        CallGraph callGraph;
        boolean hasChanged;

        do {
            callGraph = CallGraph.create();
            hasChanged = optimizeLocal(callGraph, optimizationState, changeSet);
            changeSet.clear();

            // ? maybe only run global opts once per iteration
            Set<Graph> newChanges;
            do {
                newChanges = optimizeGlobal(callGraph);
                changeSet.addAll(newChanges);
            } while (!newChanges.isEmpty());

            callGraph.prune(main);

        } while (hasChanged && !changeSet.isEmpty());

        dumpGraphsIfEnabled("opt");

        return callGraph.vertexSet().stream().map(Entity::getGraph).filter(Objects::nonNull)
                .collect(Collectors.toSet());
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
        for(Graph graph: orderedGraphs) {
            optimizationState.update(callGraph, graph);
        }

        var programChanged = false;
        for (var graph : orderedGraphs) {
            boolean graphChanged = false;
            boolean changes;
            do {
                changes = false;
                for (var optimization : localOptimizations) {
                    changes |= optimization.optimize(graph, optimizationState);
                }
                graphChanged |= changes;
            } while (changes);

            if (graphChanged) {
                callGraph.update(graph);
                optimizationState.update(callGraph, graph);
            }
            programChanged |= graphChanged;
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
