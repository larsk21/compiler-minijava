package edu.kit.compiler.intermediate_lang.data;

public enum IL_Cond {
    /**
     * less than, greater than, less equal, greater equal, equal
     */
    LT,
    GT,
    LE,
    GE,
    EQ,
    /**
     * encodes always jump
     */
    AL,
}
