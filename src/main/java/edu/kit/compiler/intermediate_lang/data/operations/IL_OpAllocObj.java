package edu.kit.compiler.intermediate_lang.data.operations;

import edu.kit.compiler.intermediate_lang.data.IL_Operand;
import edu.kit.compiler.intermediate_lang.data.IL_Type;

public class IL_OpAllocObj extends IL_Op{

    private int objSize;
    public IL_OpAllocObj(int objsize, IL_Operand res, IL_Type type) {
        super(res, type);
        this.objSize = objsize;
    }
}
