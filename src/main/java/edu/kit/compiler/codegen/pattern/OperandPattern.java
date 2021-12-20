package edu.kit.compiler.codegen.pattern;

import java.util.Collections;
import java.util.List;

import edu.kit.compiler.codegen.MatcherState;
import edu.kit.compiler.codegen.Operand;
import edu.kit.compiler.codegen.Operand.Immediate;
import edu.kit.compiler.codegen.Operand.Memory;
import edu.kit.compiler.codegen.Operand.Register;
import firm.Mode;
import firm.nodes.Const;
import firm.nodes.Node;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

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
        @Override
        public OperandMatch<Memory> match(Node node, MatcherState matcher) {
            var register = matcher.getRegister(node);
            if (node.getMode().equals(Mode.getP()) && register.isPresent()) {
                var operand = Operand.register(node.getMode(), register.get());
                return OperandMatch.some(Operand.memory(operand), List.of(node));
            } else {
                return OperandMatch.none();
            }
        }
    }
}
