package edu.kit.compiler.intermediate_lang.data.operations;

import com.sun.jna.Pointer;
import edu.kit.compiler.intermediate_lang.data.IL_Operand;
import edu.kit.compiler.intermediate_lang.data.IL_Type;
import lombok.Getter;

import java.util.List;

@Getter
public class IL_OpCall extends IL_Op {

    /**
     * ptr needs to be a label that we can call
     */
    private final IL_Op ptr;
    private final IL_Operand receiver;
    private final List<IL_Operand> arguments;

    public IL_OpCall(IL_Op ptr, IL_Operand receiver, List<IL_Operand> arguments, IL_Operand res, IL_Type type) {
        super(res, type);
        this.ptr = ptr;
        this.receiver = receiver;
        this.arguments = arguments;
    }
}
