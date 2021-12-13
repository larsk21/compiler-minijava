package edu.kit.compiler.codegen.pattern;

import java.util.Arrays;

import edu.kit.compiler.codegen.NodeRegisters;
import edu.kit.compiler.codegen.Operand;
import edu.kit.compiler.codegen.Operand.Memory;
import firm.Mode;
import firm.nodes.Node;

public class MemoryPattern implements Pattern<OperandMatch<Operand.Memory>> {

    @Override
    public OperandMatch<Memory> match(Node node, NodeRegisters registers) {
        var register = registers.getRegister(node);
        if (node.getMode().equals(Mode.getP()) && register >= 0) {
            var operand = Operand.register(node.getMode(), register);
            var predecessors = Arrays.asList(node);
            return OperandMatch.some(Operand.memory(operand), predecessors);
        } else {
            return OperandMatch.none();
        }
    }
}
