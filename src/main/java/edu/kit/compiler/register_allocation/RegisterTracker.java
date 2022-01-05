package edu.kit.compiler.register_allocation;

import edu.kit.compiler.intermediate_lang.Register;
import edu.kit.compiler.logger.Logger;
import lombok.Getter;

import java.util.*;
import java.util.stream.Collectors;

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
     *
     * This will try to avoid killing temporary register assignments.
     */
    public List<Register> getTmpRegisters(int num, Set<Register> excluded) {
        return getTmpRegisters(num, RegisterPreference.PREFER_CALLEE_SAVED_AVOID_DIV, excluded);
    }

    public List<Register> getTmpRegisters(int num, RegisterPreference pref,
                                          Set<Register> excluded) {
        List<Register> result = new ArrayList<>();
        if (num == 0) {
            return result;
        }
        // try find completely free registers
        for (Register r: pref.inPreferenceOrder().filter(
                r -> !isReservedRegister(r) && isFree(r) && !isTmp(r) && !excluded.contains(r)
        ).collect(Collectors.toList())) {
            result.add(r);
            usedRegisters.add(r);
            if (result.size() == num) {
                return result;
            }
        }
        // if not sufficient, kill temporary assignments
        for (Register r: pref.inPreferenceOrder().filter(
                r -> !isReservedRegister(r) && isFree(r) && isTmp(r) && !excluded.contains(r)
        ).collect(Collectors.toList())) {
            result.add(r);
            if (result.size() == num) {
                return result;
            }
        }
        throw new IllegalStateException("Not enough registers available.");
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
