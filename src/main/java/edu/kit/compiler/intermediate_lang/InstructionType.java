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

    // TODO:
    //  Possibly, we might want to special case moves between two registers,
    //  e.g. if they turn out to be the same hardware register (is this valid in x86?)
    REGISTER_MOVE
}
