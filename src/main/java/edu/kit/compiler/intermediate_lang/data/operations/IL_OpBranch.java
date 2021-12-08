package edu.kit.compiler.intermediate_lang.data.operations;

import edu.kit.compiler.intermediate_lang.data.IL_Cond;
import edu.kit.compiler.intermediate_lang.data.IL_Operand;
import edu.kit.compiler.intermediate_lang.data.IL_Type;
import firm.nodes.Block;
import lombok.Getter;

/**
 * branching operation
 */
@Getter
public class IL_OpBranch extends IL_Op {

    private IL_Cond cond;
    private Block nextBlock;

    public IL_OpBranch(IL_Cond condititon, Block jmpTarget, IL_Operand res, IL_Type type) {
        super(res, type);
        this.cond = condititon;
        this.nextBlock = jmpTarget;
    }

}
