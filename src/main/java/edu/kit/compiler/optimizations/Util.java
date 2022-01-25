package edu.kit.compiler.optimizations;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import edu.kit.compiler.io.Worklist;

import firm.BlockWalker;
import firm.Entity;
import firm.Graph;
import firm.Mode;
import firm.bindings.binding_irnode;
import firm.bindings.binding_irnode.ir_opcode;
import firm.nodes.Address;
import firm.nodes.Call;
import firm.nodes.Block;
import firm.nodes.Node;
import firm.nodes.NodeVisitor;
import firm.nodes.Proj;
import firm.nodes.Tuple;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class Util {

    /**
     * If the predecessor of the given Proj node is a Tuple, exchange the node
     * with the corresponding predecessor of the tuple. This can be useful if
     * a node (e.g. a Div or Mod) has been optimized and exchanged with a tuple.
     */
    public static boolean skipTuple(Proj node) {
        if (node.getPred().getOpCode() == ir_opcode.iro_Tuple) {
            var tuple = (Tuple) node.getPred();
            if (node.getNum() < tuple.getPredCount()) {
                Graph.exchange(node, tuple.getPred(node.getNum()));
                return true;
            } else {
                throw new IllegalStateException(String.format(
                        "tuple (%s) has too few predecessors", tuple.getNr()));
            }
        } else {
            return false;
        }
    }

    /**
     * Replace the given node (which is assumed to either be a Div or a Mod)
     * with a tuple. The result and memory predecessors are set to the given
     * nodes. Control flow predecessors are set to Bad.
     */
    public static void exchangeDivOrMod(Node node, Node newNode, Node newMem) {
        assert node.getOpCode() == ir_opcode.iro_Div || node.getOpCode() == ir_opcode.iro_Mod;

        var bad = node.getGraph().newBad(Mode.getX());
        Graph.turnIntoTuple(node, new Node[] { newMem, newNode, bad, bad });
    }

    /**
     * Returns the function called by the given Call node. Only constant
     * function pointer (i.e. Address nodes) are allowed.
     */
    public static Entity getCallee(Call call) {
        if (call.getPtr().getOpCode() == ir_opcode.iro_Address) {
            return ((Address) call.getPtr()).getEntity();
        } else {
            throw new IllegalStateException("only constant function pointers allowed");
        }
    }

    /**
     * Checks if the given node is pinned in its current block.
     * 
     * Note: Necessary, because jFirm does not seem to provide any API for this.
     */
    public static boolean isPinned(Node node) {
        return binding_irnode.get_irn_pinned(node.ptr) != 0;
    }

    /**
     * Sets the pin-state for the given node.
     * 
     * Note: Necessary, because jFirm does not seem to provide any API for this.
     */
    public static void setPinned(Node node, boolean pinned) {
        binding_irnode.set_irn_pinned(node.ptr, pinned ? 1 : 0);
    }

    /**
     * Firm node visitor that inserts all visited nodes in the given Worklist
     * using the method enqueueInOrder.
     */
    @AllArgsConstructor
    public static class NodeWorklistFiller extends NodeVisitor.Default {

        @Getter
        private final Worklist<Node> worklist;

        @Override
        public void defaultVisit(Node node) {
            worklist.enqueueInOrder(node);
        }

    }

    /**
     * Firm block walker that inserts all visited blocks in the given Worklist
     * using the method enqueueInOrder.
     */
    @AllArgsConstructor
    public static class BlockWorklistFiller implements BlockWalker {

        @Getter
        private final Worklist<Block> worklist;

        @Override
        public void visitBlock(Block block) {
            worklist.enqueueInOrder(block);
        }

    }

    /**
     * Firm node visitor that inserts all visited nodes in the given List.
     * 
     * If the reverse flag is set, the nodes are inserted in the reverse order
     * of their visit.
     */
    @AllArgsConstructor
    @RequiredArgsConstructor
    public static class NodeListFiller extends NodeVisitor.Default {

        @Getter
        private final List<Node> list;
        @Getter
        private boolean reverse = false;

        @Override
        public void defaultVisit(Node node) {
            if (reverse) {
                list.add(0, node);
            } else {
                list.add(node);
            }
        }

    }

    /**
     * Firm block walker that inserts all visited blocks in the given List.
     * 
     * If the reverse flag is set, the blocks are inserted in the reverse order
     * of their visit.
     */
    @AllArgsConstructor
    @RequiredArgsConstructor
    public static class BlockListFiller implements BlockWalker {

        @Getter
        private final List<Block> list;
        @Getter
        private boolean reverse = false;

        @Override
        public void visitBlock(Block block) {
            if (reverse) {
                list.add(0, block);
            } else {
                list.add(block);
            }
        }

    }

    /**
     * Firm node visitor that maps blocks to the nodes contained in them.
     * 
     * The nodes in each list are in the order of their visit.
     */
    @RequiredArgsConstructor
    public static class BlockNodeMapper extends NodeVisitor.Default {

        private final Map<Block, List<Node>> blockNodes;

        @Override
        public void defaultVisit(Node node) {
            Block block = (Block) node.getBlock();

            blockNodes.putIfAbsent(block, new ArrayList<>());
            blockNodes.get(block).add(node);
        }

    }

}
