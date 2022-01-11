package edu.kit.compiler.register_allocation;

import edu.kit.compiler.intermediate_lang.Block;
import edu.kit.compiler.intermediate_lang.RegisterSize;

import java.util.List;

public interface RegisterAllocator {
    List<String> performAllocation(int nArgs, List<Block> input, RegisterSize[] sizes);

    default List<String> performAllocation(int nArgs, List<Block> input, List<RegisterSize> sizes) {
        return performAllocation(nArgs, input, sizes.toArray(new RegisterSize[0]));
    }
}
