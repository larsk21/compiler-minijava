package edu.kit.compiler.optimizations.inlining;

import edu.kit.compiler.optimizations.CallGraph;
import firm.Entity;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.HashMap;
import java.util.Optional;

public class InliningStateTracker {
    /**
     * Heuristic value for constant overhead of calls (number of firm nodes).
     */
    public static final int CALL_OVERHEAD = 20;

    /**
     * Some heuristic values for per-function size increase limits (number of firm nodes).
     */
    public static final int ACCEPTABLE_SIZE_INCREASE = 300;
    public static final int UNPROBLEMATIC_SIZE_INCREASE = 80;
    public static final double ACCEPTABLE_INCREASE_FACTOR = 2;

    /**
     * We want to ensure that inlining always terminates.
     */
    public static final int UPPER_LIMIT_NUM_PASSES = 20;

    private final HashMap<Entity, CalleeEntry> calleeMap = new HashMap<>();
    private final HashMap<Entity, CallerEntry> callerMap = new HashMap<>();

    public CallerEntry getCallerEntry(Entity caller) {
        return callerMap.computeIfAbsent(caller, fun -> {
            // get number of nodes
            CalleeAnalysis ca = CalleeAnalysis.run(fun.getGraph());
            return new CallerEntry(ca.getNumNodes());
        });
    }

    public Optional<CalleeEntry> getCalleeEntry(Entity callee) {
        return Optional.ofNullable(calleeMap.get(callee));
    }

    public void updateFunction(CallGraph callGraph, Entity updated) {
        CalleeAnalysis ca = CalleeAnalysis.run(updated.getGraph());
        calleeMap.put(updated, CalleeEntry.fromCalleeAnalysis(callGraph, ca, updated));
        callGraph.getCallees(updated).filter(calleeMap::containsKey).forEach(
                callee -> calleeMap.put(callee, calleeMap.get(callee).update(callGraph, callee))
        );
    }

    @RequiredArgsConstructor
    public static class CalleeEntry {
        private final CalleeAnalysis ca;
        @Getter
        private final int totalCallSites;
        @Getter
        private final boolean alwaysInline;
        @Getter
        private final boolean recursive;

        public int getNumNodes() {
            return ca.getNumNodes();
        }

        public int getNumUsedArgs() {
            return ca.getNumUsedArgs();
        }

        public static CalleeEntry fromCalleeAnalysis(CallGraph callGraph, CalleeAnalysis ca, Entity self) {
            int totalCallSites = (int) Math.round(callGraph.getCallers(self).mapToDouble(
                    caller -> callGraph.getCallFrequency(caller, self)).sum()
            );
            int acceptableNewSize = ca.getNumNodes() + (int) Math.round(Math.min(
                    ACCEPTABLE_SIZE_INCREASE,
                    (ACCEPTABLE_INCREASE_FACTOR - 1) * ca.getNumNodes() + UNPROBLEMATIC_SIZE_INCREASE
            ));
            int sizeIfFullyInlined = totalCallSites * (ca.getNumNodes() - CALL_OVERHEAD);
            boolean recursive = callGraph.existsRecursion(self);
            boolean alwaysInline = sizeIfFullyInlined <= acceptableNewSize && !recursive;
            return new CalleeEntry(ca, totalCallSites, alwaysInline, recursive);
        }

        /**
         * Returns an updated entry under the assumption that the callee itself did
         * not change, but the call sites possibly did.
         */
        public CalleeEntry update(CallGraph callGraph, Entity self) {
            return fromCalleeAnalysis(callGraph, ca, self);
        }

        @Override
        public String toString() {
            return String.format("[nNodes=%d, nArgs=%d, callSites=%d, alwaysInline=%s]",
                    getNumNodes(), getNumUsedArgs(), totalCallSites, alwaysInline);
        }
    }

    @RequiredArgsConstructor
    public static class CallerEntry {
        @Getter
        private final int initialNumNodes;
        @Getter
        private int addedNodesFromCompleteInlining = 0;
        @Getter
        private int totalInliningPasses = 0;

        public void addCompletelyInlinedNodes(int num) {
            addedNodesFromCompleteInlining += num;
        }

        public int acceptableSize() {
            return (int) Math.round(
                    ACCEPTABLE_INCREASE_FACTOR * (initialNumNodes
                            + Math.min(addedNodesFromCompleteInlining, ACCEPTABLE_SIZE_INCREASE))
                    + UNPROBLEMATIC_SIZE_INCREASE);
        }

        public void addPass() {
            totalInliningPasses += 1;
        }

        public boolean shouldStop() {
            return totalInliningPasses >= UPPER_LIMIT_NUM_PASSES;
        }
    }
}
