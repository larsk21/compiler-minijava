package edu.kit.compiler.register_allocation;

import edu.kit.compiler.intermediate_lang.Register;
import edu.kit.compiler.logger.Logger;
import lombok.Getter;

import java.util.*;
import java.util.function.Function;

public class RegisterTracker {
    private EnumMap<Register, Integer> registers;
    @Getter
    private EnumSet<Register> usedRegisters;

    public RegisterTracker() {
        this.registers = new EnumMap<>(Register.class);
        this.usedRegisters = EnumSet.noneOf(Register.class);
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

    public List<Register> getFreeRegisters(int num) {
        List<Register> result = new ArrayList<>();
        if (num == 0) {
            return result;
        }
        for (Register r: Register.values()) {
            if (!isReservedRegister(r) && isFree(r)) {
                result.add(r);
                if (result.size() == num) {
                    return result;
                }
            }
        }
        throw new IllegalStateException("Not enough registers available.");
    }

    // high value => high priority
    public Register getPrioritizedRegister(Function<Register, Integer> priorities) {
        Register best = Register.RAX;
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
