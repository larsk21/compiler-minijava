package edu.kit.compiler.optimizations.attributes;

import java.util.HashMap;
import java.util.Map;

import edu.kit.compiler.io.StackWorklist;
import edu.kit.compiler.io.Worklist;
import edu.kit.compiler.optimizations.Util;
import edu.kit.compiler.optimizations.attributes.Attributes.Purity;
import edu.kit.compiler.transform.StandardLibraryEntities;
import firm.Entity;
import firm.Graph;
import firm.Mode;
import firm.Program;
import firm.bindings.binding_irnode.ir_opcode;
import firm.nodes.*;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

/**
 * Implements an analysis to check if functions are pure or const.
 */
@RequiredArgsConstructor
public final class AttributeAnalysis {

    private final Map<Entity, Attributes> functions = new HashMap<>();
    private final Entity calloc = StandardLibraryEntities.INSTANCE.getCalloc().getEntity();

    /**
     * Return the attributes computed for the given function.
     */
    public Attributes get(Entity entity) {
        return functions.getOrDefault(entity, Attributes.MINIMUM);
    }

    /**
     * Apply the analysis to the entire program. This will analyse all graphs
     * known to Firm.
     */
    public void apply() {
        apply(Program.getGraphs());
    }

    /**
     * Apply the analysis to the given set of graphs (intended for testing).
     */
    public void apply(Iterable<Graph> graphs) {
        functions.clear();

        for (var graph : graphs) {
            computeAttributes(graph.getEntity());
        }
    }

    /**
     * Update the attributes for the given function, the updates is only done
     * locally, i.e. attributes of called functions are not updated, neither are
     * those of callees of the updated function. It is assumed that attributes
     * will never get "worse" during optimization.
     */
    public void update(Graph graph) {
        functions.remove(graph.getEntity());
        computeAttributes(graph.getEntity());
    }

    /**
     * Compute and return attributes for the given function if no cached values
     * are available.
     */
    private Attributes computeAttributes(Entity entity) {
        var cachedAttributes = functions.get(entity);
        if (cachedAttributes == null) {
            var graph = entity.getGraph();
            if (graph != null) {
                // insert a set of dummy attributes for the function
                var dummyAttrs = new Attributes(Purity.CONST, false, false);
                functions.put(entity, dummyAttrs);

                // First analyze the memory chains to determine purity
                var attributes = new Attributes(Purity.CONST, true, false);
                new MemoryVisitor(graph, attributes).apply();

                // Second analyze termination behavior of the function
                checkTermination(graph, attributes);

                functions.put(entity, attributes);
                return attributes;
            } else if (entity.equals(calloc)) {
                // todo special case for calloc
                functions.put(entity, Attributes.MINIMUM);
                return Attributes.MINIMUM;
            } else {
                // no implementation known, e.g std library function
                functions.put(entity, Attributes.MINIMUM);
                return Attributes.MINIMUM;
            }
        } else {
            return cachedAttributes;
        }
    }

    private static void checkTermination(Graph graph, Attributes attributes) {
        graph.incBlockVisited();
        checkTermination(graph.getEndBlock(), attributes);

        for (var keepAlive : graph.getEnd().getPreds()) {
            if (keepAlive.getMode().equals(Mode.getBB())
                    && keepAlive.getOpCode() != ir_opcode.iro_Bad) {
                checkTermination((Block) keepAlive, attributes);
            }
        }
    }

    private static final void checkTermination(Block initialBlock, Attributes attributes) {
        var worklist = new StackWorklist<Block>(true);
        worklist.enqueueInOrder(initialBlock);

        while (attributes.isTerminates() && !worklist.isEmpty()) {
            var block = worklist.dequeue();

            if (block.blockVisited()) {
                attributes.setTerminates(false);
            } else {
                block.markBlockVisited();
                block.getPreds().forEach(pred -> {
                    if (pred.getOpCode() != ir_opcode.iro_Bad) {
                        worklist.enqueueInOrder((Block) pred.getBlock());
                    }
                });
            }
        }
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private final class MemoryVisitor extends NodeVisitor.Default {

        private final Worklist<Node> worklist = new StackWorklist<>(false);

        private final Graph graph;
        private final Attributes attributes;

        public void apply() {
            graph.incVisited();

            // walk the memory chain starting at each Return node
            for (var pred : graph.getEndBlock().getPreds()) {
                switch (pred.getOpCode()) {
                    case iro_Return -> followMemoryChain(pred);
                    case iro_Bad -> {
                        // we can safely ignore this
                    }
                    default -> throw new IllegalStateException(
                            "unexpected predecessor of EndBlock");
                }
            }

            // walk the chains starting at any memory keep-alive edges
            for (var keepAlive : graph.getEnd().getPreds()) {
                if (keepAlive.getMode().equals(Mode.getM())) {
                    followMemoryChain(keepAlive);
                }
            }
        }

        private void followMemoryChain(Node mem) {
            worklist.enqueue(mem);
            while (attributes.getPurity().isPure() && !worklist.isEmpty()) {
                var node = worklist.dequeue();
                if (!node.visited()) {
                    node.markVisited();
                    node.accept(this);
                }
            }
        }

        @Override
        public void defaultVisit(Node node) {
            throw new IllegalStateException(node.toString());
        }

        @Override
        public void visit(Proj node) {
            assert node.getMode().equals(Mode.getM());

            var pred = node.getPred();
            if (pred.getOpCode() == ir_opcode.iro_Tuple) {
                // tuples are skipped for simplicity
                var tuple = (Tuple) node.getPred();
                pred = tuple.getPred(node.getNum());
            }

            worklist.enqueue(pred);
        }

        @Override
        public void visit(Start node) {
            // we have reached the end of the memory chain
        }

        @Override
        public void visit(NoMem node) {
            // we have reached the end of the memory chain
        }

        @Override
        public void visit(Store node) {
            attributes.setPurity(Purity.IMPURE);
        }

        @Override
        public void visit(Load node) {
            limitPurity(Purity.PURE);
            worklist.enqueue(node.getMem());
        }

        @Override
        public void visit(Div node) {
            // ? could this be dealt with better
            attributes.setPurity(Purity.IMPURE);
            attributes.setTerminates(false);
            worklist.enqueue(node.getMem());
        }

        @Override
        public void visit(Mod node) {
            // ? could this be dealt with better
            attributes.setPurity(Purity.IMPURE);
            attributes.setTerminates(false);
            worklist.enqueue(node.getMem());
        }

        @Override
        public void visit(Sync node) {
            node.getPreds().forEach(worklist::enqueue);
        }

        @Override
        public void visit(Phi node) {
            node.getPreds().forEach(worklist::enqueue);
        }

        @Override
        public void visit(Call node) {
            // purity of caller is limited by purity of callee
            var calleeAttributes = computeAttributes(Util.getCallee(node));
            limitPurity(calleeAttributes.getPurity());

            // not guaranteed to terminate if the callee is not,
            // this check also deals with recursion
            limitTerminates(calleeAttributes.isTerminates());
            worklist.enqueue(node.getMem());
        }

        @Override
        public void visit(Return node) {
            worklist.enqueue(node.getMem());
        }

        private void limitPurity(Purity limit) {
            attributes.setPurity(attributes.getPurity().min(limit));
        }

        private void limitTerminates(boolean limit) {
            attributes.setTerminates(limit && attributes.isTerminates());
        }
    }
}
