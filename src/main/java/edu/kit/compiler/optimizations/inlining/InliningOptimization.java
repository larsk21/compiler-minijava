package edu.kit.compiler.optimizations.inlining;

import edu.kit.compiler.optimizations.Optimization;
import firm.BackEdges;
import firm.Graph;
import firm.bindings.binding_irgopt;
import firm.nodes.*;
import lombok.RequiredArgsConstructor;

import java.util.*;


// TODO: dont inline endless loop?
@RequiredArgsConstructor
public class InliningOptimization implements Optimization.Local {
    private final Map<String, Graph> available_callees;
    private Graph graph;

    @Override
    public boolean optimize(Graph graph) {
        this.graph = graph;

        // we transform the nodes in reverse postorder, i.e. we can access the
        // unchanged predecessors of a node when transforming it
        List<Call> calls = new ArrayList<>();
        graph.walk(new NodeVisitor.Default() {
            @Override
            public void visit(Call node) {
                calls.add(node);
            }
        });

        BackEdges.enable(graph);

        boolean changes = false;
        for (Call call: calls) {
            changes |= inline(call);
        }

        BackEdges.disable(graph);

        binding_irgopt.remove_bads(graph.ptr);
        binding_irgopt.remove_unreachable_code(graph.ptr);
        binding_irgopt.remove_bads(graph.ptr);

        return changes;
    }

    private boolean inline(Call call) {
        String name = getName(call);
        if (available_callees.containsKey(name)) {
            Graph callee = available_callees.get(name);
            Inliner.inline(graph, call, callee);
            return true;
        }
        return false;
    }

    private static String getName(Call call) {
        var addr = (Address) call.getPtr();
        return addr.getEntity().getLdName();
    }
}
