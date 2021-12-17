package edu.kit.compiler.register_allocation;

import edu.kit.compiler.intermediate_lang.Register;
import lombok.Getter;

import java.util.Optional;

/**
 * A virtual register can either be assigned to a concrete register
 * or to a stack slot (represented by the offset to the base pointer).
 */
public class RegisterAssignment {
    @Getter
    private Optional<Register> register;
    @Getter
    private Optional<Integer> stackSlot;

    public RegisterAssignment() {
        // unassigned
        this.register = Optional.empty();
        this.stackSlot = Optional.empty();
    }

    public RegisterAssignment(Register cRegister) {
        this.register = Optional.of(cRegister);
        this.stackSlot = Optional.empty();
    }

    public RegisterAssignment(int stackSlot) {
        this.register = Optional.empty();
        this.stackSlot = Optional.of(stackSlot);
    }

    public boolean isSpilled() {
        return stackSlot.isPresent();
    }
}
