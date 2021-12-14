package edu.kit.compiler.register_allocation;

import lombok.Getter;

/**
 * Models a register lifetime via begin and (non-inclusive) end index.
 */
public class Lifetime {
    @Getter
    private int begin;
    @Getter
    private int end;

    /**
     * Whether the register is an input register for the last instruction of the lifetime.
     * Note that for the first instruction, the register always is the target register
     * (due to dominance requirements).
     */
    @Getter
    private boolean lastInstrIsInput;

    public Lifetime(int begin, int end, boolean lastInstrIsInput) {
        assert -1 <= begin && begin < end && 0 < end;
        this.begin = begin;
        this.end = end;
        this.lastInstrIsInput = lastInstrIsInput;
        if (lastInstrIsInput) {
            assert begin + 1 < end;
        }
    }

    public Lifetime(int begin, int end) {
        this(begin, end, false);
    }

    public Lifetime() {
        this.begin = 0;
        this.end = 0;
        this.lastInstrIsInput = false;
    }

    public boolean isTrivial() {
        return end == 0;
    }

    public boolean contains(int index) {
        return begin <= index && index < end;
    }


    public boolean isLastInstructionAndInput(int index) {
        assert index < end;
        return lastInstrIsInput && index + 1 == end;
    }

    public boolean interferes(Lifetime other) {
        // trivial lifetime
        if (isTrivial()) {
            return false;
        }
        // check cases where one register is allowed to replace the other
        // within the same instruction
        if (other.begin + 1 == end) {
            return !lastInstrIsInput;
        } else if (begin + 1 == other.end) {
            return !other.lastInstrIsInput;
        }
        return begin < other.end && other.begin < end;
    }

    /**
     * Extends the lifetime to include the given index.
     */
    public void extend(int index, boolean lastInstrIsInput) {
        assert !isTrivial() && index >= end;
        this.end = index + 1;
        this.lastInstrIsInput = lastInstrIsInput;
    }

    @Override
    public String toString() {
        String result = String.format("%d:%d", begin, end);
        if (lastInstrIsInput) {
            result += '*';
        }
        return result;
    }
}
