package edu.kit.compiler.codegen;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import edu.kit.compiler.intermediate_lang.Instruction;
import edu.kit.compiler.intermediate_lang.RegisterSize;
import firm.Mode;
import firm.TargetValue;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

/**
 * Base for operands used during instruction selection. Implementations will
 * will implement one of the interfaces `Source` or `Target`.
 */
public interface Operand {

    /**
     * Return the operand formatted using AT&T syntax.
     */
    String format();

    /**
     * Return the register size of the operand.
     */
    RegisterSize getSize();

    /**
     * Return the Firm mode of the operand.
     */
    Mode getMode();

    /**
     * If an instruction needs to be executed for the operand to be available,
     * it is returned by this function. In practice, most operand will return
     * nothing here. Only `ImmediateRegister`s will actually return an
     * instruction, namely a move of their immediate to their target register.
     */
    default Optional<Instruction> getInstruction() {
        return Optional.empty();
    }

    /**
     * If the operand is an immediate, return its TargetValue.
     */
    default Optional<TargetValue> getConstValue() {
        return Optional.empty();
    }

    /**
     * Return an Operand equivalent to an immediate of the given TargetValue.
     * The size of the value is used to determine the size of the operand.
     */
    public static Immediate immediate(TargetValue value) {
        return new Immediate(value);
    }

    /**
     * Return an Operand representing the given register. The given mode is
     * used to determine the size of the register.
     */
    public static Register register(Mode mode, int register) {
        return new NormalRegister(mode, register);
    }

    /**
     * Return an Operand representing the given register, which is directly
     * backed by the given immediate value. In practice this means that the
     * register is the result of loading an immediate value. This type of
     * operand may be used instead of an "ordinary" register for reasons of
     * efficiency.
     * All Const nodes in a Firm graph are declared in its Start block. With a
     * naive instruction selection, these values might be loaded into registers
     * far too early and lead to excessive register usage. ImmediateRegisters
     * do away with the need to explicitly generate code for these nodes.
     * Instead the `getInstruction()` method of this operand will return the
     * required instruction, which may be placed exactly where it is needed.
     */
    public static Register immediateRegister(Immediate value, int register) {
        return new ImmediateRegister(value, register);
    }

    /**
     * Return an Operand representing a memory location. The combination of
     * values present is used to determine the correct x86 addressing mode.
     * Illegal combinations of present (or rather absent) values will result
     * in exceptions being thrown.
     */
    public static Memory memory(Optional<Integer> offset, Optional<Register> base,
            Optional<Register> index, Optional<Integer> scale) {
        return new Memory(offset, base, index, scale);
    }

    /**
     * Base for operands that can be used as source in an instruction.
     */
    public static interface Source extends Operand {
        /**
         * Returns a list of all registers required to evaluate the operand.
         * For example, an immediate will return an empty list. A address like
         * 12(@1,@42,8) will return a list containing 1 and 42.
         */
        public abstract List<Integer> getSourceRegisters();
    }

    /**
     * Base for operands that can be used as target in an instruction. Extends
     * `Source` for the sake of simplicity (generic bounds in OperandMatch would
     * otherwise get even more verbose).
     */
    public static interface Target extends Source {
        /**
         * Return the register that would be overwritten when using the operand
         * as target in an instruction.
         */
        public abstract Optional<Integer> getTargetRegister();
    }

    @RequiredArgsConstructor
    @ToString
    public static final class Immediate implements Source {

        private final TargetValue value;

        @Override
        public String format() {
            return String.format("$%d", value.asLong());
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

        @Override
        public Optional<TargetValue> getConstValue() {
            return Optional.of(value);
        }

        /**
         * Returns the TargetValue held by the Immediate.
         */
        public TargetValue get() {
            return value;
        }
    }

    @RequiredArgsConstructor
    public static abstract class Register implements Target {

        private final int register;

        @Override
        public String format() {
            return String.format("@%d", register);
        }

        @Override
        public Optional<Integer> getTargetRegister() {
            return Optional.of(register);
        }

        @Override
        public List<Integer> getSourceRegisters() {
            return List.of(register);
        }

        /**
         * Returns the index of the referenced register.
         */
        public int get() {
            return register;
        }
    }

    @ToString
    public static class NormalRegister extends Register {

        private final Mode mode;

        public NormalRegister(Mode mode, int register) {
            super(register);
            this.mode = mode;
        }

        @Override
        public RegisterSize getSize() {
            return Util.getSize(mode);
        }

        @Override
        public Mode getMode() {
            return mode;
        }
    }

    @ToString
    public static final class ImmediateRegister extends Register {
        
        // ? would is make sense for getConstValue return the Tarval ?

        private final Immediate value;

        public ImmediateRegister(Immediate value, int register) {
            super(register);
            this.value = value;
        }

        @Override
        public RegisterSize getSize() {
            return value.getSize();
        }

        @Override
        public Mode getMode() {
            return value.getMode();
        }

        @Override
        public Optional<Instruction> getInstruction() {
            return Optional.of(Instruction.newOp(
                    Util.formatLoad(getSize(), value, this),
                    Collections.emptyList(), Optional.empty(),
                    this.get()));
        }
    }

    @ToString
    public static final class Memory implements Target {

        private final Optional<Integer> offset;
        private final Optional<Register> baseRegister;
        private final Optional<Register> indexRegister;
        private final Optional<Integer> scale;

        public Memory(Optional<Integer> offset, Optional<Register> baseRegister,
                Optional<Register> indexRegister, Optional<Integer> scale) {
            if (baseRegister.isEmpty() && indexRegister.isEmpty()) {
                throw new IllegalArgumentException("either base or index register must be present");
            } else if (scale.isPresent() && indexRegister.isEmpty()) {
                throw new IllegalArgumentException("scale required index register to be present");
            } else {
                this.offset = offset;
                this.baseRegister = baseRegister;
                this.indexRegister = indexRegister;
                this.scale = scale;
            }
        }

        @Override
        public String format() {
            var builder = new StringBuilder();
            if (offset.isPresent()) {
                builder.append(offset.get());
            }
            builder.append('(');
            if (baseRegister.isPresent()) {
                builder.append(baseRegister.get().format());
            }
            if (indexRegister.isPresent()) {
                builder.append(',');
                builder.append(indexRegister.get().format());
            }
            if (scale.isPresent()) {
                builder.append(',');
                builder.append(scale.get());
            }
            builder.append(')');
            return builder.toString();
        }

        @Override
        public RegisterSize getSize() {
            return Util.getSize(Mode.getP());
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
            // todo this is quite heavy handed
            return Stream
                    .concat(baseRegister.stream(), indexRegister.stream())
                    .map(register -> register.get())
                    .collect(Collectors.toList());
        }
    }
}
