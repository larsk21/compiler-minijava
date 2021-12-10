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
        assert begin < end;
        this.begin = begin;
        this.end = end;
        this.lastInstrIsInput = lastInstrIsInput;
        if (lastInstrIsInput) {
            assert begin + 1 < end;
        }
    }

    public boolean contains(int time) {
        return begin <= time && time < end;
    }

    public boolean interferes(Lifetime other) {
        // check cases where one register is allowed to replace the other
        // within the same instruction
        if (other.begin + 1 == end) {
            return lastInstrIsInput;
        } else if (begin + 1 == other.end) {
            return other.lastInstrIsInput;
        }
        return begin < other.end && other.begin < end;
    }
}
