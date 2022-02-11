package edu.kit.compiler.optimizations;

import java.util.*;
import java.util.function.ObjIntConsumer;

import com.sun.jna.Pointer;

import edu.kit.compiler.io.CommonUtil;
import edu.kit.compiler.io.Worklist;
import firm.*;
import firm.bindings.binding_irdom;
import firm.bindings.binding_irnode;
import firm.bindings.binding_irnode.ir_opcode;
import firm.nodes.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class Util {

    public static Node[] iterableToArray(Iterable<Node> iterable) {
        List<Node> list = new ArrayList<>();
        for(var entry : iterable) {
            list.add(entry);
        }
        return list.toArray(new Node[0]);
    }
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
     * Find the deepest common dominator of the given blocks.
     * 
     * Note: A block also dominates itself.
     */
    public static Block findDeepestCommonDominator(Iterable<Block> blocks) {
        return CommonUtil.stream(blocks)
            .reduce((dominator, block) -> {
                Pointer ptr = binding_irdom.ir_deepest_common_dominator(dominator.ptr, block.ptr);
                return (Block) Node.createWrapper(ptr);
            })
            .orElse(null);
    }

    /**
     * Get the immediate dominator of the given block.
     */
    public static Block getImmediateDominator(Block block) {
        return (Block) Node.createWrapper(binding_irdom.get_Block_idom(block.ptr));
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

            blockNodes.computeIfAbsent(block, item -> new ArrayList<Node>()).add(node);
        }

    }

    /**
     * Firm node visitor that maps a block to its successors.
     */
    @RequiredArgsConstructor
    public static class BlockSuccessorMapper implements BlockWalker {

        private final Map<Block, Set<Block>> successors;

        @Override
        public void visitBlock(Block block) {
            successors.computeIfAbsent(block, item -> new HashSet<>());

            for (Node predNode : block.getPreds()) {
                Block pred = (Block) predNode.getBlock();

                successors.computeIfAbsent(pred, item -> new HashSet<>()).add(block);
            }
        }

    }

    /**
     * Call `func` for each non-Bad direct control flow predecessor of `block`.
     */
    public static void forEachPredBlock(Block block, ObjIntConsumer<Block> func) {
        for (int i = 0; i < block.getPredCount(); ++i) {
            var pred = block.getPred(i);
            if (pred.getOpCode() != ir_opcode.iro_Bad) {
                func.accept((Block) pred.getBlock(), i);
            }
        } 
    }

    /**
     * Returns the number of arguments of the given function.
     */
    public static int getNArgs(Entity func) {
        var type = (MethodType) func.getType();
        return type.getNParams();
    }
}
