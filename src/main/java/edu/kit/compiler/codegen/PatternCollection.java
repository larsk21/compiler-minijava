package edu.kit.compiler.codegen;

import static firm.bindings.binding_irnode.ir_opcode.*;

import java.util.List;
import java.util.Map;

import edu.kit.compiler.codegen.Operand.Immediate;
import edu.kit.compiler.codegen.Operand.Memory;
import edu.kit.compiler.codegen.Operand.Register;
import edu.kit.compiler.codegen.pattern.BinaryInstructionPattern;
import edu.kit.compiler.codegen.pattern.BlockPattern;
import edu.kit.compiler.codegen.pattern.CallPattern;
import edu.kit.compiler.codegen.pattern.ConditionPattern;
import edu.kit.compiler.codegen.pattern.ConversionPattern;
import edu.kit.compiler.codegen.pattern.DivisionPattern;
import edu.kit.compiler.codegen.pattern.DivisionPattern.Type;
import edu.kit.compiler.codegen.pattern.InstructionMatch;
import edu.kit.compiler.codegen.pattern.LoadEffectivePattern;
import edu.kit.compiler.codegen.pattern.LoadMemoryPattern;
import edu.kit.compiler.codegen.pattern.OperandMatch;
import edu.kit.compiler.codegen.pattern.OperandPattern;
import edu.kit.compiler.codegen.pattern.Pattern;
import edu.kit.compiler.codegen.pattern.PhiPattern;
import edu.kit.compiler.codegen.pattern.ReturnPattern;
import edu.kit.compiler.codegen.pattern.UnaryInstructionPattern;
import edu.kit.compiler.codegen.pattern.UnknownPattern;
import edu.kit.compiler.intermediate_lang.RegisterSize;
import edu.kit.compiler.io.CommonUtil;

import firm.bindings.binding_irnode.ir_opcode;
import firm.nodes.Node;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

public class PatternCollection implements Pattern<InstructionMatch> {

    private static final Pattern<OperandMatch<Immediate>> IMM8 = OperandPattern.immediate(RegisterSize.BYTE);
    private static final Pattern<OperandMatch<Immediate>> IMM = OperandPattern.immediate();
    private static final Pattern<OperandMatch<Register>> REG = OperandPattern.register();
    private static final Pattern<OperandMatch<Memory>> MEM = OperandPattern.memory();

    private final Map<ir_opcode, Pattern<InstructionMatch>> map;

    @Override
    public InstructionMatch match(Node node, MatcherState matcher) {
        var pattern = map.get(node.getOpCode());
        if (pattern != null) {
            return pattern.match(node, matcher);
        } else {
            return InstructionMatch.none();
        }
    }

    public PatternCollection() {
        map = Map.ofEntries(
                Map.entry(iro_Add, new CompoundPattern(List.of(
                        new LoadEffectivePattern(),
                        new ArithmeticPattern(iro_Add, "add", 0, true)))),
                Map.entry(iro_Sub, new ArithmeticPattern(iro_Sub, "sub", 0, false)),
                Map.entry(iro_Mul, new CompoundPattern(List.of(
                    new LoadEffectivePattern(),
                    new ArithmeticPattern(iro_Mul, "imul", 0, true)))),
                Map.entry(iro_And, new ArithmeticPattern(iro_And, "and", 0, true)),
                Map.entry(iro_Eor, new ArithmeticPattern(iro_Eor, "xor", 0, true)),

                Map.entry(iro_Shl, new BinaryInstructionPattern(iro_Shl, "shl", REG, IMM8, 0, false)),
                Map.entry(iro_Shr, new BinaryInstructionPattern(iro_Shr, "shr", REG, IMM8, 0, false)),
                Map.entry(iro_Shrs, new BinaryInstructionPattern(iro_Shrs, "sar", REG, IMM8, 0, false)),

                Map.entry(iro_Div, new DivisionPattern(Type.DIV)),
                Map.entry(iro_Mod, new DivisionPattern(Type.MOD)),

                Map.entry(iro_Minus, new UnaryInstructionPattern(iro_Minus, "neg", REG, 0)),

                Map.entry(iro_Conv, new ConversionPattern()),

                Map.entry(iro_Store, new CompoundPattern(List.of(
                        new BinaryInstructionPattern(iro_Store, "mov", MEM, IMM, 1, false),
                        new BinaryInstructionPattern(iro_Store, "mov", MEM, REG, 1, false)))),
                Map.entry(iro_Load, new LoadMemoryPattern()),

                Map.entry(iro_Call, new CallPattern()),
                Map.entry(iro_Phi, new PhiPattern()),
                Map.entry(iro_Block, new BlockPattern()),
                Map.entry(iro_Return, new ReturnPattern()),

                Map.entry(iro_Jmp, new ConditionPattern.Unconditional()),
                Map.entry(iro_Cond, new CompoundPattern(List.of(
                        new ConditionPattern.Test(true),
                        new ConditionPattern.Comparison<>(REG, IMM, true),
                        new ConditionPattern.Comparison<>(REG, REG, false),
                        new ConditionPattern.Unknown()))),

                // nodes with constant values for which we never generate instructions
                Map.entry(iro_Const, new EmptyPattern()),
                Map.entry(iro_Address, new EmptyPattern()),

                // nodes for which we never need to generate instructions
                Map.entry(iro_Start, new EmptyPattern()),
                Map.entry(iro_End, new InheritingPattern()),
                Map.entry(iro_Cmp, new InheritingPattern()),
                Map.entry(iro_Sync, new InheritingPattern()),
                Map.entry(iro_NoMem, new EmptyPattern()),

                // handle Unknown nodes
                Map.entry(iro_Unknown, new UnknownPattern()));
    }

    /**
     * A pattern which always returns a match with no predecessors.
     */
    private static final class EmptyPattern implements Pattern<InstructionMatch> {
        @Override
        public InstructionMatch match(Node node, MatcherState matcher) {
            return InstructionMatch.empty(node);
        }
    }

    /**
     * A pattern which always returns a match that inherits the predecessors of
     * the given Firm node.
     */
    private static final class InheritingPattern implements Pattern<InstructionMatch> {
        @Override
        public InstructionMatch match(Node node, MatcherState matcher) {
            return InstructionMatch.empty(node, CommonUtil.toList(node.getPreds()));
        }
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private static class CompoundPattern implements Pattern<InstructionMatch> {

        private final List<Pattern<InstructionMatch>> patterns;

        @Override
        public InstructionMatch match(Node node, MatcherState matcher) {
            for (var pattern : patterns) {
                var shadow = new MatcherShadow(matcher);
                var match = pattern.match(node, shadow);
                if (match.matches()) {
                    shadow.merge();
                    return match;
                }
            }
            return InstructionMatch.none();
        }
    }

    private static final class ArithmeticPattern implements Pattern<InstructionMatch> {

        private final CompoundPattern patterns;

        public ArithmeticPattern(ir_opcode opcode, String command, int offset, boolean commutate) {
            this.patterns = new CompoundPattern(List.of(
                    new BinaryInstructionPattern(opcode, command, REG, IMM, offset, commutate),
                    new BinaryInstructionPattern(opcode, command, REG, REG, offset, false)));
        }

        @Override
        public InstructionMatch match(Node node, MatcherState matcher) {
            return patterns.match(node, matcher);
        }
    }

    private static final class MatcherShadow extends MatcherState {

        private final MatcherState subject;

        public MatcherShadow(MatcherState subject) {
            super(subject.graph);
            this.subject = subject;
        }

        public void merge() {
            subject.phiRegisters.putAll(this.phiRegisters);
            subject.registerSizes.addAll(this.registerSizes);
        }

        @Override
        public int getNRegisters() {
            return subject.getNRegisters() + super.getNRegisters();
        }

        @Override
        public int getNewRegister(RegisterSize size) {
            var register = super.getNewRegister(size);
            return subject.getNRegisters() + register;
        }

        @Override
        public InstructionMatch getMatch(Node node) {
            return subject.getMatch(node);
        }

        @Override
        public RegisterSize getRegisterSize(int register) {
            var pivot = subject.getNRegisters();
            if (register < pivot) {
                return subject.getRegisterSize(register);
            } else {
                return super.getRegisterSize(register - pivot);
            }
        }

        @Override
        protected int peekPhiRegister(Node phi) {
            var existing = subject.peekPhiRegister(phi);
            if (existing == -1) {
                return super.peekPhiRegister(phi);
            } else {
                return existing;
            }
        }

        @Override
        public void setMatch(Node node, InstructionMatch match) {
            throw new UnsupportedOperationException("not supported on shadow");
        }
    }
}
