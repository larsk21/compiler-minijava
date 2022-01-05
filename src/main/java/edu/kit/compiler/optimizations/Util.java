package edu.kit.compiler.optimizations;

import firm.BackEdges;
import firm.Graph;
import firm.Mode;
import firm.bindings.binding_irnode.ir_opcode;
import firm.nodes.Conv;
import firm.nodes.Div;
import firm.nodes.Mod;
import firm.nodes.Node;
import firm.nodes.Proj;
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
     * Replace the given node (which is assumed to be a Div or Mod). The result
     * projection is replaced with `newNode`, the memory projection is replaced
     * with `newMem`.
     */
    public static void exchangeDivOrMod(Node node, Node newNode, Node newMem) {
        assert node.getOpCode() == ir_opcode.iro_Div || node.getOpCode() == ir_opcode.iro_Mod;

        var projMem = node.getOpCode() == ir_opcode.iro_Div ? Div.pnM : Mod.pnM;
        var projRes = node.getOpCode() == ir_opcode.iro_Div ? Div.pnRes : Mod.pnRes;

        for (var edge : BackEdges.getOuts(node)) {
            var projNum = ((Proj) edge.node).getNum();

            if (projNum == projMem) {
                Graph.exchange(edge.node, newMem);
            } else if (projNum == projRes) {
                Graph.exchange(edge.node, newNode);
            } else {
                throw new UnsupportedOperationException(
                        "Div control flow projections not supported");
            }
        }
    }
}
