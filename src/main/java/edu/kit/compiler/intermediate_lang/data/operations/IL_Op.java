package edu.kit.compiler.intermediate_lang.data.operations;

import edu.kit.compiler.intermediate_lang.data.IL_Operand;
import edu.kit.compiler.intermediate_lang.data.IL_Type;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class IL_Op {

    private static int id_gen = 0;
    private final IL_Operand res;
    private final IL_Type type;
    private final int id;

    public IL_Op(IL_Operand res, IL_Type type) {
        this.res = res;
        this.type = type;
        this.id = id_gen++;
    }

}
