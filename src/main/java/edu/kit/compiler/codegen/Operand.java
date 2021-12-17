package edu.kit.compiler.codegen;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import edu.kit.compiler.intermediate_lang.RegisterSize;
import firm.Mode;
import firm.TargetValue;
import lombok.RequiredArgsConstructor;

public abstract class Operand {
    public abstract String format();

    public abstract RegisterSize getSize();

    public abstract Mode getMode();

    public abstract List<Integer> getSourceRegisters();

    public static Immediate immediate(TargetValue value) {
        return new Immediate(value);
    }

    public static Register register(Mode mode, int register) {
        return new Register(mode, register);
    }

    public static Memory memory(Register register) {
        return new Memory(register);
    }

    @RequiredArgsConstructor
    public static final class Immediate extends Operand {
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

        public TargetValue get() {
            return value;
        }

        @Override
        public List<Integer> getSourceRegisters() {
            return Collections.emptyList();
        }
    }

    public static abstract class Target extends Operand {
        public abstract Optional<Integer> getTargetRegister();
    }

    @RequiredArgsConstructor
    public static final class Register extends Target {
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
    public static final class Memory extends Target {
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
