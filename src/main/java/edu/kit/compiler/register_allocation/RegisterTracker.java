package edu.kit.compiler.register_allocation;

import edu.kit.compiler.intermediate_lang.Register;
import edu.kit.compiler.logger.Logger;
import lombok.Getter;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static edu.kit.compiler.intermediate_lang.Register.*;

public class RegisterTracker {
    private EnumMap<Register, Integer> registers;
    @Getter
    private EnumSet<Register> usedRegisters;

    public RegisterTracker() {
        this.registers = new EnumMap<>(Register.class);
        this.usedRegisters = EnumSet.noneOf(Register.class);
    }

    public boolean isEmpty() {
        return registers.isEmpty();
    }

    public boolean isFree(Register r) {
        return !registers.containsKey(r);
    }

    public Optional<Integer> get(Register r) {
        if (isFree(r)) {
            return Optional.empty();
        }
        return Optional.of(registers.get(r));
    }

    public void set(Register r, int vRegister) {
        assert isFree(r);
        registers.put(r, vRegister);
        usedRegisters.add(r);
    }

    public void clear(Register r) {
        assert !isFree(r);
        registers.remove(r);
    }

    public void replace(Register r, int vRegister) {
        assert !isFree(r);
        registers.put(r, vRegister);
    }

    public void markUsed(Register r) {
        usedRegisters.add(r);
    }

    /**
     * Free registers must always ve requested together.
     */
    public List<Register> getFreeRegisters(int num, Optional<Register> exludedRegister) {
        return getFreeRegisters(num, RegisterPreference.PREFER_CALLEE_SAVED, exludedRegister);
    }

    public List<Register> getFreeRegisters(int num, RegisterPreference pref,
                                           Optional<Register> exludedRegister) {
        List<Register> result = new ArrayList<>();
        if (num == 0) {
            return result;
        }
        for (Register r: pref.inPreferenceOrder().filter(
                r -> !isReservedRegister(r) && isFree(r) &&
                        (exludedRegister.isEmpty() || r != exludedRegister.get())
        ).collect(Collectors.toList())) {
            result.add(r);
            usedRegisters.add(r);
            if (result.size() == num) {
                return result;
            }
        }
        throw new IllegalStateException("Not enough registers available.");
    }

    public Optional<Register> tryGetFreeRegister() {
        return tryGetFreeRegister(RegisterPreference.PREFER_CALLEE_SAVED);
    }

    public Optional<Register> tryGetFreeRegister(RegisterPreference pref) {
        for (Register r: pref.inPreferenceOrder().filter(
                r -> !isReservedRegister(r) && isFree(r)
        ).collect(Collectors.toList())) {
                usedRegisters.add(r);
                return Optional.of(r);
        }
        return Optional.empty();
    }

    // high value => high priority
    public Register getPrioritizedRegister(Function<Register, Integer> priorities) {
        Register best = RAX;
        int maxPrio = -1;
        for (Register r: Register.values()) {
            if (!isReservedRegister(r) && isFree(r)) {
                int prio = priorities.apply(r);
                if (prio > maxPrio) {
                    best = r;
                    maxPrio = prio;
                }
            }
        }
        assert maxPrio > -1;
        usedRegisters.add(best);
        return best;
    }

    private boolean isReservedRegister(Register r) {
        return switch (r) {
            case RSP, RBP -> true;
            default -> false;
        };
    }

    // debugging
    public void printState(Logger logger) {
        logger.debug("Current register assignments:");
        registers.forEach((r, vRegister) -> {
            logger.debug("%s => %d", r.getAsQuad(), vRegister);
        });
    }
}
