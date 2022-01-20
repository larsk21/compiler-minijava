package edu.kit.compiler.optimizations.inlining;

import edu.kit.compiler.optimizations.CallGraph;
import edu.kit.compiler.optimizations.Optimization;
import edu.kit.compiler.optimizations.OptimizationState;
import firm.BackEdges;
import firm.Entity;
import firm.Graph;
import firm.bindings.binding_irgopt;
import firm.bindings.binding_irnode;
import firm.nodes.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.*;

public class InliningOptimization implements Optimization.Local {
    private Graph graph;
    private InliningStateTracker stateTracker;

    @Override
    public boolean optimize(Graph graph, OptimizationState state) {
        this.graph = graph;
        this.stateTracker = state.getInlineStateTracker();

        var callerEntry = stateTracker.getCallerEntry(graph.getEntity());
        callerEntry.addPass();
        if (callerEntry.shouldStop()) {
            return false;
        }

        // we transform the nodes in reverse postorder, i.e. we can access the
        // unchanged predecessors of a node when transforming it
        List<Call> alwaysInlineCalls = new ArrayList<>();
        List<PrioritizedCall> maybeInlineCalls = new ArrayList<>();
        collectCalls(alwaysInlineCalls, maybeInlineCalls);

        BackEdges.enable(graph);
        // approximates the current size of the function
        int currentNumNodes = CalleeAnalysis.run(graph).getNumNodes();
        boolean changes = false;
        for (Call call: alwaysInlineCalls) {
            Inliner.inline(graph, call, getEntity(call).getGraph());
            int numNodes = getCalleeEntry(call).get().getNumNodes();
            callerEntry.addCompletelyInlinedNodes(numNodes);
            currentNumNodes += numNodes;
            changes = true;
        }

        for (var pc: maybeInlineCalls) {
            Call call = pc.getCall();
            int numNodes = getCalleeEntry(call).get().getNumNodes();
            if (currentNumNodes + numNodes <= callerEntry.acceptableSize()) {
                Inliner.inline(graph, call, getEntity(call).getGraph());
                currentNumNodes += numNodes;
                changes = true;
            } else {
                break;
            }
        }

        BackEdges.disable(graph);

        binding_irgopt.remove_unreachable_code(graph.ptr);
        binding_irgopt.remove_bads(graph.ptr);

        return changes;
    }

    private void collectCalls(List<Call> alwaysInlineCalls, List<PrioritizedCall> maybeInlineCalls) {
        graph.walk(new NodeVisitor.Default() {
            @Override
            public void visit(Call call) {
                var entry  = getCalleeEntry(call);
                if (entry.isPresent()) {
                    double prio = calculatePriority(call, entry.get());
                    if (entry.get().isAlwaysInline()) {
                        alwaysInlineCalls.add(call);
                    } else if (prio >= 0) {
                        maybeInlineCalls.add(new PrioritizedCall(call, prio));
                    }
                }
            }
        });
        maybeInlineCalls.sort(Comparator.reverseOrder());
    }

    /**
     * High priority is considered first.
     *
     * Returns -1 if the call shouldn't be inlined at all.
     */
    private static double calculatePriority(Call call, InliningStateTracker.CalleeEntry entry) {
        int numConstArgs = 0;
        for (int i = 2; i < call.getPredCount(); i++) {
            if (call.getPred(i).getOpCode() == binding_irnode.ir_opcode.iro_Const) {
                numConstArgs++;
            }
        }
        int logWeight = argWeighting(call.getPredCount() - 2, entry.getNumUsedArgs(), numConstArgs);
        if (entry.isRecursive()) {
            // inlining of recursive functions is usually a bad idea
            logWeight -= 3;
        }
        boolean doInline = (logWeight >= 3) || (
                (Math.pow(2, logWeight) * InliningStateTracker.UNPROBLEMATIC_SIZE_INCREASE / 2) >= entry.getNumNodes()
                        && logWeight >= 0
        );
        if (doInline) {
            double basePrio = Math.pow(2, logWeight) / entry.getNumNodes();
            return basePrio;
        }
        return -1;
    }

    private static int argWeighting(int nArgs, int nUsedArgs, int nConstArgs) {
        int weight = nUsedArgs < nArgs ? 1 : 0;
        if (nConstArgs > 0) {
            weight += 2;
            weight += nConstArgs;
            weight = Math.max(0, weight - nArgs / 5);
            if (nConstArgs >= nUsedArgs - 1) {
                weight += 1;
            }
            if (nConstArgs == nUsedArgs) {
                weight += 1;
            }
        }
        return weight;
    }

    private static Entity getEntity(Call call) {
        var addr = (Address) call.getPtr();
        return addr.getEntity();
    }

    private Optional<InliningStateTracker.CalleeEntry> getCalleeEntry(Call call) {
        return stateTracker.getCalleeEntry(getEntity(call));
    }

    @RequiredArgsConstructor
    private static class PrioritizedCall implements Comparable<PrioritizedCall> {
        @Getter
        private final Call call;
        private final double priority;


        @Override
        public int compareTo(PrioritizedCall o) {
            return Comparator.<PrioritizedCall>comparingDouble(pc -> pc.priority).compare(this, o);
        }
    }
}
