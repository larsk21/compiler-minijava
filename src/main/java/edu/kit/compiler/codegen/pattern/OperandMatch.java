package edu.kit.compiler.codegen.pattern;

import java.util.Collection;
import java.util.stream.Stream;

import edu.kit.compiler.codegen.Operand;
import firm.nodes.Node;
import lombok.RequiredArgsConstructor;

/**
 * A match for an operand to an instruction. For example the result register
 * of a previously matched instruction, or immediate value associated with Const
 * node of the firm graph.
 */
public interface OperandMatch<T extends Operand> extends Match {

    /**
     * Return the operand associated with this match.
     */
    T getOperand();

    public static <T extends Operand> OperandMatch<T> some(T operand, Collection<Node> preds) {
        return new Some<>(operand, preds);
    }

    public static <T extends Operand> OperandMatch<T> none() {
        return new None<T>();
    }

    public static final class None<T extends Operand> implements OperandMatch<T> {
        @Override
        public boolean matches() {
            return false;
        }

        @Override
        public T getOperand() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Stream<Node> getPredecessors() {
            throw new UnsupportedOperationException();
        }
    }

    @RequiredArgsConstructor
    public static final class Some<T extends Operand> implements OperandMatch<T> {

        private final T operand;
        private final Collection<Node> predecessors;

        @Override
        public boolean matches() {
            return true;
        }

        @Override
        public T getOperand() {
            return operand;
        }

        @Override
        public Stream<Node> getPredecessors() {
            return predecessors.stream();
        }
    }
}
