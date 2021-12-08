package edu.kit.compiler.intermediate_lang.data;

/**
 * il representation of where an operand could be stored
 */
public enum IL_Repr {
    VIRTUAL,
    PHYSICAL,
    STACK,
    CONST,
    MEM,
}
