package edu.kit.compiler.intermediate_lang.data.operations;

import edu.kit.compiler.intermediate_lang.data.IL_Operand;
import edu.kit.compiler.intermediate_lang.data.IL_Type;
import lombok.ToString;

/**
 * allocates memory for an object with specific size
 */
@ToString
public class IL_OpAllocObj extends IL_Op{

    /**
     * size in bytes to allocate for this object
     */
    private final int objSize;
    public IL_OpAllocObj(int objsize, IL_Operand res, IL_Type type) {
        super(res, type);
        this.objSize = objsize;
    }

    @Override
    public String toString() {
        // instead of mmap call malloc
        String memoryAllocation = "\tli\t$9,\t%eax"
                .concat("\tli\t" + objSize * 8 + "\t%ebx")
                .concat("\tint $0x80");
        return memoryAllocation;
    }
}
