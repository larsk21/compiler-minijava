package edu.kit.compiler.optimizations.unrolling;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.kit.compiler.optimizations.Util;
import edu.kit.compiler.optimizations.unrolling.LoopAnalysis.Loop;
import firm.Graph;
import firm.Mode;
import firm.bindings.binding_irnode.ir_opcode;
import firm.nodes.Block;
import firm.nodes.Cond;
import firm.nodes.Node;
import firm.nodes.Phi;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

/**
 * Implements functionality to fully or partially unroll loops.
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class LoopUnroller {

    private final Map<Block, List<Node>> nodesPerBlock;

    private final Graph graph;
    private final Loop loop;
    private final int nCopies;
    private final boolean isFull;

    private final HashMap<Node, NodeVec> copies = new HashMap<>();

    /**
     * Unrolls `loop` by the given factor. The caller must ensure that the loop
     * can actually be unrolled by the given factor. The `isFull` flag must be
     * true iff the loop will be fully unrolled. The body of the given Loop
     * is guaranteed to be updated if necessary. Returns true if the graph has
     * been modified.
     * 
     * In the following H and B are the original header and body of the loop,
     * while H_n and B_n are their n-th copy.
     * 
     * 1. If `isFull` is true, i.e. the loop is to be fully unrolled, the header
     * and body of the loop are copied `factor` times. The original header is
     * executed exactly once, and its body is never executed.
     * 
     * The control flow looks something like the following:
     * -> H_1 -> B_1 -> ... -> H_N -> B_N -> H ->
     * 
     * 2. If `isFull` is false, i.e. the loopy is to be partially unrolled, the
     * header and body of the loop are only copied `factor-1` times. The
     * original header will continue to be used as loop header of the new loop.
     * 
     * The control flow looks something like the following:
     *    ↓─────<──────<──────<──────<──────<─────┐
     * -> H -> B -> H_1 -> B_1 -> ... -> H_N-1 -> B_N-1
     *    ↓
     */
    public static boolean unroll(Loop loop, int factor, boolean isFull,
            Map<Block, List<Node>> nodesPerBlock) {
        if (factor > 1 || (factor == 1 && isFull)) {
            var unroller = new LoopUnroller(nodesPerBlock, loop.getGraph(),
                    loop, isFull ? factor : factor - 1, isFull);
            unroller.unroll();
            loop.updateBody();
            return true;
        } else {
            return false;
        }
    }

    /**
     * Complete skips the body of the loop. The header is executed once, after
     * which the program unconditionally leaves the loop.
     */
    public static void skipLoop(Loop loop, Map<Block, List<Node>> nodesPerBlock) {
        var unroller = new LoopUnroller(nodesPerBlock, loop.getGraph(), loop, 0, true);
        unroller.fixHeaderCond(loop.getCond(), true);
    }

    private void unroll() {
        var header = loop.getHeader();
        var headerCopy = copyLoopNodes();

        fixCopyHeader(header, headerCopy);
        fixOriginalHeader(header, headerCopy);
        fixHeaderPhis(header);
    }

    private NodeVec copyLoopNodes() {
        var header = loop.getHeader();
        var headerCopy = NodeVec.copy(header, nCopies);
        copies.put(header, headerCopy);

        for (var node : nodesPerBlock.get(loop.getHeader())) {
            copyNode(node);
        }

        for (var block : loop.getBody()) {
            for (var node : nodesPerBlock.get(block)) {
                copyNode(node);
            }
        }

        return headerCopy;
    }

    /**
     * Correctly sets the control flow predecessors of the loop header copies.
     * Also replaces the conditional jumps that exit the header with
     * unconditional jumps to the loop body.
     */
    private void fixCopyHeader(Block header, NodeVec headerCopy) {
        Util.forEachPredBlock(header, (predBlock, i) -> {
            var bad = graph.newBad(Mode.getX());
            if (loop.isBackEdge(i)) {

                var pred = header.getPred(i);
                var predCopy = copies.get(pred);

                headerCopy.get(0).setPred(i, isFull ? bad : pred);
                for (int j = 1; j < nCopies; ++j) {
                    headerCopy.get(j).setPred(i, predCopy.get(j - 1));
                }
            } else {
                for (int j = isFull ? 1 : 0; j < nCopies; ++j) {
                    headerCopy.get(j).setPred(i, bad);
                }
            }
        });

        for (int i = 0; i < nCopies; ++i) {
            fixHeaderCond(copies.get(loop.getCond()).get(i), false);
        }
    }

    /**
     * Sets the control flow predecessors of the original loop. If the loops is
     * to be fully unrolled, replaces the conditional jump in the header with
     * an unconditional jump that exits the loop
     */
    private void fixOriginalHeader(Block header, NodeVec headerCopy) {
        Util.forEachPredBlock(header, (predBlock, i) -> {
            if (loop.isBackEdge(i)) {
                var predCopy = copies.get(header.getPred(i));
                assert predCopy.get(nCopies - 1) != null;
                header.setPred(i, predCopy.get(nCopies - 1));
            } else if (isFull) {
                header.setPred(i, graph.newBad(Mode.getX()));
            }
        });

        if (isFull) {
            fixHeaderCond(loop.getCond(), true);
        }
    }

    /**
     * Turns the given header Cond into an unconditional jump. The jump will
     * exit the loop depending on the value of `exitLoop`.
     */
    private void fixHeaderCond(Node cond, boolean exitLoop) {
        assert cond.getOpCode() == ir_opcode.iro_Cond;
        assert loop.getExitProj() >= 0;
        assert loop.getExitProj() < Cond.pnMax;

        var bad = graph.newBad(Mode.getX());
        var jmp = graph.newJmp(cond.getBlock());

        if ((loop.getExitProj() == Cond.pnTrue) == exitLoop) {
            Graph.turnIntoTuple(cond, new Node[] { bad, jmp });
        } else {
            Graph.turnIntoTuple(cond, new Node[] { jmp, bad });
        }
    }

    /**
     * Correctly sets predecessors of Phi nodes in the loop header and its
     * copies.
     */
    private void fixHeaderPhis(Block header) {
        for (var node : nodesPerBlock.get(header)) {
            if (node.getOpCode() == ir_opcode.iro_Phi) {
                fixHeaderPhi((Phi) node);
            }
        }
    }

    private void fixHeaderPhi(Phi phi) {
        for (int i = 0; i < phi.getPredCount(); ++i) {
            if (loop.isBackEdge(i)) {
                var phiCopy = copies.get(phi);
                var predCopy = copies.get(phi.getPred(i));

                phiCopy.get(0).setPred(i, phi.getPred(i));
                for (int j = 1; j < nCopies; ++j) {
                    phiCopy.get(j).setPred(i, predCopy.get(j - 1));
                }
                phi.setPred(i, predCopy.get(nCopies - 1));
            }
        }
    }

    private NodeVec copyBlock(Block block) {
        assert loop.containsBlock(block);

        var existing = copies.get(block);
        if (existing == null) {
            var blockCopies = NodeVec.copy(block, nCopies);
            copies.put(block, blockCopies);

            for (int i = 0; i < block.getPredCount(); ++i) {
                var pred = block.getPred(i);
                var predCopy = copyNode(pred);
                blockCopies.setPreds(i, predCopy);
            }

            return blockCopies;
        } else {
            return existing;
        }
    }

    private NodeVec copyNode(Node node) {
        if (loop.containsNode(node)) {
            var existing = copies.get(node);
            if (existing == null) {
                var nodeCopy = NodeVec.copy(node, nCopies);
                copies.put(node, nodeCopy);

                var blockCopy = copyBlock((Block) node.getBlock());
                nodeCopy.setBlocks(blockCopy);

                for (int i = 0; i < node.getPredCount(); ++i) {
                    var predCopy = copyNode(node.getPred(i));
                    nodeCopy.setPreds(i, predCopy);
                }

                return nodeCopy;
            } else {
                return existing;
            }
        } else {
            return NodeVec.of(node, nCopies);
        }
    }

    /**
     * A fixed size vector of nodes. Used to do the same operation on multiple
     * nodes at once, and to create multiple copies of a node at once.
     */
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    @ToString
    private static final class NodeVec {

        @Getter
        private final Node[] nodes;

        public static NodeVec of(Node node, int numCopies) {
            var nodes = new Node[numCopies];
            Arrays.fill(nodes, node);
            return new NodeVec(nodes);
        }

        public static NodeVec copy(Node node, int numCopies) {
            var copies = new Node[numCopies];
            for (int i = 0; i < numCopies; ++i) {
                copies[i] = node.exactCopy();
            }

            return new NodeVec(copies);
        }

        public Node get(int i) {
            return nodes[i];
        }

        public void setPreds(int n, NodeVec preds) {
            assert nodes.length == preds.nodes.length;

            for (int i = 0; i < nodes.length; ++i) {
                nodes[i].setPred(n, preds.nodes[i]);
            }
        }

        public void setBlocks(NodeVec blocks) {
            assert nodes.length == blocks.nodes.length;

            for (int i = 0; i < nodes.length; ++i) {
                nodes[i].setBlock(blocks.nodes[i]);
            }
        }
    }
}
