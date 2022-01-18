package edu.kit.compiler.optimizations;

import firm.Entity;
import firm.Graph;
import firm.Mode;
import firm.bindings.binding_irnode.ir_opcode;
import firm.nodes.Address;
import firm.nodes.Call;
import firm.nodes.Node;
import firm.nodes.Proj;
import firm.nodes.Tuple;
import lombok.AccessLevel;
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
}
