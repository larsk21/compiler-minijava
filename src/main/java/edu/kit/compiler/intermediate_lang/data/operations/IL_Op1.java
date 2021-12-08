package edu.kit.compiler.intermediate_lang.data.operations;

import edu.kit.compiler.intermediate_lang.data.IL_Operand;
import edu.kit.compiler.intermediate_lang.data.IL_Type;
import lombok.Getter;

@Getter
public class IL_Op1 extends IL_Op{

    private final IL_Operand firstOperand;

    public IL_Op1(IL_Operand firstOperand, IL_Operand res, IL_Type type) {
        super(res, type);
        this.firstOperand = firstOperand;
    }
}
