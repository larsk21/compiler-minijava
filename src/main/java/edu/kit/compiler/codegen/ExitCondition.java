package edu.kit.compiler.codegen;

import java.util.ArrayList;
import java.util.List;

import edu.kit.compiler.codegen.BasicBlocks.BlockEntry;
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
     * Translate the exit condition into a list of IL instructions.  For an
     * unconditional jump, this return something `jmp .L42`. For a conditional
     * jump it might return `cmp @1 @2; jl .L42; jmp .L28`.
     */
    public abstract List<Instruction> getInstructions();

    /**
     * Sets the destination of the condition if it evaluates to true. Also used
     * to set the destination for an unconditional jump.
     */
    public abstract void setTrueBlock(BasicBlocks.BlockEntry block);

    /**
     * Sets the destination of the condition if it evaluates to false.
     */
    public abstract void setFalseBlock(BasicBlocks.BlockEntry block);

    public static ExitCondition unconditional() {
        return new UnconditionalJump();
    }

    public static ExitCondition conditional(Relation relation,
            Operand.Source left, Operand.Source right) {
        return new ConditionalJump(relation, left, right);
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class UnconditionalJump extends ExitCondition {

        private BasicBlocks.BlockEntry block;

        @Override
        public List<Instruction> getInstructions() {
            assert block != null;
            return List.of(Instruction.newJmp(
                    Util.formatJmp("jmp", block.getLabel()),
                    block.getLabel()));
        }

        @Override
        public void setTrueBlock(BlockEntry block) {
            this.block = block;
        }

        @Override
        public void setFalseBlock(BlockEntry block) {
            throw new UnsupportedOperationException();
        }

    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class ConditionalJump extends ExitCondition {

        private final Relation relation;
        private final Operand.Source target;
        private final Operand.Source source;

        private BasicBlocks.BlockEntry trueBlock;
        private BasicBlocks.BlockEntry falseBlock;

        @Override
        public List<Instruction> getInstructions() {
            assert trueBlock != null && falseBlock != null;

            return switch (relation) {
                case True -> new UnconditionalJump(trueBlock).getInstructions();
                case False -> new UnconditionalJump(falseBlock).getInstructions();
                case LessEqualGreater -> new UnconditionalJump(trueBlock).getInstructions();
                default -> List.of(
                        Instruction.newInput(
                                Util.formatCmd("cmp", target.getSize(), source, target),
                                getInputRegisters(source, target)),
                        Instruction.newJmp(
                                Util.formatJmp(getJmpCmd(), trueBlock.getLabel()),
                                trueBlock.getLabel()),
                        Instruction.newJmp(
                                Util.formatJmp("jmp", falseBlock.getLabel()),
                                falseBlock.getLabel()));
            };
        }

        @Override
        public void setTrueBlock(BlockEntry block) {
            this.trueBlock = block;
        }

        @Override
        public void setFalseBlock(BlockEntry block) {
            this.falseBlock = block;
        }

        private String getJmpCmd() {
            if (target.getMode().isSigned()) {
                return getSignedJmpCmd(relation);
            } else {
                return getUnsignedJmpCmd(relation);
            }
        }

        private static List<Integer> getInputRegisters(Operand.Source lhs, Operand.Source rhs) {
            var registers = new ArrayList<Integer>();
            registers.addAll(lhs.getSourceRegisters());
            registers.addAll(rhs.getSourceRegisters());

            return registers;
        }

        private static String getSignedJmpCmd(Relation relation) {
            return switch (relation) {
                case Equal -> "je";
                case Greater -> "jg";
                case GreaterEqual -> "jge";
                case Less -> "jl";
                case LessEqual -> "jle";
                case LessGreater -> "jne";

                // Specially handled relations
                case True, False, LessEqualGreater -> throw new IllegalStateException();

                // Unordered relation not needed for our purposes
                default -> throw new IllegalStateException();
            };
        }

        private static String getUnsignedJmpCmd(Relation relation) {
            return switch (relation) {
                case Equal -> "je";
                case Greater -> "ja";
                case GreaterEqual -> "jae";
                case Less -> "jb";
                case LessEqual -> "jbe";
                case LessGreater -> "jne";

                // Specially handled relations
                case True, False, LessEqualGreater -> throw new IllegalStateException();

                // Unordered relation not needed for our purposes
                default -> throw new IllegalStateException();
            };
        }
    }
}
