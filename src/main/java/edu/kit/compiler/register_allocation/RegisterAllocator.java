package edu.kit.compiler.register_allocation;

import edu.kit.compiler.data.ast_nodes.MethodNode;
import edu.kit.compiler.intermediate_lang.Block;
import edu.kit.compiler.intermediate_lang.Instruction;
import edu.kit.compiler.intermediate_lang.RegisterSize;

import java.util.List;

public interface RegisterAllocator {
    List<String> performAllocation(int nArgs, List<Block> input, RegisterSize[] sizes);
}
