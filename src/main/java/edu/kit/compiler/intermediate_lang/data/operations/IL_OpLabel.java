package edu.kit.compiler.intermediate_lang.data.operations;

import edu.kit.compiler.intermediate_lang.data.IL_Operand;
import edu.kit.compiler.intermediate_lang.data.IL_Type;
import lombok.Getter;

@Getter
public class IL_OpLabel extends IL_Op{
    /**
     * label index into a string table
     */
    private final int label;

    public IL_OpLabel(int label, IL_Operand res, IL_Type type) {
        super(res, type);
        this.label = label;
    }
}
