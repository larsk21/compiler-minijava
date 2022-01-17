package edu.kit.compiler.optimizations.inlining;

import edu.kit.compiler.optimizations.CallGraph;
import edu.kit.compiler.optimizations.Optimization;
import firm.BackEdges;
import firm.Entity;
import firm.Graph;
import firm.bindings.binding_irgopt;
import firm.bindings.binding_irnode;
import firm.nodes.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.*;


// TODO: dont inline endless loop?
@RequiredArgsConstructor
public class InliningOptimization implements Optimization.Global {
    private final InliningStateTracker stateTracker = new InliningStateTracker();

    @Override
    public Set<Graph> optimize(CallGraph callGraph) {
        Set<Graph> changes = new HashSet<>();
        callGraph.walkGraphsBottomUp(graph -> {
            boolean changed = inliningPass(graph);
            if (changed) {
                changes.add(graph);
            }
            stateTracker.updateEntity(callGraph, graph.getEntity());
        });
        return changes;
    }

    private boolean inliningPass(Graph graph) {
        // we transform the nodes in reverse postorder, i.e. we can access the
        // unchanged predecessors of a node when transforming it
        List<Call> alwaysInlineCalls = new ArrayList<>();
        List<PrioritizedCall> maybeInlineCalls = new ArrayList<>();
        collectCalls(graph, alwaysInlineCalls, maybeInlineCalls);

        // approximates the current size of the function
        int currentNumNodes = CalleeAnalysis.run(graph).getNumNodes();
        var callerEntry = stateTracker.getCallerEntry(graph.getEntity());

        BackEdges.enable(graph);
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

    private void collectCalls(Graph graph, List<Call> alwaysInlineCalls, List<PrioritizedCall> maybeInlineCalls) {
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
        boolean hasUnusedArg = call.getPredCount() > entry.getNumUsedArgs() + 2;
        int numConstArgs = 0;
        for (int i = 2; i < call.getPredCount(); i++) {
            if (call.getPred(i).getOpCode() == binding_irnode.ir_opcode.iro_Const) {
                numConstArgs++;
            }
        }
        int logWeight = (hasUnusedArg ? 1 : 0) + (numConstArgs == 0 ? 0 : numConstArgs + 2);
        boolean doInline = Math.pow(2, logWeight) * InliningStateTracker.UNPROBLEMATIC_SIZE_INCREASE / 2 >= entry.getNumNodes();
        if (doInline) {
            double basePrio = Math.pow(2, logWeight) / entry.getNumNodes();
            return basePrio;
        }
        return -1;
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
