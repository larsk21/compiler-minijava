package edu.kit.compiler.optimizations.unrolling;

import static firm.bindings.binding_irgraph.ir_graph_properties_t.IR_GRAPH_PROPERTY_CONSISTENT_DOMINANCE;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.kit.compiler.io.StackWorklist;
import edu.kit.compiler.optimizations.Util;
import firm.BlockWalker;
import firm.Graph;
import firm.bindings.binding_irdom;
import firm.bindings.binding_irnode.ir_opcode;
import firm.nodes.Block;
import firm.nodes.Cond;
import firm.nodes.Node;
import firm.nodes.Proj;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.EqualsAndHashCode.Include;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

/**
 * Implements an analysis to detect loops in Firm graphs.
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class LoopAnalysis {

    /**
     * A map of loop headers to Loops
     */
    private final Map<Block, Loop> loops = new HashMap<>();

    public static List<LoopTree> apply(Graph graph) {
        graph.assureProperties(IR_GRAPH_PROPERTY_CONSISTENT_DOMINANCE);

        var analysis = new LoopAnalysis();
        analysis.collectLoops(graph);

        var surroundingLoops = analysis.findSurroundingLoops(graph);
        analysis.checkLoopsClosed(graph, surroundingLoops);

        return LoopTreeBuilder.build(analysis, surroundingLoops);
    }

    private void collectLoops(Graph graph) {
        graph.walkBlocksPostorder(new BlockWalker() {
            @Override
            public void visitBlock(Block block) {
                Util.forEachPredBlock(block, (predBlock, i) -> {
                    if (isBackEdge(predBlock, block)) {
                        // It is possible for multiple backedges to point to the
                        // same loop header
                        loops.computeIfAbsent(block, Loop::new).addBackEdge(i);
                    }
                });
            }
        });

        for (var loop : loops.values()) {
            if (loop.cond == null || loop.exitProj == -1) {
                loop.setInvalid();
            }
        }
    }

    /**
     * Returns a map of each block in the graph to its nearest surrounding loop.
     * More formally, for each block B find a loop L such that B is contained
     * in the body of L and and there is no Loop L' such that B is contained in
     * the body of L' and the header of L' is not contained in the body of L.
     */
    private Map<Block, Loop> findSurroundingLoops(Graph graph) {
        var surroundingLoops = new HashMap<Block, Loop>();

        graph.walkBlocksPostorder(new BlockWalker() {
            @Override
            public void visitBlock(Block block) {
                var loop = findSurroundingLoop(block);
                if (loop != null) {
                    assert loop.body.contains(block);
                    assert loops.values().stream().noneMatch(other -> other.body.contains(block)
                            && loop.body.contains(other.header));
                    surroundingLoops.put(block, loop);
                }
            }
        });

        return surroundingLoops;
    }

    /**
     * Returns the nearest surrounding loop of `block`, where `block` is not the
     * header of that loop.
     */
    private Loop findSurroundingLoop(Block block) {
        var dom = block.ptr;
        while ((dom = binding_irdom.get_Block_idom(dom)) != null) {
            var loop = loops.get(new Block(dom));
            if (loop != null && loop.body.contains(block)) {
                return loop;
            }
        }

        return null;
    }

    /**
     * For each loop, checks if it is closed, and invalidates the loop if not.
     * A loop is said to be closed if there are no jumps from within the loop
     * body to any block outside of the loop.
     */
    private void checkLoopsClosed(Graph graph, Map<Block, Loop> surroundingLoops) {
        graph.walkBlocks(new BlockWalker() {
            @Override
            public void visitBlock(Block block) {
                var loop = surroundingLoops.get(block);
                Util.forEachPredBlock(block, (predBlock, i) -> {
                    var predLoop = surroundingLoops.get(predBlock);
                    if (predLoop != null && !predLoop.equals(loop)
                            && !predLoop.getHeader().equals(block)) {
                        // this CF edge is a jump from within a loop to a block
                        // that is not in the body or header of that loop
                        predLoop.setInvalid();
                    }
                });
            }
        });
    }

    /**
     * Represents a loop in a Firm graph. If a loop is not valid (i.e. if
     * `isValid()` returns false), no guarantees are made concerning the
     * behavior of any other method.
     * 
     * A loop has a header and a body. The header is always exactly one block,
     * the body may have an arbitrary number of blocks. The header is never
     * part of the body.
     * 
     * Loops are considered valid if they fulfill every criteria listed below.
     * Any loop that does not fit this criteria is detected and marked as
     * invalid. Only valid loop loops are considered for loop unrolling.
     * 
     * - The loop header dominates every block in the body
     * - There must be exactly one Cond in the header
     * - Exactly one path of the header Cond must leave the loop
     * - The loop must be "closed", i.e. there are no jumps from within the loop
     * body that leave the loop (this also includes Returns in the loop body)
     */
    @ToString
    @EqualsAndHashCode(onlyExplicitlyIncluded = true)
    public static final class Loop {

        @Include
        @Getter
        private final Block header;
        private final boolean[] isBackEdge;

        @Getter
        private boolean valid = true;

        @Getter
        private int exitProj = -1;
        @Getter
        private Cond cond;

        @Getter
        private final Set<Block> body = new HashSet<>();

        private Loop(Block header) {
            this.header = header;
            this.isBackEdge = new boolean[header.getPredCount()];
        }

        public Graph getGraph() {
            return header.getGraph();
        }

        /**
         * Returns true if the given node is contained in the loop.
         */
        public boolean containsNode(Node node) {
            return switch (node.getOpCode()) {
                case iro_Block -> containsBlock((Block) node);
                default -> containsBlock((Block) node.getBlock());
            };
        }

        /**
         * Returns true if the given block is the header or part of the body of
         * the loop.
         */
        public boolean containsBlock(Block block) {
            return header.equals(block) || body.contains(block);
        }

        /**
         * Returns true if the body of this loop contains the given loop.
         */
        public boolean contains(Loop other) {
            return this.body.contains(other.getHeader());
        }

        /**
         * returns true if the i-th predecessor of the header is a back edge,
         * i.e. control flow along the edge comes from within the loop.
         */
        public boolean isBackEdge(int i) {
            return isBackEdge[i];
        }

        /**
         * Recomputes the body of the loop, which may for example have been
         * changed by an unrolling operation.
         */
        public void updateBody() {
            body.clear();

            getGraph().incVisited();
            header.markVisited();

            Util.forEachPredBlock(header, (predBlock, i) -> {
                if (isBackEdge(i)) {
                    collectBlocks(predBlock, false);
                }
            });
        }

        /**
         * Marks the control flow predecessor of the loop header at the given
         * index as back edge and adds the resulting blocks to the loop body.
         */
        private void addBackEdge(int idx) {
            assert idx < header.getPredCount();
            assert header.getPred(idx).getBlock().getOpCode() == ir_opcode.iro_Block;

            isBackEdge[idx] = true;

            getGraph().incVisited();
            header.markVisited();
            var tail = (Block) header.getPred(idx).getBlock();
            collectBlocks(tail, true);
        }

        /**
         * Walk all paths through the loop, starting at the given block and add
         * them to the loop body. If `setExit` is true, also set the `cond` and
         * `exitProj` information of the loop.
         */
        private void collectBlocks(Block tail, boolean setExit) {
            var worklist = new StackWorklist<Block>();
            if (!tail.visited()) {
                worklist.enqueue(tail);
            }

            while (!worklist.isEmpty()) {
                var block = worklist.dequeue();
                block.markVisited();
                body.add(block);
                Util.forEachPredBlock(block, (pred, j) -> {
                    if (!pred.visited()) {
                        worklist.enqueue(pred);
                    }

                    if (setExit && pred.equals(header)) {
                        setExit(block.getPred(j));
                    }
                });
            }
        }

        /**
         * Tries to set `cond` and `exitProj` under the assumption that `cfNode`
         * is a control flow node in the header, and does not exit the loop.
         */
        private void setExit(Node cfNode) {
            assert cfNode.getBlock().equals(header);

            if (cfNode.getOpCode() != ir_opcode.iro_Proj
                    || cfNode.getPred(0).getOpCode() != ir_opcode.iro_Cond) {
                setInvalid();
                return;
            }

            Cond cond = (Cond) cfNode.getPred(0);
            int exitProj = ((Proj) cfNode).getNum() == Cond.pnTrue
                    ? Cond.pnFalse
                    : Cond.pnTrue;

            if (this.exitProj == -1 && this.cond == null) {
                this.exitProj = exitProj;
                this.cond = cond;
            } else if (this.exitProj == exitProj && this.cond.equals(cond)) {
                // path already visited, carry on
            } else {
                // both control flow successors of header are part of loop
                setInvalid();
            }
        }

        private void setInvalid() {
            this.valid = false;
        }
    }

    /**
     * Represents a node in the hierarchy of loops. The children of a node are
     * those loops that are nested immediately within `loop`.
     */
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class LoopTree {

        @Getter
        private final Loop loop;
        @Getter
        private final List<LoopTree> children = new LinkedList<>();

        private void addChild(LoopTree child) {
            this.children.add(child);
        }
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private static final class LoopTreeBuilder {

        private final Map<Block, Loop> surroundingLoops;

        private final List<LoopTree> roots = new LinkedList<>();
        private final Map<Loop, LoopTree> trees = new HashMap<>();

        private static List<LoopTree> build(LoopAnalysis analysis,
                Map<Block, Loop> surroundingLoops) {
            var builder = new LoopTreeBuilder(surroundingLoops);

            for (var loop : analysis.loops.values()) {
                builder.addTreeIfAbsent(loop);
            }

            return builder.roots;
        }

        private LoopTree addTreeIfAbsent(Loop loop) {
            var existingTree = trees.get(loop);
            if (existingTree == null) {
                var newTree = new LoopTree(loop);
                trees.put(loop, newTree);

                var surroundingLoop = surroundingLoops.get(loop.getHeader());
                if (surroundingLoop == null) {
                    roots.add(newTree);
                } else {
                    addTreeIfAbsent(surroundingLoop).addChild(newTree);
                }

                return newTree;
            } else {
                return existingTree;
            }
        }
    }

    private static boolean isBackEdge(Block tail, Block head) {
        return binding_irdom.block_dominates(head.ptr, tail.ptr) != 0;
    }
}
