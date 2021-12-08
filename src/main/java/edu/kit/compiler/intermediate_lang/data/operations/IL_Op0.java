package edu.kit.compiler.intermediate_lang.data.operations;

import edu.kit.compiler.intermediate_lang.data.IL_Operand;
import edu.kit.compiler.intermediate_lang.data.IL_Type;
import lombok.Getter;

@Getter
public class IL_Op0 extends IL_Op{
    public IL_Op0(IL_Operand res, IL_Type type) {
        super(res, type);
    }
}
