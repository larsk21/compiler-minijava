package edu.kit.compiler.optimizations.unrolling;

import static firm.bindings.binding_irgraph.ir_graph_properties_t.IR_GRAPH_PROPERTY_CONSISTENT_DOMINANCE;

import java.util.ArrayList;
import java.util.Comparator;
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

    public static LoopAnalysis apply(Graph graph) {
        graph.assureProperties(IR_GRAPH_PROPERTY_CONSISTENT_DOMINANCE);

        var analysis = new LoopAnalysis();
        try {
            analysis.collectLoops(graph);
        } catch (GraphNotReducibleException e) {
            assert false : "Graphs in MiniJava should always be reducible";
            analysis.loops.clear();
        }
        return analysis;
    }

    /**
     * Returns a hierarchy of the loops found by this analysis.
     */
    public List<LoopTree> getForestOfLoops() {
        return LoopTree.build(this);
    }

    /**
     * An exception to indicate that a graph is not reducible. See "Compilers
     * Principles, Techniques, and Tools" (Aho et al., Chapter 9.6.4).
     */
    private static final class GraphNotReducibleException extends RuntimeException {
    }

    private void collectLoops(Graph graph) {
        graph.walkBlocksPostorder(new BlockWalker() {
            @Override
            public void visitBlock(Block block) {
                Util.forEachPredBlock(block, (predBlock, i) -> {
                    if (isBackEdge(predBlock, block)) {
                        if (!isRetreatingEdge(predBlock, block)) {
                            throw new GraphNotReducibleException();
                        }

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
     * Represents a node in the hierarchy of loops. The children of a node are
     * those loops that are nested immediately within `loop`.
     */
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class LoopTree {

        @Getter
        private final Loop loop;
        @Getter
        private final List<LoopTree> children = new LinkedList<>();

        private static List<LoopTree> build(LoopAnalysis analysis) {
            var sortedLoops = new ArrayList<>(analysis.loops.values());
            sortedLoops.sort(Comparator.reverseOrder());

            var trees = new HashMap<Block, LoopTree>();
            var roots = new LinkedList<LoopTree>();

            for (var loop : sortedLoops) {
                insertTree(roots, trees, loop);
            }

            return roots;
        }

        private static void insertTree(List<LoopTree> roots,
                Map<Block, LoopTree> trees, Loop loop) {
            var dom = loop.getHeader().ptr;

            while ((dom = binding_irdom.get_Block_idom(dom)) != null) {
                var tree = trees.get(new Block(dom));
                if (tree != null && tree.getLoop().contains(loop)) {
                    var child = new LoopTree(loop);
                    tree.children.add(child);
                    trees.put(loop.getHeader(), child);
                    return;
                }
            }

            var root = new LoopTree(loop);
            roots.add(root);
            trees.put(loop.getHeader(), root);
        }
    }

    /**
     * Represents a loop in a Firm graph. If a loop is not valid (i.e. if
     * `isValid()` returns false), no guarantees are made concerning the
     * behavior of any other method.
     * 
     * A loop has a header and a body. The header is always exactly one block,
     * the body may have an arbitrary number of blocks. The header is never
     * part of the body.
     */
    @ToString
    public static final class Loop implements Comparable<Loop> {

        @Getter
        private final Block header;
        private final boolean[] isBackEdge;

        /**
         * A loop is valid if it is a natural loop, i.e. the loop header must
         * dominate every block of the loop body. Additionally, the header must
         * contain a Cond node where exactly one case exists the loop.
         */
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

        @Override
        public int compareTo(Loop other) {
            if (other.containsBlock(this.header)) {
                return -1;
            } else if (this.containsBlock(other.header)) {
                return 1;
            } else {
                return 0;
            }
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
         * Mark the control flow predecessor of the loop header at the given
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
                        setExit(block, block.getPred(j));
                    }
                });
            }
        }

        private void setExit(Block block, Node pred) {
            int exitProj;
            Cond cond;

            if (pred.getOpCode() == ir_opcode.iro_Proj
                    && pred.getPred(0).getOpCode() == ir_opcode.iro_Cond) {
                var proj = (Proj) pred;
                cond = (Cond) proj.getPred();
                exitProj = proj.getNum() == Cond.pnTrue ? Cond.pnFalse : Cond.pnTrue;
            } else {
                // loop header has unsupported control flow, maybe infinite loop?
                setInvalid();
                return;
            }

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

    private static boolean isBackEdge(Block tail, Block head) {
        return binding_irdom.block_dominates(head.ptr, tail.ptr) != 0;
    }

    private static boolean isRetreatingEdge(Block tail, Block head) {
        var dom = tail.ptr;
        while (dom != null && !dom.equals(head.ptr)) {
            dom = binding_irdom.get_Block_idom(dom);
        }

        return new Block(dom).equals(head);
    }
}
