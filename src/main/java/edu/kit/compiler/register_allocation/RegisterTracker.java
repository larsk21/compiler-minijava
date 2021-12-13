package edu.kit.compiler.register_allocation;

import edu.kit.compiler.intermediate_lang.Register;
import edu.kit.compiler.logger.Logger;
import lombok.Getter;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static edu.kit.compiler.intermediate_lang.Register.*;

public class RegisterTracker {
    // don't count reserved registers
    private static final int NUM_AVAILABLE = Register.values().length - 2;

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
        return !registers.containsKey(r) || registers.get(r) < 0;
    }

    public boolean isTmp(Register r) {
        return registers.containsKey(r) && registers.get(r) < 0;
    }

    public int numFree() {
        return NUM_AVAILABLE - registers.size();
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

    public boolean clear(int vRegister) {
        for (var entry: registers.entrySet()) {
            if (entry.getValue() == vRegister) {
                registers.remove(entry.getKey());
                return true;
            }
        }
        return false;
    }

    public void replace(Register r, int vRegister) {
        assert !isFree(r);
        registers.put(r, vRegister);
    }

    public void setTmp(Register r, int vRegister) {
        assert isFree(r);
        registers.put(r, -vRegister - 1);
        usedRegisters.add(r);
    }

    public boolean clearTmp(int vRegister) {
        var reg = getTmp(vRegister);
        if (reg.isPresent()) {
            registers.remove(reg.get());
            return true;
        }
        return false;
    }

    public boolean clearTmp(Register r) {
        if (isTmp(r)) {
            registers.remove(r);
            return true;
        }
        return false;
    }

    public void clearAllTmps() {
        for (var entry: registers.entrySet()) {
            if (entry.getValue() < 0) {
                registers.remove(entry.getKey());
            }
        }
    }

    public Optional<Register> getTmp(int vRegister) {
        for (var entry: registers.entrySet()) {
            if (-entry.getValue() - 1 == vRegister) {
                return Optional.of(entry.getKey());
            }
        }
        return Optional.empty();
    }

    public boolean hasTmp(int vRegister) {
        return getTmp(vRegister).isPresent();
    }

    public void clearSlotFromTmp(Register r) {
        assert isFree(r);
        registers.remove(r);
    }

    public void markUsed(Register r) {
        usedRegisters.add(r);
    }

    public List<Integer> getCurrentVRegisters() {
        return getCurrentVRegisters(Optional.empty());
    }

    public List<Integer> getCurrentVRegisters(Optional<Register> excludedRegister) {
        List<Integer> result = new ArrayList<>(registers.size());
        for (var entry: registers.entrySet()) {
            if (excludedRegister.isEmpty() || entry.getKey() != excludedRegister.get()) {
                result.add(entry.getValue());
            }
        }
        return result;
    }

    /**
     * Free registers must always be requested together.
     */
    public List<Register> getFreeRegisters(int num, Optional<Register> exludedRegister) {
        return getFreeRegisters(num, RegisterPreference.PREFER_CALLEE_SAVED_AVOID_DIV, exludedRegister);
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

    /**
     * Free registers must always be requested together.
     *
     * This will try to avoid killing temporary register assignments.
     */
    public List<Register> getTmpRegisters(int num, Optional<Register> exludedRegister) {
        return getTmpRegisters(num, RegisterPreference.PREFER_CALLEE_SAVED_AVOID_DIV, exludedRegister);
    }

    public List<Register> getTmpRegisters(int num, RegisterPreference pref,
                                          Optional<Register> exludedRegister) {
        List<Register> result = new ArrayList<>();
        if (num == 0) {
            return result;
        }
        // try find completely free registers
        for (Register r: pref.inPreferenceOrder().filter(
                r -> !isReservedRegister(r) && isFree(r) && !isTmp(r) &&
                        (exludedRegister.isEmpty() || r != exludedRegister.get())
        ).collect(Collectors.toList())) {
            result.add(r);
            usedRegisters.add(r);
            if (result.size() == num) {
                return result;
            }
        }
        // if not sufficient, kill temporary assignments
        for (Register r: pref.inPreferenceOrder().filter(
                r -> !isReservedRegister(r) && isFree(r) && isTmp(r) &&
                        (exludedRegister.isEmpty() || r != exludedRegister.get())
        ).collect(Collectors.toList())) {
            result.add(r);
            if (result.size() == num) {
                return result;
            }
        }
        throw new IllegalStateException("Not enough registers available.");
    }

    public Optional<Register> tryGetFreeRegister() {
        return tryGetFreeRegister(RegisterPreference.PREFER_CALLEE_SAVED_AVOID_DIV);
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
//    public Register getPrioritizedRegister(Function<Register, Integer> priorities) {
//        Register best = RAX;
//        int maxPrio = -1;
//        for (Register r: Register.values()) {
//            if (!isReservedRegister(r) && isFree(r)) {
//                int prio = priorities.apply(r);
//                if (prio > maxPrio) {
//                    best = r;
//                    maxPrio = prio;
//                }
//            }
//        }
//        assert maxPrio > -1;
//        usedRegisters.add(best);
//        return best;
//    }

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
