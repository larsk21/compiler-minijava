package edu.kit.compiler.optimizations.inlining;

import edu.kit.compiler.optimizations.Optimization;
import edu.kit.compiler.optimizations.OptimizationState;
import edu.kit.compiler.optimizations.analysis.LoopAnalysis;
import firm.BackEdges;
import firm.Entity;
import firm.Graph;
import firm.bindings.binding_irgopt;
import firm.bindings.binding_irgraph;
import firm.bindings.binding_irnode;
import firm.nodes.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.*;

/**
 * Implements inlining as a local optimization.
 * The inliner basically works by collecting all call nodes of the firm graph,
 * then deciding for each call whether it can be inlined at all (directly recursive
 * calls and functions without return node can't be inlined, as well as standard library
 * functions), then deciding whether the inlining is worth it based on some heuristic.
 * For all remaining calls, a priority is calculated and the calls are inlined in order
 * of the priority. However, if the size of the function would grow too much by the inlining,
 * no more calls are inlined.
 *
 * Furthermore, the inliner uses some global information via the `OptimizationState` to:
 *  - find functions that are `always inline`, i.e. that are small enough that it makes
 *    sense to inline them at all call sizes without increasing the overall code size by much
 *  - track the size of a function over all inlining rounds, thus ensuring that the total
 *    code size only grows by a constant factor
 */
public class InliningOptimization implements Optimization.Local {
    private Graph graph;
    private InliningStateTracker stateTracker;

    @Override
    public boolean optimize(Graph graph, OptimizationState state) {
        this.graph = graph;
        this.stateTracker = state.getInlineStateTracker();

        var callerEntry = stateTracker.getCallerEntry(graph.getEntity());
        if (callerEntry.shouldStop()) {
            return false;
        }
        callerEntry.addPass();

        LoopAnalysis loopAnalysis = new LoopAnalysis(graph);
        loopAnalysis.analyze();

        // we transform the nodes in reverse postorder, i.e. we can access the
        // unchanged predecessors of a node when transforming it
        List<Call> alwaysInlineCalls = new ArrayList<>();
        List<PrioritizedCall> maybeInlineCalls = new ArrayList<>();
        collectCalls(loopAnalysis, alwaysInlineCalls, maybeInlineCalls);

        BackEdges.enable(graph);
        // approximates the current size of the function
        int currentNumNodes = CalleeAnalysis.run(graph).getNumNodes();
        boolean changes = false;
        for (Call call: alwaysInlineCalls) {
            int numNodes = getCalleeEntry(call).get().getNumNodes();
            if (numNodes > InliningStateTracker.LARGE_FN
                    && currentNumNodes > InliningStateTracker.LARGE_FN) {
                // edge case: large function with exactly one call site
                double prio = calculatePriority(loopAnalysis, call, getCalleeEntry(call).get());
                maybeInlineCalls.add(new PrioritizedCall(call, prio));
            } else {
                Inliner.inline(graph, call, getEntity(call).getGraph());
                callerEntry.addCompletelyInlinedNodes(numNodes);
                currentNumNodes += numNodes;
                changes = true;
            }
        }

        maybeInlineCalls.sort(Comparator.reverseOrder());
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

        graph.confirmProperties(binding_irgraph.ir_graph_properties_t.IR_GRAPH_PROPERTIES_NONE);
        binding_irgopt.remove_unreachable_code(graph.ptr);
        binding_irgopt.remove_bads(graph.ptr);

        return changes;
    }

    private void collectCalls(LoopAnalysis loops, List<Call> alwaysInlineCalls, List<PrioritizedCall> maybeInlineCalls) {
        graph.walk(new NodeVisitor.Default() {
            @Override
            public void visit(Call call) {
                var entry  = getCalleeEntry(call);
                var callee = getEntity(call).getGraph();
                if (entry.isPresent() && Inliner.canBeInlined(call.getGraph(), callee)) {
                    double prio = calculatePriority(loops, call, entry.get());
                    if (entry.get().isAlwaysInline()) {
                        alwaysInlineCalls.add(call);
                    } else if (prio >= 0) {
                        maybeInlineCalls.add(new PrioritizedCall(call, prio));
                    }
                }
            }
        });
    }

    /**
     * High priority is considered first.
     *
     * Returns -1 if the call shouldn't be inlined at all.
     */
    private static double calculatePriority(LoopAnalysis loops, Call call, InliningStateTracker.CalleeEntry entry) {
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
        if (entry.isAlwaysInline()) {
            // edge case: large function with exactly one call site
            logWeight += 3;
        }
        boolean doInline = logWeight >= 3 || (
                (Math.pow(2, logWeight) * InliningStateTracker.UNPROBLEMATIC_SIZE_INCREASE / 2) >= entry.getNumNodes()
                        && logWeight >= 0
        );
        if (doInline) {
            int loopDepth = loops.getBlockLoops().get((Block) call.getBlock()).size();
            logWeight += 2 * loopDepth;
            return Math.pow(2, logWeight) / entry.getNumNodes();
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
        private static final Comparator<PrioritizedCall> comparator = Comparator.<PrioritizedCall>comparingDouble(pc -> pc.priority);

        @Getter
        private final Call call;
        private final double priority;

        @Override
        public int compareTo(PrioritizedCall o) {
            return comparator.compare(this, o);
        }
    }
}
