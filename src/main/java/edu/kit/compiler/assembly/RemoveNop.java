package edu.kit.compiler.assembly;

import java.util.Iterator;
import java.util.NoSuchElementException;

import edu.kit.compiler.assembly.AssemblyOptimizer.AssemblyOptimization;
import lombok.RequiredArgsConstructor;

/**
 * A basic optimization to remove `nop` instructions, potentially introduced
 * by Unknown nodes in the Firm graph.
 */
public class RemoveNop implements AssemblyOptimization {

    @Override
    public Iterator<String> optimize(Iterator<String> instructions) {
        return new Optimization(instructions);
    }

    @RequiredArgsConstructor
    private static final class Optimization implements Iterator<String> {

        private final Iterator<String> input;

        private String buffer;

        @Override
        public boolean hasNext() {
            fillBuffer();
            return buffer != null;
        }

        @Override
        public String next() {
            var next = buffer;
            buffer = null;

            if (next != null) {
                return next;
            } else {
                throw new NoSuchElementException();
            }
        }

        private void fillBuffer() {
            while (buffer == null && input.hasNext()) {
                var next = input.next();
                buffer = next == "nop" ? null : next;
            }
        }
    }
}
