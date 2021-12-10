package edu.kit.compiler.intermediate_lang;

import edu.kit.compiler.intermediate_lang.data.operations.*;

public interface AsmVisitor {
    public String visit(IL_Op0 op);
    public String visit(IL_Op1 op);
    public String visit(IL_Op2 op);
    public String visit(IL_Op3 op);
    public String visit(IL_OpAllocObj op);
    public String visit(IL_OpBranch op);
    public String visit(IL_OpCall op);
    public String visit(IL_OpLabel op);
}
