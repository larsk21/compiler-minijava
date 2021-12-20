package edu.kit.compiler.intermediate_lang;

public enum InstructionType {
    // all instructions that don't need special casing, i.e. most instructions
    GENERAL,

    // div and mod have special register requirements
    DIV,
    MOD,

    // signed and unsigned move between two registers (which might be of different size)
    MOV_S,
    MOV_U,

    // function call / return
    CALL,
    RET,
}
