package edu.kit.compiler.codegen.pattern;

import edu.kit.compiler.codegen.Operand;
import edu.kit.compiler.codegen.Operand.Immediate;

import java.util.Collections;

import edu.kit.compiler.codegen.NodeRegisters;
import firm.bindings.binding_irnode.ir_opcode;
import firm.nodes.Const;
import firm.nodes.Node;

public class ImmediatePattern implements Pattern<OperandMatch<Immediate>> {
    @Override
    public OperandMatch<Immediate> match(Node node, NodeRegisters registers) {
        if (node.getOpCode() == ir_opcode.iro_Const) {
            var value = ((Const) node).getTarval();
            var operand = Operand.immediate(value);
            return OperandMatch.some(operand, Collections.emptyList());
        } else {
            return OperandMatch.none();
        }
    }
}
