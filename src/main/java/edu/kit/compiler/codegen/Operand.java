package edu.kit.compiler.codegen;

import java.util.List;
import java.util.Optional;

import edu.kit.compiler.intermediate_lang.RegisterSize;
import firm.Mode;
import firm.TargetValue;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

public interface Operand {

    public abstract String format();

    public abstract RegisterSize getSize();

    public abstract Mode getMode();

    public static Immediate immediate(TargetValue value) {
        return new Immediate(value);
    }

    public static Register register(Mode mode, int register) {
        return new Register(mode, register);
    }

    public static Memory memory(Register register) {
        return new Memory(register);
    }

    public static interface Source extends Operand {
        public abstract List<Integer> getSourceRegisters();
    }

    public static interface Target extends Source {
        public abstract Optional<Integer> getTargetRegister();
    }

    @RequiredArgsConstructor
    @ToString
    public static final class Immediate implements Source {

        private final TargetValue value;

        @Override
        public String format() {
            // todo are byte and word formatting correct
            return switch (getSize()) {
                case BYTE -> String.format("$0x%x", (byte) value.asInt());
                case WORD -> String.format("$0x%x", (short) value.asInt());
                case DOUBLE -> String.format("$0x%x", value.asInt());
                case QUAD -> String.format("$0x%x", value.asLong());
                default -> throw new IllegalStateException();
            };
        }

        @Override
        public RegisterSize getSize() {
            return Util.getSize(value.getMode());
        }

        @Override
        public Mode getMode() {
            return value.getMode();
        }

        @Override
        public List<Integer> getSourceRegisters() {
            return List.of();
        }

        public TargetValue get() {
            return value;
        }
    }

    @RequiredArgsConstructor
    @ToString
    public static final class Register implements Target {

        private final Mode mode;
        private final int register;

        @Override
        public String format() {
            return String.format("@%d", register);
        }

        @Override
        public RegisterSize getSize() {
            return Util.getSize(mode);
        }

        @Override
        public Mode getMode() {
            return mode;
        }

        @Override
        public Optional<Integer> getTargetRegister() {
            return Optional.of(register);
        }

        @Override
        public List<Integer> getSourceRegisters() {
            return List.of(register);
        }

        public int get() {
            return register;
        }
    }

    @RequiredArgsConstructor
    @ToString
    public static final class Memory implements Target {

        private final Register register;

        @Override
        public String format() {
            return String.format("(%s)", register.format());
        }

        @Override
        public RegisterSize getSize() {
            return Util.getSize(Mode.getANY());
        }

        @Override
        public Mode getMode() {
            return Mode.getP();
        }

        @Override
        public Optional<Integer> getTargetRegister() {
            return Optional.empty();
        }

        @Override
        public List<Integer> getSourceRegisters() {
            return List.of(register.register);
        }
    }
}
