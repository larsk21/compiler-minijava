package edu.kit.compiler.register_allocation;

import edu.kit.compiler.intermediate_lang.Register;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Stream;

/**
 * For a given virtual register, this class represents preferences
 * to which concrete register the virtual register should (or should not)
 * be assigned.
 */
public class RegisterPreference {
    private static final EnumSet<Register> x86_DIV_REGISTERS = EnumSet.of(Register.RAX, Register.RDX);
    private static final EnumSet<Register> x86_GENERAL_CALLER_SAVED = EnumSet.of(
            Register.RDI, Register.RSI, Register.RCX, Register.R8, Register.R9, Register.R10, Register.R11
    );
    private static final EnumSet<Register> x86_CALLEE_SAVED = EnumSet.of(
            Register.R12, Register.R13, Register.R14, Register.R15, Register.RBX
    );

    public static final RegisterPreference PREFER_CALLEE_SAVED =
            new RegisterPreference(1, x86_CALLEE_SAVED, x86_GENERAL_CALLER_SAVED, x86_DIV_REGISTERS);
    public static final RegisterPreference PREFER_CALLEE_SAVED_NO_DIV =
            new RegisterPreference(1, x86_CALLEE_SAVED, x86_GENERAL_CALLER_SAVED);
    public static final RegisterPreference PREFER_CALLER_SAVED =
            new RegisterPreference(3, x86_GENERAL_CALLER_SAVED, x86_DIV_REGISTERS, x86_CALLEE_SAVED);
    public static final RegisterPreference PREFER_CALLER_SAVED_AVOID_DIV =
            new RegisterPreference(2, x86_GENERAL_CALLER_SAVED, x86_CALLEE_SAVED, x86_DIV_REGISTERS);

    private List<EnumSet<Register>> preferenceList;
    /**
     * Registers starting with this index should rather be avoided.
     */
    private int avoidanceIndex;

    private RegisterPreference(int avoidanceIndex, EnumSet<Register>... prefs) {
        this(avoidanceIndex, List.of(prefs));
    }

    private RegisterPreference(int avoidanceIndex, List<EnumSet<Register>> prefs) {
        this.preferenceList = prefs;
        this.avoidanceIndex = avoidanceIndex;
    }

    public RegisterPreference withPreferredRegister(Register r) {
        List<EnumSet<Register>> cloned = new ArrayList<>();
        cloned.add(EnumSet.of(r));
        cloned.addAll(preferenceList);
        return new RegisterPreference(avoidanceIndex + 1, cloned);
    }

    public Stream<Register> inPreferenceOrder() {
        return preferenceList.stream().flatMap(EnumSet::stream);
    }

    public Stream<Register> preferredOnlyOrder() {
        return preferenceList.stream().limit(avoidanceIndex).flatMap(EnumSet::stream);
    }

    public boolean isPreferred(Register r) {
        for (int i = 0; i < avoidanceIndex; i++) {
            if (preferenceList.get(i).contains(r)) {
                return true;
            }
        }
        return false;
    }

    public boolean isAvoided(Register r) {
        return !isPreferred(r);
    }
}
