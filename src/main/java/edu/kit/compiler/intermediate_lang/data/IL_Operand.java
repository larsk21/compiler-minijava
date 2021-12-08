package edu.kit.compiler.intermediate_lang.data;

import com.sun.jna.Pointer;
import lombok.Getter;

import java.util.Optional;

/**
 * operand for an operation
 */
@Getter
public class IL_Operand {
    private IL_Repr il_repr;

    /**
     * depending on the representation different sources are available
     */
    private Optional<IL_Register> allocatedReg;
    private Optional<Integer> virtualRegIndex;
    private Optional<Integer> stackSlot;
    private Optional<Integer> constValue;
    private Optional<Pointer> heapAddress;

    public IL_Operand(IL_Register register) {
        this.allocatedReg = Optional.of(register);
    }
    public IL_Operand(IL_Repr repr, int idx) {
       switch (repr) {
           case VIRTUAL: this.virtualRegIndex = Optional.of(idx);
           case CONST: this.constValue = Optional.of(idx);
           case STACK: this.stackSlot = Optional.of(idx);
       }
    }
    public IL_Operand(Pointer ptr) {
        this.heapAddress = Optional.of(ptr);
    }

}
