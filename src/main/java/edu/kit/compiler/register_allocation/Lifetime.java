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

    public Lifetime(int begin, int end) {
        assert begin < end;
        this.begin = begin;
        this.end = end;
    }

    public boolean contains(int time) {
        return begin <= time && time < end;
    }

    public boolean interferes(Lifetime other) {
        return begin < other.end && other.begin < end;
    }
}
