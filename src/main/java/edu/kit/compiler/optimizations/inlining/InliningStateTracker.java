package edu.kit.compiler.optimizations.inlining;

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
    private final HashMap<Entity, CalleeEntry> CallerEntry = new HashMap<>();

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
