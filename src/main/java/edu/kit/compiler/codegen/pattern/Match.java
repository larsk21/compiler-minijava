package edu.kit.compiler.codegen.pattern;

import java.util.stream.Stream;

import firm.nodes.Node;

public interface Match {
    public boolean matches();

    public Stream<Node> getPredecessors();

    public static abstract class None implements Match {
        @Override
        public boolean matches() {
            return false;
        }

        @Override
        public Stream<Node> getPredecessors() {
            throw new UnsupportedOperationException();
        }
    }

    public static abstract class Some implements Match {
        @Override
        public boolean matches() {
            return true;
        }
    }
}
