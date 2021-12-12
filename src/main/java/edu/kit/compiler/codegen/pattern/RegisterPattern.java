package edu.kit.compiler.codegen.pattern;

import edu.kit.compiler.codegen.Operand;
import edu.kit.compiler.codegen.NodeRegisters;
import firm.nodes.Node;

public class RegisterPattern implements Pattern<OperandMatch<Operand.Register>> {
    @Override
    public OperandMatch<Operand.Register> match(Node node, NodeRegisters registers) {
        var register = registers.getRegister(node);
        if (register >= 0) {
            return OperandMatch.some(Operand.register(node.getMode(), register));
        } else {
            return OperandMatch.none();
        }
    }
}
