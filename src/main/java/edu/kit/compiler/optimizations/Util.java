package edu.kit.compiler.optimizations;

import firm.Graph;
import firm.Mode;
import firm.bindings.binding_irnode.ir_opcode;
import firm.nodes.Conv;
import firm.nodes.Node;
import firm.nodes.Proj;
import firm.nodes.Tuple;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class Util {

    /**
     * Try to contracted nested Conv nodes. Currently only Is -> Ls -> Is is
     * being removed, as we currently don't generate any other combinations
     * that could be removed.
     * 
     * @return true if the Firm graph has changes, false otherwise
     */
    public static boolean contractConv(Conv node) {
        if (node.getOp().getOpCode() == ir_opcode.iro_Conv) {
            // remove casts introduced by 64 bit division where possible
            var op = (Conv) node.getOp();
            if (node.getMode().equals(Mode.getIs())
                    && op.getMode().equals(Mode.getLs())
                    && op.getOp().getMode().equals(Mode.getIs())) {
                Graph.exchange(node, op.getOp());
                return true;
            }
        }
        return false;
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
}
