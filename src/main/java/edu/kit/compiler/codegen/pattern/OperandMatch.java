package edu.kit.compiler.codegen.pattern;

import edu.kit.compiler.codegen.Operand;
import lombok.RequiredArgsConstructor;

public interface OperandMatch<Op extends Operand> extends Match {

    public Op getOperand();

    public static <Op extends Operand> OperandMatch<Op> some(Op operand) {
        return new Some<>(operand);
    }

    public static <Op extends Operand> OperandMatch<Op> none() {
        return new None<Op>();
    }

    public static final class None<Op extends Operand> extends Match.None implements OperandMatch<Op> {
        @Override
        public Op getOperand() {
            throw new UnsupportedOperationException();
        }
    }

    @RequiredArgsConstructor
    public static final class Some<Op extends Operand> extends Match.Some implements OperandMatch<Op> {
        public final Op operand;

        @Override
        public Op getOperand() {
            return operand;
        }
    }
}
