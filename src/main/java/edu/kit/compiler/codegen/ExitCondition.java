package edu.kit.compiler.codegen;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import edu.kit.compiler.codegen.BasicBlocks.BlockEntry;
import edu.kit.compiler.intermediate_lang.Instruction;
import firm.Mode;
import firm.Relation;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

public abstract class ExitCondition {

    public abstract List<Instruction> getInstructions();

    public abstract void setTrueBlock(BasicBlocks.BlockEntry block);

    public abstract void setFalseBlock(BasicBlocks.BlockEntry block);

    public static ExitCondition unconditional() {
        return new UnconditionalJump();
    }

    public static ExitCondition condition(Relation relation,
            Operand.Register left, Operand.Register right) {
        return new ConditionalJump(relation, left, right, right.getMode());
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class UnconditionalJump extends ExitCondition {

        private BasicBlocks.BlockEntry block;

        @Override
        public List<Instruction> getInstructions() {
            assert block != null;
            return Arrays.asList(Instruction.newJmp(
                    Util.formatJmp("jmp", block.getLabel()),
                    Optional.empty()));
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
        private final Operand.Register destination;
        private final Operand.Register source;
        private final Mode mode;

        private BasicBlocks.BlockEntry trueBlock;
        private BasicBlocks.BlockEntry falseBlock;

        @Override
        public List<Instruction> getInstructions() {
            assert trueBlock != null && falseBlock != null;

            // todo what about unsigned, i.e. booleans?
            // todo should we reverse jump conditions?
            return switch (relation) {
                case True -> new UnconditionalJump(trueBlock).getInstructions();
                case False -> new UnconditionalJump(falseBlock).getInstructions();
                case LessEqualGreater -> new UnconditionalJump(trueBlock).getInstructions();
                default -> Arrays.asList(
                        Instruction.newInput(
                                Util.formatCmd("cmp", Util.getSize(mode), source, destination),
                                Arrays.asList(source.get(), destination.get())),
                        Instruction.newJmp(
                                Util.formatJmp(getJmpCmd(), trueBlock.getLabel()),
                                Optional.empty()),
                        Instruction.newJmp(
                                Util.formatJmp("jmp", falseBlock.getLabel()),
                                Optional.empty()));
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
            if (destination.getMode().isSigned()) {
                return getSignedJmpCmd(relation);
            } else {
                return getUnsignedJmpCmd(relation);
            }
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
