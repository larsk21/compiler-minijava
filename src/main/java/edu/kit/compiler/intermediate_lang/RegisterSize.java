package edu.kit.compiler.intermediate_lang;

import lombok.Getter;

/**
 * Represents the different possible sizes for x86 registers.
 */
public enum RegisterSize {
    BYTE(1, 'b'),
    WORD(2, 'w'),
    DOUBLE(4, 'l'),
    QUAD(8, 'q');

    @Getter
    private int bytes;
    @Getter
    private char suffix;

    RegisterSize(int bytes, char suffix) {
        this.bytes = bytes;
        this.suffix = suffix;
    }
}
