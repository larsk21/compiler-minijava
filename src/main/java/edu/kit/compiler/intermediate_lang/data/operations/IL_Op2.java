package edu.kit.compiler.intermediate_lang.data.operations;

import edu.kit.compiler.intermediate_lang.data.IL_Operand;
import edu.kit.compiler.intermediate_lang.data.IL_Type;
import lombok.Getter;

@Getter
public class IL_Op2 extends IL_Op{

    private final IL_Operand firstOperand;
    private final IL_Operand secondOperand;

    public IL_Op2(IL_Operand firstOperand, IL_Operand secondOperand, IL_Operand res, IL_Type type) {
        super(res, type);
        this.firstOperand = firstOperand;
        this.secondOperand = secondOperand;
    }
}
