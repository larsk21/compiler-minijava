package edu.kit.compiler.codegen.pattern;

import java.util.Arrays;

import edu.kit.compiler.codegen.NodeRegisters;
import edu.kit.compiler.codegen.Operand;
import firm.nodes.Node;

public class RegisterPattern implements Pattern<OperandMatch<Operand.Register>> {
    @Override
    public OperandMatch<Operand.Register> match(Node node, NodeRegisters registers) {
        var register = registers.getRegister(node);
        if (register >= 0) {
            var predecessors = Arrays.asList(node);
            var operand = Operand.register(node.getMode(), register);
            return OperandMatch.some(operand, predecessors);
        } else {
            return OperandMatch.none();
        }
    }
}
