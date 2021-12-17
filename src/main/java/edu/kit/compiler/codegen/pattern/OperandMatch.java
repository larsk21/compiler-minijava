package edu.kit.compiler.codegen.pattern;

import java.util.Collection;
import java.util.stream.Stream;

import edu.kit.compiler.codegen.Operand;
import firm.nodes.Node;
import lombok.RequiredArgsConstructor;

public interface OperandMatch<Op extends Operand> extends Match {

    public Op getOperand();

    public static <Op extends Operand> OperandMatch<Op> some(Op operand, Collection<Node> preds) {
        return new Some<>(operand, preds);
    }

    public static <Op extends Operand> OperandMatch<Op> none() {
        return new None<Op>();
    }

    public static final class None<Op extends Operand> implements OperandMatch<Op> {
        @Override
        public boolean matches() {
            return false;
        }

        @Override
        public Op getOperand() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Stream<Node> getPredecessors() {
            throw new UnsupportedOperationException();
        }
    }

    @RequiredArgsConstructor
    public static final class Some<Op extends Operand> implements OperandMatch<Op> {

        private final Op operand;
        private final Collection<Node> predecessors;

        @Override
        public boolean matches() {
            return true;
        }

        @Override
        public Op getOperand() {
            return operand;
        }

        @Override
        public Stream<Node> getPredecessors() {
            return predecessors.stream();
        }
    }
}
