package edu.kit.compiler.optimizations.attributes;

import static firm.bindings.binding_irop.irop_flags.irop_flag_uses_memory;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.StreamSupport;

import edu.kit.compiler.io.StackWorklist;
import edu.kit.compiler.io.Worklist;
import edu.kit.compiler.optimizations.Util;
import edu.kit.compiler.optimizations.attributes.Attributes.Purity;
import edu.kit.compiler.transform.StandardLibraryEntities;
import firm.Entity;
import firm.Graph;
import firm.Mode;
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
     * Return attributes of the given function. Attributes are computed as they
     * are needed, and will subsequently be cached.
     */
    public Attributes getAttributes(Entity entity) {
        // return functions.getOrDefault(entity, Attributes.MINIMUM);
        var cachedAttributes = functions.get(entity);
        if (cachedAttributes == null) {
            var graph = entity.getGraph();
            if (graph != null) {
                // insert dummy attributes to deal with recursion, if a function
                // may call itself, termination can not be guaranteed
                functions.put(entity, new Attributes(Purity.CONST, false, false));

                // First analyze the memory chains to determine purity
                var attributes = new Attributes(Purity.CONST, true, false);
                new MemoryVisitor(graph, attributes).apply();

                // Second analyze termination behavior of the function
                checkTermination(graph, attributes);

                // Third analyze whether the function is malloc-like
                attributes.setMalloc(isMallocLike(graph));

                functions.put(entity, attributes);
                return attributes;
            } else if (entity.equals(calloc)) {
                // special case for calls to calloc
                var attributes = new Attributes(Purity.PURE, true, true);
                functions.put(entity, attributes);
                return attributes;
            } else {
                // no implementation known, e.g std library function
                functions.put(entity, Attributes.MINIMUM);
                return Attributes.MINIMUM;
            }
        } else {
            return cachedAttributes;
        }
    }

    /**
     * Invalidate previously computed attributes for the given function. The
     * operation is done locally, i.e. attributes of called functions are not
     * invalidated, neither are those of callees of the function. It is assumed
     * that attributes will never get "worse" during optimization.
     */
    public void invalidate(Graph graph) {
        functions.remove(graph.getEntity());
    }

    /**
     * Check the given graph for control flow loops and unset `terminates` in
     * the given attributes if a loop is found.
     */
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

    private static void checkTermination(Block initialBlock, Attributes attributes) {
        // ! this is not currently correct

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

    /**
     * Returns true if the given graph is malloc-like, i.e. it may return newly
     * allocated memory.
     */
    private boolean isMallocLike(Graph graph) {
        return StreamSupport.stream(graph.getEndBlock().getPreds().spliterator(), false)
                .anyMatch(this::maybeNewAlloc);
    }

    /**
     * Returns true if the given node may be the result of a call to a
     * malloc-like function. 
     */
    private boolean maybeNewAlloc(Node node) {
        return switch (node.getOpCode()) {
            case iro_Return -> {
                if (node.getPredCount() != 2) {
                    yield false;
                } else {
                    yield maybeNewAlloc(node.getPred(1));
                }
            }
            case iro_Proj -> maybeNewAlloc(node.getPred(0));
            case iro_Phi -> {
                if (node.visited()) {
                    yield false;
                } else {
                    node.markVisited();
                    for (var pred : node.getPreds()) {
                        if (maybeNewAlloc(pred)) yield true;
                    }
                    yield false;
                }
            }
            case iro_Call -> {
                var callee = Util.getCallee((Call) node);
                var attributes = getAttributes(callee);
                yield (attributes.isMalloc() ? true : false);
            }

            default -> {
                var offset = Util.hasFlag(node, irop_flag_uses_memory) ? 1 : 0;
                for (var i = offset; i < node.getPredCount(); ++i) {
                    if (maybeNewAlloc(node.getPred(i))) yield true;
                }
                yield false;
            }
        };
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
            attributes.setPurity(Purity.IMPURE);
            attributes.setTerminates(false);
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
            var calleeAttributes = getAttributes(Util.getCallee(node));
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
