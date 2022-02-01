package edu.kit.compiler.optimizations;

import firm.bindings.binding_irnode.ir_opcode;
import firm.nodes.Node;

/**
 * This class contains an estimate of the cost (in abstract CPU cycles) of Firm
 * nodes.
 */
public final class NodeCosts {

    private NodeCosts() { }

    /**
     * Get an estimate of the cost of the given node without considering
     * surrounding nodes. More advanced instruction selection is not taken into
     * account.
     */
    public static int getCost(Node node) {
        return getCost(node.getOpCode());
    }

    /**
     * Get an estimate of the cost of a node with the given opcode without
     * considering additional information for that node.
     */
    public static int getCost(ir_opcode opcode) {
        switch (opcode) {
            case iro_Const:
                return 0;
            case iro_Address:
                return 0;
            case iro_Proj:
                return 0;
            case iro_Tuple:
                return 0;
            case iro_NoMem:
                return 0;
            case iro_Mul:
                return 4;
            case iro_Mulh:
                return 4;
            case iro_Div:
                return 25;
            case iro_Mod:
                return 25;
            case iro_Alloc:
                return 50;
            case iro_Load:
                return 50;
            case iro_Store:
                return 50;
            case iro_Call:
                return 2;
            case iro_Return:
                return 2;
            default:
                return 1;
        }
    }

    /**
     * Return true if the given estimated cost of a node is cheaper than the
     * estimated cost of a load from memory.
     */
    public static boolean cheaperThanLoad(int nodeCost) {
        return nodeCost < getCost(ir_opcode.iro_Load);
    }

}
