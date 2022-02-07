package edu.kit.compiler;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

/**
 * Represents the set of all debug flags supported by this compiler.
 */
@AllArgsConstructor
@RequiredArgsConstructor
public class DebugFlags {

    /**
     * Whether the compiler should dump the graphs of all methods.
     */
    @Getter
    @Setter
    private boolean dumpGraphs = false;

    /**
     * Whether the inline optimization should be disabled regardless of the optimization level.
     */
    @Getter
    @Setter
    private boolean noInline = false;

}
