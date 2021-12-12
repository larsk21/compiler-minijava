package edu.kit.compiler.register_allocation;

import edu.kit.compiler.data.ast_nodes.MethodNode;
import edu.kit.compiler.intermediate_lang.Instruction;
import edu.kit.compiler.intermediate_lang.RegisterSize;

import java.util.List;

public interface RegisterAllocator {
    List<String> performAllocation(int nArgs, List<Instruction> input, RegisterSize[] sizes);

    default List<String> performAllocation(MethodNode node, List<Instruction> input, RegisterSize[] sizes) {
        return performAllocation(node.getParameters().size(), input, sizes);
    }
}
