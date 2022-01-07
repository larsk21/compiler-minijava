package edu.kit.compiler.intermediate_lang;

import lombok.Getter;

/**
 * Represents a concrete x86 register.
 */
public enum Register {
    RAX("%al", "%ax", "%eax", "%rax"),
    RBX("%bl", "%bx", "%ebx", "%rbx"),
    RCX("%cl", "%cx", "%ecx", "%rcx"),
    RDX("%dl", "%dx", "%edx", "%rdx"),
    RSI("%sil", "%si", "%esi", "%rsi"),
    RDI("%dil", "%di", "%edi", "%rdi"),
    RBP("%bpl", "%bp", "%ebp", "%rbp"),
    RSP("%spl", "%sp", "%esp", "%rsp"),
    R8("%r8b", "%r8w", "%r8d", "%r8"),
    R9("%r9b", "%r9w", "%r9d", "%r9"),
    R10("%r10b", "%r10w", "%r10d", "%r10"),
    R11("%r11b", "%r11w", "%r11d", "%r11"),
    R12("%r12b", "%r12w", "%r12d", "%r12"),
    R13("%r13b", "%r13w", "%r13d", "%r13"),
    R14("%r14b", "%r14w", "%r14d", "%r14"),
    R15("%r15b", "%r15w", "%r15d", "%r15");

    @Getter
    private String asByte;
    @Getter
    private String asWord;
    @Getter
    private String asDouble;
    @Getter
    private String asQuad;

    Register(String asByte, String asWord, String asDouble, String asQuad) {
        this.asByte = asByte;
        this.asWord = asWord;
        this.asDouble = asDouble;
        this.asQuad = asQuad;
    }

    public String asSize(RegisterSize size) {
        return switch (size) {
            case BYTE -> asByte;
            case WORD -> asWord;
            case DOUBLE -> asDouble;
            case QUAD -> asQuad;
        };
    }

    public static Register fromOrdinal(int ord) {
        return values()[ord];
    }
}
