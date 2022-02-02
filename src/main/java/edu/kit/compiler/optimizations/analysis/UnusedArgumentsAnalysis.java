package edu.kit.compiler.optimizations.analysis;

import edu.kit.compiler.io.StackWorklist;
import edu.kit.compiler.optimizations.CallGraph;
import edu.kit.compiler.optimizations.Util;
import firm.BackEdges;
import firm.Entity;
import firm.Graph;
import firm.Mode;
import firm.bindings.binding_irnode;
import firm.nodes.Call;
import firm.nodes.Node;
import firm.nodes.Proj;
import firm.nodes.Start;
import lombok.*;

import java.util.*;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class UnusedArgumentsAnalysis {
    private final Map<Entity, ArgUsageValue[]> usageMap = new HashMap<>();

    public static void run(CallGraph callGraph) {
        var analysis = new UnusedArgumentsAnalysis();
        analysis.analyse(callGraph);
    }

    private void analyse(CallGraph callGraph) {
        usageMap.clear();
        var worklist = new StackWorklist<Entity>();
        for (Entity func: callGraph.functionSet()) {
            if (func.getGraph() != null) {
                BackEdges.enable(func.getGraph());
                usageMap.put(func, analyzeArgs(func));
                worklist.enqueue(func);
                BackEdges.disable(func.getGraph());
            }
        }

        while (!worklist.isEmpty()) {
            Entity current = worklist.dequeue();
            ArgUsageValue[] values = usageMap.get(current);
            if (updateValues(values)) {
                callGraph.getCallers(current).forEach(worklist::enqueue);
            }
        }

        for (var entry: usageMap.entrySet()) {
            System.out.println(entry.getKey().getLdName() + ":");
            System.out.println(Arrays.toString(entry.getValue()));
        }
    }

    private boolean updateValues(ArgUsageValue[] values) {
        boolean changed = false;
        for (int i = 0; i < values.length; i++) {
            if (values[i].isUnknown()) {
                boolean definitivelyUsed = false;
                List<ArgDependency> deps = values[i].getDependencies();
                List<ArgDependency> updated = new ArrayList<>();
                for (ArgDependency d: deps) {
                    var val = getUsage(d.getFunction(), d.getArgIndex());
                    if (val.isDefinitivelyUsed()) {
                        values[i] = new ArgUsageValue(true);
                        definitivelyUsed = true;
                        changed = true;
                        break;
                    } else if (val.isUnknown()) {
                        updated.add(d);
                    }
                }

                if (!definitivelyUsed) {
                    values[i] = new ArgUsageValue(updated);
                    changed |= (updated.size() != deps.size());
                }
            }
        }
        return changed;
    }

    private ArgUsageValue getUsage(Entity function, int index) {
        Graph graph = function.getGraph();
        if (graph == null) {
            // standard library function
            return new ArgUsageValue(true);
        } else {
            return usageMap.get(function)[index];
        }
    }

    private ArgUsageValue[] analyzeArgs(Entity function) {
        Proj argProj = null;
        Start start = function.getGraph().getStart();
        for (var edge: BackEdges.getOuts(start)) {
            if (edge.node.getOpCode() == binding_irnode.ir_opcode.iro_Proj
                    && edge.node.getMode().equals(Mode.getT())) {
                assert argProj == null: "Proj not unique!";
                argProj = (Proj)  edge.node;
            }
        }

        ArgUsageValue[] result = new ArgUsageValue[Util.getNArgs(function)];
        Arrays.fill(result, new ArgUsageValue(false));
        for (var edge: BackEdges.getOuts(argProj)) {
            if (edge.node.getOpCode() == binding_irnode.ir_opcode.iro_Proj) {
                Proj currentArg = (Proj) edge.node;
                int index = currentArg.getNum();
                var newValue = analyzeNodeUsage(currentArg, argProj);
                result[index] = result[index].supremum(newValue);
            }
        }
        return result;
    }

    /**
     * We consider a node used if it is the predecessor of any control flow node or
     * the predecessor of a memory-node. If it is the predecessor of a call node,
     * we need to analyze whether the argument is used in the called function
     * (which is done later with a fixed-point analysis).
     */
    private ArgUsageValue analyzeNodeUsage(Node node, Node pred) {
        if (node.getMode().equals(Mode.getX()) || node.getMode().equals(Mode.getM())) {
            return new ArgUsageValue(true);
        }
        if (node.getOpCode() == binding_irnode.ir_opcode.iro_Call) {
            // find the predecessor index
            for (int i = 0; i < node.getPredCount(); i++) {
                if (node.getPred(i).equals(pred)) {
                    var dep = new ArgDependency(Util.getCallee((Call) node), i - 2);
                    return new ArgUsageValue(List.of(dep));
                }
            }
            assert false: "Could not find predecessor index for call.";
        }
        ArgUsageValue result = new ArgUsageValue(false);
        for (var edge: BackEdges.getOuts(node)) {
            var value = analyzeNodeUsage(edge.node, node);
            result = result.supremum(value);
            if (result.isDefinitivelyUsed()) {
                return result;
            }
        }
        return result;
    }
}

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
class ArgUsageValue {
    @Getter
    private final boolean definitivelyUsed;
    @Getter
    private final List<ArgDependency> dependencies;

    public ArgUsageValue(boolean used) {
        this.definitivelyUsed = used;
        this.dependencies = List.of();
    }

    public ArgUsageValue(List<ArgDependency> dependencies) {
        this.definitivelyUsed = false;
        this.dependencies = dependencies;
    }

    public boolean isUnknown() {
        assert !definitivelyUsed || dependencies.isEmpty();
        return !dependencies.isEmpty();
    }

    public boolean isDefinitivelyUnused() {
        return !definitivelyUsed && dependencies.isEmpty();
    }

    public ArgUsageValue supremum(ArgUsageValue other) {
        boolean used = this.definitivelyUsed || other.definitivelyUsed;
        List<ArgDependency> dependencies = new ArrayList<>();
        if (!used) {
            dependencies.addAll(this.dependencies);
            dependencies.addAll(other.dependencies);
        }
        return new ArgUsageValue(used, dependencies);
    }

    @Override
    public String toString() {
        if (definitivelyUsed) {
            return "<used>";
        } else if (dependencies.isEmpty()) {
            return "<unused>";
        } else {
            return String.format("<deps=%s>", dependencies);
        }
    }
}

@Data
class ArgDependency {
    private final Entity function;
    private final int argIndex;

    @Override
    public String toString() {
        return String.format("func=%s, index=%s", function.getLdName(), argIndex);
    }
}
