package edu.kit.compiler.codegen;

import java.util.ArrayList;
import java.util.List;

import edu.kit.compiler.intermediate_lang.Instruction;
import firm.Relation;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

/**
 * Represents the exit condition of a basic block.
 */
public abstract class ExitCondition {

    /**
     * Translate the exit condition into a list of IL instructions. For an
     * unconditional jump, this return something `jmp .L42`. For a conditional
     * jump it might return `cmp @1 @2; jl .L42; jmp .L28`.
     */
    public abstract List<Instruction> getInstructions();

    /**
     * Sets the destination of the condition if it evaluates to true. Also used
     * to set the destination for an unconditional jump.
     */
    public abstract void setTrueBlock(int label);

    /**
     * Sets the destination of the condition if it evaluates to false.
     */
    public abstract void setFalseBlock(int label);

    /**
     * Replaces a destination block with a new block.
     */
    public abstract void replaceBlock(int oldId, int newId);

    public abstract boolean isUnconditional();

    /**
     * Returns an ExitCondition that always jumps to the trueBlock.
     */
    public static ExitCondition unconditional() {
        return new UnconditionalJump();
    }

    /**
     * Returns an ExitCondition that compares the two operands using the given
     * relation and jumps to the true or false block accordingly.
     */
    public static ExitCondition comparison(Relation relation,
            Operand.Source left, Operand.Source right) {
        return new Comparison(relation, left, right);
    }

    /**
     * Returns an ExitCondition that compares the operand to zero and jumps to
     * the true or false block accordingly (uses test instruction).
     */
    public static ExitCondition test(Relation relation, Operand.Source operand) {
        return new Test(relation, operand);
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class UnconditionalJump extends ExitCondition {
        private int label = -1;

        @Override
        public List<Instruction> getInstructions() {
            assert label >= 0;
            return List.of(Instruction.newJmp(
                    Util.formatJmp("jmp", label), label)
            );
        }

        @Override
        public void setTrueBlock(int label) {
            this.label = label;
        }

        @Override
        public void setFalseBlock(int label) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void replaceBlock(int oldId, int newId) {
            assert label == oldId;
            label = newId;
        }

        @Override
        public boolean isUnconditional() {
            return true;
        }
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static abstract class ConditionalJump extends ExitCondition {

        protected final Relation relation;

        private int trueLabel = -1;
        private int falseLabel = -1;

        @Override
        public void setTrueBlock(int label) {
            this.trueLabel = label;
        }

        @Override
        public void setFalseBlock(int label) {
            this.falseLabel = label;
        }

        @Override
        public void replaceBlock(int oldId, int newId) {
            if (trueLabel == oldId) {
                trueLabel = newId;
            } else {
                assert falseLabel == oldId;
                falseLabel = newId;
            }
        }

        @Override
        public boolean isUnconditional() {
            return false;
        }

        @Override
        public List<Instruction> getInstructions() {
            return switch (relation) {
                case True -> asUnconditional(true).getInstructions();
                case False -> asUnconditional(false).getInstructions();
                case LessEqualGreater -> asUnconditional(true).getInstructions();
                default -> List.of(
                        getCmpInstruction(),
                        Instruction.newJmp(Util.formatJmp(getTrueJump(), trueLabel), trueLabel),
                        Instruction.newJmp(Util.formatJmp("jmp", falseLabel), falseLabel));
            };
        }

        protected UnconditionalJump asUnconditional(boolean value) {
            return new UnconditionalJump(value ? trueLabel : falseLabel);
        }

        protected abstract String getTrueJump();

        protected abstract Instruction getCmpInstruction();
    }

    public static final class Comparison extends ConditionalJump {

        private final Operand.Source first;
        private final Operand.Source second;

        public Comparison(Relation relation, Operand.Source first, Operand.Source second) {
            super(relation);
            this.first = first;
            this.second = second;
        }

        @Override
        public Instruction getCmpInstruction() {
            var inputRegisters = new ArrayList<>(first.getSourceRegisters());
            inputRegisters.addAll(second.getSourceRegisters());
            return Instruction.newInput(
                    Util.formatCmd("cmp", first.getSize(), second, first),
                    inputRegisters);
        }

        @Override
        protected String getTrueJump() {
            var isSigned = first.getMode().isSigned();
            return switch (relation) {
                case Equal -> "je";
                case Greater -> isSigned ? "jg" : "ja";
                case GreaterEqual -> isSigned ? "jge" : "jae";
                case Less -> isSigned ? "jl" : "jb";
                case LessEqual -> isSigned ? "jle" : "jbe";
                case LessGreater -> "jne";

                // Specially handled relations
                case True, False, LessEqualGreater -> throw new IllegalStateException();

                // Unordered relation not needed for our purposes
                default -> throw new IllegalStateException();
            };
        }
    }

    public static final class Test extends ConditionalJump {

        private final Operand.Source operand;

        public Test(Relation relation, Operand.Source operand) {
            super(relation);
            this.operand = operand;
        }

        @Override
        public List<Instruction> getInstructions() {
            if (operand.getMode().isSigned()) {
                return super.getInstructions();
            } else {
                return switch (relation) {
                    case GreaterEqual -> asUnconditional(true).getInstructions();
                    case Less -> asUnconditional(false).getInstructions();
                    default -> super.getInstructions();
                };
            }
        }

        @Override
        protected Instruction getCmpInstruction() {
            return Instruction.newInput(
                    Util.formatCmd("test", operand.getSize(), operand, operand),
                    operand.getSourceRegisters());
        }

        @Override
        protected String getTrueJump() {
            var isSigned = operand.getMode().isSigned();
            return switch (relation) {
                case Equal -> "jz";
                case Greater -> isSigned ? "jg" : "ja";
                case LessGreater -> "jnz";

                // either always true or false if unsigned
                case GreaterEqual -> ifSigned(isSigned, "jns");
                case Less -> ifSigned(isSigned, "js");
                case LessEqual -> isSigned ? "jle" : "jz";

                // Specially handled relations
                case True, False, LessEqualGreater -> throw new IllegalStateException();

                // Unordered relation not needed for our purposes
                default -> throw new IllegalStateException();
            };
        }

        private String ifSigned(boolean signed, String command) {
            if (signed) {
                return command;
            } else {
                throw new IllegalStateException();
            }
        }
    }
}
