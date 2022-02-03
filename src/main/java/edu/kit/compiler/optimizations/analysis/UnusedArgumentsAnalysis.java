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

/**
 * Call graph based analyses to determine unused function arguments.
 *
 * First, the graphs are analysed to see whether arguments are
 * definitively used or definitively unused. However, some arguments
 * might be used only as argument to another function. In case of recursive
 * calls, we thus can not determine in a single pass whether an argument is
 * actually used (because the arguments have cyclic dependencies).
 * To resolve this, we collect all dependencies between arguments and run
 * a fixed point iteration on the dependencies (note that this operates on
 * the call graph only and doesn't need to look at the function graphs).
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class UnusedArgumentsAnalysis {
    private final Map<Entity, ArgUsageValue[]> usageMap = new HashMap<>();

    public static  List<ArgMapping> run(CallGraph callGraph) {
        var analysis = new UnusedArgumentsAnalysis();
        return analysis.analyse(callGraph);
    }

    private List<ArgMapping> analyse(CallGraph callGraph) {
        usageMap.clear();
        var worklist = new StackWorklist<Entity>();

        // collect usage data and dependencies for args
        for (Entity func: callGraph.functionSet()) {
            if (func.getGraph() != null) {
                BackEdges.enable(func.getGraph());
                usageMap.put(func, analyzeArgs(func));
                worklist.enqueue(func);
                BackEdges.disable(func.getGraph());
            }
        }

        // run fixed point analysis to resolve dependencies
        while (!worklist.isEmpty()) {
            Entity current = worklist.dequeue();
            ArgUsageValue[] values = usageMap.get(current);
            if (updateValues(values)) {
                callGraph.getCallers(current).forEach(worklist::enqueue);
            }
        }

        // collect result
        List<ArgMapping> result = new ArrayList<>();
        for (var entry: usageMap.entrySet()) {
            var mapping = new ArgMapping(entry.getKey(), entry.getValue());
            if (mapping.anyUnused()) {
                result.add(mapping);
            }
        }
        return result;
    }

    /**
     * Single step of the fixed point iteration that updates
     * the usage values of a specific function.
     */
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
        var argProj = Util.getArgProj(function);
        ArgUsageValue[] result = new ArgUsageValue[Util.getNArgs(function)];
        Arrays.fill(result, new ArgUsageValue(false));
        if (argProj.isPresent()) {
            for (var edge : BackEdges.getOuts(argProj.get())) {
                if (edge.node.getOpCode() == binding_irnode.ir_opcode.iro_Proj) {
                    Proj currentArg = (Proj) edge.node;
                    int index = currentArg.getNum();
                    var newValue = analyzeNodeUsage(currentArg, argProj.get());
                    result[index] = result[index].supremum(newValue);
                }
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
        if (node.getMode().equals(Mode.getX()) || node.getMode().equals(Mode.getM())
                || node.getOpCode() == binding_irnode.ir_opcode.iro_Load
                || node.getOpCode() == binding_irnode.ir_opcode.iro_Store) {
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

    /**
     * Represents for a specific function which arguments are unused and
     * additionally translates each old argument index to the according new index.
     */
    @ToString
    public static class ArgMapping {
        @Getter
        private final Entity func;
        private final int[] indexMapping;
        private final boolean[] used;

        public ArgMapping(Entity func, ArgUsageValue[] usage) {
            this.func = func;
            this.indexMapping = new int[usage.length];
            this.used = new boolean[usage.length];
            int mapped = 0;
            for (int i = 0; i < usage.length; i++) {
                indexMapping[i] = mapped;
                if (usage[i].isDefinitivelyUsed()) {
                    // after the fixpoint iteration, unknown is also unused
                    // (due to cyclic dependencies)
                    used[i] = true;
                    mapped++;
                }
            }
        }

        public int nArgs() {
            return indexMapping.length;
        }

        public int getMappedIndex(int i) {
            return indexMapping[i];
        }

        public boolean isUsed(int i) {
            return used[i];
        }

        public boolean anyUnused() {
            return numUnused() > 0;
        }

        public int numUnused() {
            int unused = 0;
            for (boolean used: used) {
                if (!used) {
                    unused++;
                }
            }
            return unused;
        }
    }
}

/**
 * Lattice value that represents available information about the usage
 * of an argument.
 */
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
