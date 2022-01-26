package edu.kit.compiler.assembly;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Represents a collection of assembly optimization that can be collectively
 * applied to a list of instructions.
 */
@RequiredArgsConstructor
public final class AssemblyOptimizer {

    /**
     * Represents an optimization that can be applied to assembly instructions.
     */
    @RequiredArgsConstructor
    public static abstract class AssemblyOptimization {

        @Getter
        private final int windowSize;

        abstract Optional<String[]> optimize(String[] instructions);
    }

    private final Iterable<AssemblyOptimization> optimizations;

    public List<String> apply(List<String> instructions) {
        Deque<String> input = new ArrayDeque<>(instructions);

        for (var optimization : optimizations) {
            input = apply(input, optimization);
        }

        return List.copyOf(input);
    }

    private Deque<String> apply(Deque<String> input, AssemblyOptimization optimization) {
        var windowSize = optimization.getWindowSize();

        if (input.size() < windowSize) {
            return input;
        }

        var output = new ArrayDeque<String>();
        var window = new String[windowSize];

        for (int i = 0; i < windowSize; ++i) {
            window[i] = input.removeFirst();
        }

        do {
            var optimized = optimization.optimize(window);
            if (optimized.isPresent()) {
                copyWindow(optimized.get(), window, input);
            } else {
                advanceWindow(window, input, output);
            }

        } while (!input.isEmpty());

        for (var instr : window) {
            if (instr != null) {
                output.addLast(instr);
            }
        }

        return output;
    }

    /**
     * Shift the window left by one position. Left margin is pushed to output,
     * right margin ist popped from output.
     */
    private void advanceWindow(String[] window, Deque<String> input, Deque<String> output) {
        assert window.length > 0;

        output.addLast(window[0]);
        for (int i = 1; i < window.length; ++i) {
            window[i - 1] = window[i];
        }

        if (!input.isEmpty()) {
            window[window.length - 1] = input.removeFirst();
        }
    }

    /**
     * Copy element from `source` to `target`. Spare element are pushed and
     * popped from the front of `instructions`
     */
    private void copyWindow(String[] source, String[] target, Deque<String> instructions) {
        var min = Math.min(source.length, target.length);

        for (int i = 0; i < min; ++i) {
            target[i] = source[i];
        }

        for (int i = source.length; i < target.length && !instructions.isEmpty(); ++i) {
            target[i] = instructions.removeFirst();
        }

        for (int i = target.length; i < source.length; ++i) {
            instructions.addFirst(source[i]);
        }
    }
}
