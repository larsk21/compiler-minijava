package edu.kit.compiler.codegen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import firm.Mode;
import firm.nodes.Node;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class PhiInstruction {
    
    private final List<PhiSource> entries = new ArrayList<>();

    @Getter
    private final int destination;

    @Getter
    private final Mode mode;

    public List<PhiSource> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    public void addEntry(Node block, int register) {
        entries.add(new PhiSource(block, register));
    }

    @Override
    public String toString() {
        var source = entries.stream().map(e -> e.toString())
            .collect(Collectors.joining(", "));
        
        return String.format("phi %s -> @%d", source, destination);
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class PhiSource {
        @Getter
        private final Node predBlock;

        @Getter
        private final int register;

        @Override
        public String toString() {
            return "@" + register;
        }
    }
}
