package edu.kit.compiler.optimizations.unrolling;

import static firm.bindings.binding_irgraph.ir_graph_properties_t.IR_GRAPH_PROPERTY_CONSISTENT_DOMINANCE;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.kit.compiler.io.CommonUtil;
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
import lombok.Setter;
import lombok.ToString;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class LoopAnalysis {

    /**
     * A map of loop headers to Loops
     */
    private final Map<Block, Loop> loops = new HashMap<>();

    public static LoopAnalysis apply(Graph graph) {
        graph.assureProperties(IR_GRAPH_PROPERTY_CONSISTENT_DOMINANCE);
        var analysis = new LoopAnalysis();
        analysis.collectLoops(graph);
        return analysis;
    }

    public List<Loop> getInnermostLoops() {
        var innerLoops = new LinkedList<Loop>();
        for (var loop : loops.values()) {
            if (loop.getBody().stream().noneMatch(loops::containsKey)) {
                innerLoops.add(loop);
            }
        }
        return innerLoops;
    }

    private void collectLoops(Graph graph) {
        graph.walkBlocksPostorder(new BlockWalker() {
            @Override
            public void visitBlock(Block block) {
                Util.forEachPredBlock(block, (pred, i) -> {
                    if (isBackEdge(pred, block)) {
                        Block loopHead = block, loopTail = pred;

                        // It is possible for multiple backedges to point to the
                        // same loop header
                        loops.computeIfAbsent(loopHead, Loop::new)
                                .addBackEdge(loopTail);
                    }
                });
            }
        });

        for (var loop : loops.values()) {
            if (loop.cond == null || loop.exitProj == -1) {
                loop.setValid(false);
            }
        }
    }

    @RequiredArgsConstructor
    @ToString
    public static final class Loop {

        @Getter
        private final Block header;

        @Getter
        @Setter
        private boolean valid = true;
        @Getter
        @Setter
        private int exitProj = -1;
        @Getter
        @Setter
        private Cond cond;

        @Getter
        private final Set<Block> body = new HashSet<>();
        @Getter
        private final Set<Block> backEdges = new HashSet<>();

        public Graph getGraph() {
            return header.getGraph();
        }

        public boolean containsNode(Node node) {
            return switch (node.getOpCode()) {
                case iro_Block -> containsBlock((Block) node);
                default -> containsBlock((Block) node.getBlock());
            };
        }

        public boolean containsBlock(Block block) {
            return header.equals(block) || body.contains(block);
        }

        public boolean isBackEdge(Block tail) {
            return backEdges.contains(tail);
        }

        public boolean[] computeBackEdges() {
            var isBackEdge = new boolean[getHeader().getPredCount()];
            Util.forEachPredBlock(getHeader(), (pred, i) -> {
                if (isBackEdge(pred)) {
                    isBackEdge[i] = true;
                }
            });

            return isBackEdge;
        }

        public void addBackEdge(Block tail) {
            assert CommonUtil.stream(header.getPreds())
                    .map(Node::getBlock)
                    .anyMatch(tail::equals);

            header.getGraph().incVisited();
            header.markVisited();
            backEdges.add(tail);

            var worklist = new StackWorklist<Block>();
            if (!tail.visited()) {
                worklist.enqueue(tail);
            }

            while (!worklist.isEmpty()) {
                var block = worklist.dequeue();
                block.markVisited();
                body.add(block);
                Util.forEachPredBlock(block, (pred, i) -> {
                    if (!pred.visited()) {
                        worklist.enqueue(pred);
                    }

                    if (pred.equals(header)) {
                        setExit(block, block.getPred(i));
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
                exitProj = proj.getNum();
            } else {
                // loop header has unsupported control flow, maybe infinite loop?
                setValid(false);
                return;
            }

            if (this.exitProj == -1 && this.cond == null) {
                this.exitProj = exitProj;
                this.cond = cond;
            } else if (this.exitProj == exitProj && this.cond.equals(cond)) {
                // path already visited, carry on
            } else {
                // both control flow successors of header are part of loop
                setValid(false);
            }
        }
    }

    private static boolean isBackEdge(Block tail, Block head) {
        return binding_irdom.block_dominates(head.ptr, tail.ptr) != 0;
    }
}
