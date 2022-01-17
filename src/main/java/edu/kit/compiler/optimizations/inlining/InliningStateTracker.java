package edu.kit.compiler.optimizations.inlining;

import edu.kit.compiler.optimizations.CallGraph;
import firm.Entity;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.HashMap;

public class InliningStateTracker {
    /**
     * Heuristic value for constant overhead of calls (number of firm nodes).
     */
    public static final int CALL_OVERHEAD = 15;

    /**
     * Some heuristic values for per-function size increase limits (number of firm nodes).
     */
    public static final int ACCEPTABLE_SIZE_INCREASE = 300;
    public static final int UNPROBLEMATIC_SIZE_INCREASE = 60;
    public static final double ACCEPTABLE_INCREASE_FACTOR = 2;

    private final HashMap<Entity, CalleeEntry> calleeMap = new HashMap<>();
    private final HashMap<Entity, CalleeEntry> callerMap = new HashMap<>();

    public void updateForEntity(CallGraph callGraph, Entity updated) {
        CalleeAnalysis ca = CalleeAnalysis.run(updated.getGraph());
        calleeMap.put(updated, CalleeEntry.fromCalleeAnalysis(callGraph, ca, updated));
        callGraph.getCallees(updated).forEach(
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
            boolean alwaysInline = sizeIfFullyInlined <= acceptableNewSize && !callGraph.existsRecursion(self);
            return new CalleeEntry(ca, totalCallSites, alwaysInline);
        }

        /**
         * Returns an updated entry under the assumption that the callee itself did
         * not change, but the call sites possibly did.
         */
        public CalleeEntry update(CallGraph callGraph, Entity self) {
            return fromCalleeAnalysis(callGraph, ca, self);
        }
    }

    @RequiredArgsConstructor
    public static class CallerEntry {
        @Getter
        private final int initialNumNodes;
        @Getter
        private int addedNodesFromCompleteInlining = 0;

        public void addCompletelyInlinedNodes(int num) {
            addedNodesFromCompleteInlining += num;
        }

        public int acceptableSize() {
            return (int) Math.round(ACCEPTABLE_INCREASE_FACTOR * initialNumNodes
                    + addedNodesFromCompleteInlining + UNPROBLEMATIC_SIZE_INCREASE);
        }
    }
}
