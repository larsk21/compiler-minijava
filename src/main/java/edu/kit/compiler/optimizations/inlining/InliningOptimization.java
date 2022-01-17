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
        graph.walk(new NodeVisitor.Default() {
            @Override
            public void visit(Call call) {
                var entry  = getCalleeEntry(call);
                if (entry.isPresent()) {
                    if (entry.get().isAlwaysInline()) {
                        alwaysInlineCalls.add(call);
                    } else {
                        maybeInlineCalls.add(new PrioritizedCall(
                                call, calculatePriority(call, entry.get())
                        ));
                    }
                }
            }
        });
        maybeInlineCalls.sort(Comparator.reverseOrder());

        BackEdges.enable(graph);

        // approximates the current size of the function
        int currentNumNodes = CalleeAnalysis.run(graph).getNumNodes();
        var callerEntry = stateTracker.getCallerEntry(graph.getEntity());

        boolean changes = false;
        for (Call call: alwaysInlineCalls) {
            Inliner.inline(graph, call, getEntity(call).getGraph());
            int numNodes = getCalleeEntry(call).get().getNumNodes();
            callerEntry.addCompletelyInlinedNodes(numNodes);
            currentNumNodes += numNodes;
            changes = true;
            System.out.println(String.format("Inlining %s into %s",
                getEntity(call).getLdName(), graph.getEntity().getLdName()));
        }

        for (var pc: maybeInlineCalls) {
            Call call = pc.getCall();
            int numNodes = getCalleeEntry(call).get().getNumNodes();
            System.out.println(String.format("Inlining canditate: %s into %s",
                    getEntity(call).getLdName(), graph.getEntity().getLdName()));
            if (currentNumNodes + numNodes <= callerEntry.acceptableSize()) {
                Inliner.inline(graph, call, getEntity(call).getGraph());
                changes = true;
                System.out.println("--> Inlining");
            } else {
                System.out.println("--> Not Inlining");
            }
        }

        BackEdges.disable(graph);

        binding_irgopt.remove_unreachable_code(graph.ptr);
        binding_irgopt.remove_bads(graph.ptr);

        return changes;
    }

    /**
     * High priority is considered first.
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
        return Math.pow(2, logWeight) / entry.getNumNodes();
    }

    private static Entity getEntity(Call call) {
        var addr = (Address) call.getPtr();
        return addr.getEntity();
    }

    private Optional<InliningStateTracker.CalleeEntry> getCalleeEntry(Call call) {
        return stateTracker.getCalleeEntry(call.getGraph().getEntity());
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
