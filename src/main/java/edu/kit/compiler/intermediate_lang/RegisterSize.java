package edu.kit.compiler.intermediate_lang;

import lombok.Getter;

/**
 * Represents the different possible sizes for x86 registers.
 */
public enum RegisterSize {
    BYTE(1),
    WORD(2),
    DOUBLE(4),
    QUAD(8);

    @Getter
    private int bytes;

    RegisterSize(int bytes) {
        this.bytes = bytes;
    }
}
