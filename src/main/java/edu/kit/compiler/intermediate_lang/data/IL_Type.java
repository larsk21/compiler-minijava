package edu.kit.compiler.intermediate_lang.data;

/**
 * assembly type of this operation
 */
public enum IL_Type {
    /**
     * first operation of each block
     */
    IL_LABEL,
    /**
     * create standard stack frame
     */
    IL_STD_ENTRY,
    /**
     * returns the control flow, result is in eax
     */
    IL_RETURN,
    /**
     * moves between two operands
     */
    IL_MOVE,
    /**
     * cmp operation
     */
    IL_CMP,
    /**
     * branching operation
     */
    IL_BRANCH,
    /**
     * arithmetic expressions
     */
    IL_ADD,
    IL_SUB,
    IL_MUL,
    IL_DIV,
    IL_MOD,

    /**
     * call operation
     */
    IL_CALL,
}
