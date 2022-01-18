package edu.kit.compiler.optimizations;

import java.util.HashMap;
import java.util.Map;

import edu.kit.compiler.io.StackWorklist;
import edu.kit.compiler.io.Worklist;
import edu.kit.compiler.transform.StandardLibraryEntities;
import firm.Entity;
import firm.Graph;
import firm.Mode;
import firm.Program;
import firm.bindings.binding_irnode.ir_opcode;
import firm.nodes.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

/**
 * Implements an analysis to check if functions are pure or const.
 */
@RequiredArgsConstructor
public final class FunctionAttributeAnalysis {

    private final Map<Entity, Attributes> functions = new HashMap<>();
    private final Entity calloc = StandardLibraryEntities.INSTANCE.getCalloc().getEntity();

    public Attributes get(Entity entity) {
        return functions.getOrDefault(entity, Attributes.MINIMUM);
    }

    public void apply() {
        functions.clear();

        for (var graph : Program.getGraphs()) {
            computeAttributes(graph.getEntity());
        }
    }

    private Attributes computeAttributes(Entity entity) {
        var attributes = functions.get(entity);
        if (attributes != null) {
            return attributes;
        } else {
            var graph = entity.getGraph();
            if (graph != null) {
                // set function to to minimal attributes to deal with recursion
                functions.put(entity, Attributes.MINIMUM);

                var visitor = new MemoryVisitor(graph);
                visitor.apply();

                functions.put(entity, visitor.attributes);
                return visitor.attributes;
            } else if (entity.equals(calloc)) {
                // todo special case for calloc
                functions.put(entity, Attributes.MINIMUM);
                return Attributes.MINIMUM;
            } else {
                // no implementation known, e.g std library function
                functions.put(entity, Attributes.MINIMUM);
                return Attributes.MINIMUM;
            }
        }
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static final class Attributes {
        
        public static final Attributes MINIMUM = new Attributes();

        private Purity purity = Purity.IMPURE;

        /**
         * A functions that is guaranteed not to result in an endless loop or
         * abort the program, which for our purposes means no Div or Mod.
         */
        private boolean terminates = false;

        /**
         * Function returns newly allocated memory.
         */
        private boolean malloc = false;

        public void ceilPurity(Purity ceil) {
            purity = purity.min(ceil);
        }
    }

    /**
     * Represents the purity of a function. Each entry of this enum imposes
     * more restrictions on a function.
     */
    public static enum Purity {
        /**
         * Any function is impure by default.
         */
        IMPURE,

        /**
         * A function is pure if it does not affect the observable state of the
         * program. For our purposes, this means no stores or calls to non-pure
         * functions.
         * 
         * Calls to pure functions can be removed without changing the semantics of
         * the program.
         */
        PURE,

        /**
         * A function is const if it's pure and its return value is not affected
         * by the state of the program. A const function's return value
         * therefore depend solely on its arguments.
         */
        CONST;

        public boolean isPure() {
            return PURE.compareTo(this) <= 0;
        }

        public boolean isConst() {
            return CONST == this;
        }

        public Purity min(Purity other) {
            return this.compareTo(other) <= 0 ? this : other;
        }
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private final class MemoryVisitor extends NodeVisitor.Default {

        private final Worklist<Node> worklist = new StackWorklist<>(true);
        private final Attributes attributes = new Attributes(Purity.CONST, true, false);

        private final Graph graph;

        private void apply() {
            graph.incVisited();

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

            for (var keepAlive : graph.getEnd().getPreds()) {
                if (keepAlive.getMode().equals(Mode.getM())) {
                    followMemoryChain(keepAlive);
                }
            }
        }

        private void followMemoryChain(Node mem) {
            assert worklist.isEmpty();

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

            worklist.enqueueInOrder(pred);
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
            attributes.ceilPurity(Purity.PURE);
            worklist.enqueueInOrder(node.getMem());
        }

        @Override
        public void visit(Div node) {
            // todo handle this in terminates analysis
            worklist.enqueueInOrder(node.getMem());
        }

        @Override
        public void visit(Mod node) {
            // todo handle this in terminates analysis
            worklist.enqueueInOrder(node.getMem());
        }

        @Override
        public void visit(Sync node) {
            node.getPreds().forEach(worklist::enqueueInOrder);
        }

        @Override
        public void visit(Call node) {
            // purity of caller is limited by purity of callee
            var calleeAttributes = computeAttributes(Util.getCallee(node));
            attributes.ceilPurity(calleeAttributes.getPurity());
            attributes.terminates &= calleeAttributes.terminates;
            worklist.enqueueInOrder(node.getMem());
        }

        @Override
        public void visit(Phi node) {
            // todo deal with loops in terminates analysis
            node.getPreds().forEach(worklist::enqueueInOrder);
        }

        @Override
        public void visit(Return node) {
            worklist.enqueueInOrder(node.getMem());
        }
    }
}
