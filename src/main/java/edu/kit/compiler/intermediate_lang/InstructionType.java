package edu.kit.compiler.intermediate_lang;

public enum InstructionType {
    // all instructions that don't need special casing, i.e. most instructions
    GENERAL,

    // div and mod have special register requirements
    DIV,
    MOD,

    // function call / return
    CALL,
    RET,
}
