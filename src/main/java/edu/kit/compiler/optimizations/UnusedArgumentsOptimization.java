package edu.kit.compiler.optimizations;

import edu.kit.compiler.optimizations.analysis.UnusedArgumentsAnalysis;
import edu.kit.compiler.optimizations.analysis.UnusedArgumentsAnalysis.ArgMapping;
import firm.*;
import firm.bindings.binding_irgopt;
import firm.bindings.binding_irnode;
import firm.nodes.Call;
import firm.nodes.Node;
import firm.nodes.NodeVisitor;
import firm.nodes.Proj;

import java.util.*;
import java.util.stream.Collectors;

/**
 * A global optimization that analyses for all functions whether some of their
 * arguments are unused (or used for recursive calls only). Then, all unused
 * arguments are eliminated by updating the firm type of the function, the graph
 * of the function and all call-sites.
 */
public class UnusedArgumentsOptimization implements Optimization.Global {
    @Override
    public Set<Graph> optimize(CallGraph callGraph) {
        Set<Graph> changed = new HashSet<>();
        var mappings = UnusedArgumentsAnalysis.run(callGraph);

        // collect call-sites
        Map<Entity, List<Call>> callsites = new HashMap<>();
        var callers = callGraph.getTransitiveCallers(
                mappings.stream().map(ArgMapping::getFunc).collect(Collectors.toList()));
        callers.forEachRemaining(func -> {
            Graph graph = func.getGraph();
            graph.walk(new NodeVisitor.Default() {
                @Override
                public void visit(Call call) {
                    Entity func = Util.getCallee(call);
                    callsites.computeIfAbsent(func, f -> new ArrayList<>()).add(call);
                }
            });
        });

        // apply changes
        for (var m: mappings) {
            changed.add(m.getFunc().getGraph());
            updateType(m);
            updateCallee(m);
            updateCallers(m, callsites.computeIfAbsent(m.getFunc(), f -> List.of()));
        }

        for (Graph graph: changed) {
            binding_irgopt.remove_bads(graph.ptr);
            binding_irgopt.remove_unreachable_code(graph.ptr);
            binding_irgopt.remove_bads(graph.ptr);
        }
        return changed;
    }

    private void updateType(ArgMapping mapping) {
        Entity func = mapping.getFunc();
        var type = (MethodType) func.getType();
        assert type.getNParams() == mapping.nArgs();
        List<Type> newParamTypes = new ArrayList<>();
        for (int i = 0; i < mapping.nArgs(); i++) {
            if (mapping.isUsed(i)) {
                newParamTypes.add(type.getParamType(i));
            }
        }
        func.setType(new MethodType(
                newParamTypes.toArray(new Type[0]),
                type.getNRess() == 0 ?  new Type[] {} : new Type[] {type.getResType(0)}
        ));
    }

    private void updateCallee(ArgMapping mapping) {
        Graph graph = mapping.getFunc().getGraph();
        BackEdges.enable(graph);
        var argProj = graph.getArgs();
        for (var edge: BackEdges.getOuts(argProj)) {
            if (edge.node.getOpCode() == binding_irnode.ir_opcode.iro_Proj) {
                Proj currentArg = (Proj) edge.node;
                int index = currentArg.getNum();
                if (mapping.isUsed(index)) {
                    currentArg.setNum(mapping.getMappedIndex(index));
                } else {
                    Graph.exchange(currentArg, graph.newBad(currentArg.getMode()));
                }
            }
        }
        BackEdges.disable(graph);
    }

    private void updateCallers(ArgMapping mapping, List<Call> calls) {
        Graph graph = mapping.getFunc().getGraph();
        Entity func = mapping.getFunc();
        var type = (MethodType) func.getType();
        for (Call call: calls) {
            List<Node> inputs = new ArrayList<>();
            for (int i = 2; i < call.getPredCount(); i++) {
                if (mapping.isUsed(i - 2)) {
                    inputs.add(call.getPred(i));
                }
            }
            Node replacement = graph.newCall(call.getBlock(), call.getMem(),
                    call.getPred(1), inputs.toArray(new Node[0]), type);
            Graph.exchange(call, replacement);
        }
    }
}
