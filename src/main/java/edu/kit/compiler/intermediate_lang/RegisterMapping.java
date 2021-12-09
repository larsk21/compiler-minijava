package edu.kit.compiler.intermediate_lang;

public interface RegisterMapping {
    boolean contains(int vRegister);

    /**
     * Get the mapped concrete register.
     */
    Register get(int vRegister);
}
