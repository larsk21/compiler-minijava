package edu.kit.compiler.codegen.pattern;

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import edu.kit.compiler.codegen.MatcherState;
import edu.kit.compiler.codegen.Operand;
import edu.kit.compiler.codegen.Operand.Immediate;
import edu.kit.compiler.codegen.Operand.Memory;
import edu.kit.compiler.codegen.Operand.Register;
import edu.kit.compiler.intermediate_lang.RegisterSize;
import firm.Mode;
import firm.TargetValue;
import firm.bindings.binding_irnode.ir_opcode;
import firm.nodes.Const;
import firm.nodes.Node;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

/**
 * A collection of patterns for various basic operands.
 */
public final class OperandPattern {

    /**
     * Equivalent to `Operand.immediate(DOUBLE)`.
     */
    public static Pattern<OperandMatch<Immediate>> immediate() {
        return new ImmediatePattern(RegisterSize.DOUBLE);
    }

    /**
     * Return a pattern that will match any Const node, as well as Conv nodes
     * with Const operand. The pattern only matches if the constant fits into
     * a register with the given size.
     */
    public static Pattern<OperandMatch<Immediate>> immediate(RegisterSize maxSize) {
        return new ImmediatePattern(maxSize);
    }

    /**
     * Return a pattern that will match any node for which a register can be
     * found using `MatcherState#getRegister(Node)`.
     */
    public static Pattern<OperandMatch<Register>> register() {
        return new RegisterPattern();
    }

    public static Pattern<OperandMatch<Memory>> memory() {
        return new MemoryPattern();
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class ImmediatePattern implements Pattern<OperandMatch<Immediate>> {

        private final RegisterSize maxSize;

        @Override
        public OperandMatch<Immediate> match(Node node, MatcherState matcher) {
            return switch (node.getOpCode()) {
                case iro_Const -> checkSize(matchConst(node, matcher));
                case iro_Conv -> checkSize(matchConv(node, matcher));
                default -> OperandMatch.none();
            };
        }

        private OperandMatch<Immediate> matchConst(Node node, MatcherState matcher) {
            var value = ((Const) node).getTarval();
            var operand = Operand.immediate(value);
            return OperandMatch.some(operand, Collections.emptyList());
        }

        private OperandMatch<Immediate> matchConv(Node node, MatcherState matcher) {
            var match = this.match(node.getPred(0), matcher);
            if (match.matches()) {
                var value = match.getOperand().get();
                var operand = Operand.immediate(value.convertTo(node.getMode()));
                return OperandMatch.some(operand, Collections.emptyList());
            } else {
                return OperandMatch.none();
            }
        }

        private OperandMatch<Immediate> checkSize(OperandMatch<Immediate> match) {
            if (match.matches()) {
                if (isOverflow(match.getOperand().get())) {
                    return OperandMatch.none();
                } else {
                    return match;
                }
            } else {
                return match;
            }
        }

        private boolean isOverflow(TargetValue value) {
            // based on is_overflow(..) in libfirm/ir/tv/tv.c
            if (value.getMode().isSigned()) {
                // if sign bit is set, all upper bits must be zro
                return value.highest_bit() >= maxSize.getBits() - 1
                        && value.not().highest_bit() >= maxSize.getBits() - 1;
            } else {
                // overflow if any of the upper bits is zero
                return value.highest_bit() >= maxSize.getBits();
            }
        }
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class RegisterPattern implements Pattern<OperandMatch<Register>> {
        @Override
        public OperandMatch<Register> match(Node node, MatcherState matcher) {
            var register = matcher.getRegister(node);
            if (register.isPresent()) {
                var predecessors = List.of(node);
                var operand = Operand.register(node.getMode(), register.get());
                return OperandMatch.some(operand, predecessors);
            } else {
                return OperandMatch.none();
            }
        }
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class MemoryPattern implements Pattern<OperandMatch<Memory>> {

        private static final Pattern<OperandMatch<Immediate>> IMM32 = OperandPattern.immediate(RegisterSize.DOUBLE);
        private static final Pattern<OperandMatch<Register>> REG = OperandPattern.register();

        @Override
        public OperandMatch<Memory> match(Node node, MatcherState matcher) {
            if (node.getMode().equals(Mode.getP())) {
                var addOffset = matchAddOffset(node, matcher);
                if (addOffset.matches()) {
                    return addOffset;
                } else {
                    return matchBaseOnly(node, matcher);
                }
            } else {
                return OperandMatch.none();
            }
        }

        private OperandMatch<Memory> matchBaseOnly(Node node, MatcherState matcher) {
            var registerMatch = REG.match(node, matcher);
            if (registerMatch.matches()) {
                var memory = Operand.memory(registerMatch.getOperand());
                return OperandMatch.some(memory, List.of(node));
            } else {
                return OperandMatch.none();
            }
        }

        private OperandMatch<Memory> matchAddOffset(Node node, MatcherState matcher) {
            if (node.getOpCode() == ir_opcode.iro_Add
                    && node.getPred(1).getOpCode() == ir_opcode.iro_Const) {
                // only consider constant on rhs because of normalization
                // performed in ArithmeticIdentitiesOptimization
                var baseMatch = REG.match(node.getPred(0), matcher);
                var offsetMatch = IMM32.match(node.getPred(1), matcher);
                if (baseMatch.matches() && offsetMatch.matches()) {
                    var baseRegister = baseMatch.getOperand();
                    var offsetValue = offsetMatch.getOperand().get();
                    var memory = Operand.memory(offsetValue.asInt(), baseRegister);
                    return OperandMatch.some(memory, List.of(node.getPred(0)));
                } else {
                    return OperandMatch.none();
                }
            } else {
                return OperandMatch.none();
            }
        }
    }
}
