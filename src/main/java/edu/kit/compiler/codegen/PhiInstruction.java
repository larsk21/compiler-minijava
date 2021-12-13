package edu.kit.compiler.codegen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import firm.Mode;
import firm.nodes.Node;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class PhiInstruction {
    
    private final List<Entry> entries = new ArrayList<>();

    @Getter
    private final int destination;

    @Getter
    private final Mode mode;

    public List<Entry> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    public void addEntry(Node block, int register) {
        entries.add(new Entry(block, register));
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class Entry {
        @Getter
        private final Node predBlock;

        @Getter
        private final int register;
    }
}
