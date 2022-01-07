package edu.kit.compiler.codegen.pattern;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import edu.kit.compiler.codegen.MatcherState;
import edu.kit.compiler.codegen.Operand;
import edu.kit.compiler.codegen.Util;
import edu.kit.compiler.codegen.Operand.Immediate;
import edu.kit.compiler.codegen.Operand.Memory;
import edu.kit.compiler.codegen.Operand.Register;
import edu.kit.compiler.intermediate_lang.RegisterSize;
import firm.TargetValue;
import firm.bindings.binding_irnode.ir_opcode;
import firm.nodes.Add;
import firm.nodes.Const;
import firm.nodes.Mul;
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
        return OperandPattern.immediate(RegisterSize.DOUBLE);
    }

    /**
     * Return a pattern that will match any Const node, as well as Conv nodes
     * with Const operand. The pattern only matches if the constant fits into
     * a register with the given size.
     */
    public static Pattern<OperandMatch<Immediate>> immediate(RegisterSize maxSize) {
        return new ImmediatePattern((TargetValue value) -> {
            return !Util.isOverflow(value, maxSize);
        });
    }

    /**
     * Returns a pattern that will match any Const node for whose TargetValue
     * the given predicate returns true.
     */
    public static Pattern<OperandMatch<Immediate>> immediate(
            Predicate<TargetValue> predicate) {
        return new ImmediatePattern(predicate);
    }

    /**
     * Return a pattern that will match any Const node that can be used as scale
     * in address, i.e. the values 1, 2, 4, or 8.
     */
    public static Pattern<OperandMatch<Immediate>> addressScale() {
        return new ImmediatePattern((TargetValue value) -> {
            var longValue = value.asLong();
            return longValue == 1 || longValue == 2
                    || longValue == 4 || longValue == 8;
        });
    }

    /**
     * Return a pattern that will match any Const of value zero.
     */
    public static Pattern<OperandMatch<Immediate>> zero() {
        return new ImmediatePattern(TargetValue::isNull);
    }

    /**
     * Return a pattern that will match any node for which a register can be
     * found using `MatcherState#getRegister(Node)`.
     */
    public static Pattern<OperandMatch<Register>> register() {
        return new RegisterPattern();
    }

    /**
     * Return a pattern that will match memory locations. An effort will be made
     * to utilize x86 addressing modes where possible.
     */
    public static Pattern<OperandMatch<Memory>> memory() {
        return new MemoryPattern();
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class ImmediatePattern implements Pattern<OperandMatch<Immediate>> {

        private final Predicate<TargetValue> predicate;

        @Override
        public OperandMatch<Immediate> match(Node node, MatcherState matcher) {
            return switch (node.getOpCode()) {
                case iro_Const -> checkPredicate(matchConst(node, matcher));
                case iro_Conv -> checkPredicate(matchConv(node, matcher));
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

        private OperandMatch<Immediate> checkPredicate(OperandMatch<Immediate> match) {
            if (match.matches()) {
                if (predicate.test(match.getOperand().get())) {
                    return match;
                } else {
                    return OperandMatch.none();
                }
            } else {
                return match;
            }
        }
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class RegisterPattern implements Pattern<OperandMatch<Register>> {

        private static final Pattern<OperandMatch<Immediate>> IMMEDIATE = OperandPattern.immediate(RegisterSize.QUAD);

        @Override
        public OperandMatch<Register> match(Node node, MatcherState matcher) {
            var immediateMatch = IMMEDIATE.match(node, matcher);
            if (immediateMatch.matches()) {
                var immediate = immediateMatch.getOperand();
                var operand = Operand.immediateRegister(immediate,
                        matcher.getNewRegister(immediate.getSize()));
                return OperandMatch.some(operand, Collections.emptyList());
            } else {
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
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class MemoryPattern implements Pattern<OperandMatch<Memory>> {

        private static final Pattern<OperandMatch<Immediate>> OFFSET = OperandPattern.immediate(RegisterSize.DOUBLE);
        private static final Pattern<OperandMatch<Immediate>> SCALE = OperandPattern.addressScale();
        private static final Pattern<OperandMatch<Register>> REGISTER = OperandPattern.register();

        @Override
        public OperandMatch<Memory> match(Node node, MatcherState matcher) {
            // We make some simplifying assumptions here. If these assumptions
            // are not upheld, worse addressing modes may be used as a result.
            // If arithmetic identities have been eliminated, these assumptions
            // will always be upheld.
            // - Constants should always be right side of addition (x + c)
            // - Constants should always be right side of multiplication (x * c)
            // - Subtractions of constants should not be used (x - c == x + (-c))

            var nodes = AddressNodes.of(node);

            var indexRight = matchMul(nodes.offset, nodes.firstRegister,
                    nodes.secondRegister, matcher);
            if (indexRight.matches()) {
                return indexRight;
            }

            var indexLeft = matchMul(nodes.offset, nodes.secondRegister,
                    nodes.firstRegister, matcher);
            if (indexLeft.matches()) {
                return indexLeft;
            }

            // ! possible improvement: (2,4,8) + 1 * %rax = (%rax,%rax,8)
            var match = getMatch(nodes.offset, nodes.firstRegister,
                    nodes.secondRegister, matcher);
            if (!match.matches()) {
                // fallback if something goes awry
                var register = REGISTER.match(node, matcher);
                var operand = Operand.memory(Optional.empty(),
                        Optional.of(register.getOperand()),
                        Optional.empty(), Optional.empty());
                var predecessors = register.getPredecessors()
                        .collect(Collectors.toList());
                return OperandMatch.some(operand, predecessors);
            } else {
                return match;
            }
        }

        /**
         * Try to match a memory addressing mode where `index` is a
         * multiplication with one of the scaling factors 1, 2, 4, or 8.
         */
        private OperandMatch<Memory> matchMul(Node offset, Node base, Node index, MatcherState matcher) {
            if (index != null && index.getOpCode() == ir_opcode.iro_Mul) {
                var mul = (Mul) index;
                var scaleMatch = SCALE.match(mul.getRight(), matcher);
                if (scaleMatch.matches()) {
                    return getMatch(Optional.ofNullable(offset),
                            Optional.ofNullable(base), Optional.of(mul.getLeft()),
                            Optional.of(scaleMatch.getOperand()), matcher);
                } else {
                    return OperandMatch.none();
                }
            } else {
                return OperandMatch.none();
            }
        }

        /**
         * Wraps the given nodes (any of which may be null) with Optionals and
         * passes them to the other overload of `getMatch`.
         */
        private OperandMatch<Memory> getMatch(Node offset, Node base, Node index, MatcherState matcher) {
            return getMatch(Optional.ofNullable(offset), Optional.ofNullable(base),
                    Optional.ofNullable(index), Optional.empty(), matcher);
        }

        /**
         * Try to match memory mode with the given parts. Any number of the
         * given Optionals may be empty. The caller must ensure that the present
         * values are a valid combination to avoid exceptions. An example for
         * an invalid combination might be if only base and scale are present.
         */
        private OperandMatch<Memory> getMatch(Optional<Node> offset, Optional<Node> base,
                Optional<Node> index, Optional<Immediate> scale, MatcherState matcher) {
            var offsetMatch = offset.map(node -> OFFSET.match(node, matcher));
            var baseMatch = base.map(node -> REGISTER.match(node, matcher));
            var indexMatch = index.map(node -> REGISTER.match(node, matcher));

            if (matches(offsetMatch) && matches(baseMatch) && matches(indexMatch)) {
                var memory = Operand.memory(
                        offsetMatch.map(m -> m.getOperand().get().asInt()),
                        baseMatch.map(m -> m.getOperand()),
                        indexMatch.map(m -> m.getOperand()),
                        scale.map(m -> m.get().asInt()));
                var preds = Stream
                        .concat(baseMatch.stream(), indexMatch.stream())
                        .flatMap(m -> m.getPredecessors())
                        .collect(Collectors.toList());

                return OperandMatch.some(memory, preds);
            } else {
                return OperandMatch.none();
            }
        }

        private static final boolean matches(Optional<? extends Match> match) {
            return match.map(m -> m.matches()).orElse(true);
        }
    }

    /**
     * Represents the additive parts of a memory address. The field of this
     * class are explicitly allowed to be null.
     */
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE, staticName = "of")
    private static final class AddressNodes {

        public final Node firstRegister;
        public final Node secondRegister;
        public final Node offset;

        public static AddressNodes of(Node node) {
            if (node.getOpCode() == ir_opcode.iro_Add) {
                var add = (Add) node;
                if (isOffset(add.getRight())) {
                    var offset = add.getRight();
                    if (add.getLeft().getOpCode() == ir_opcode.iro_Add) {
                        // Add(Add(r1, r2), c)
                        return of(add.getLeft().getPred(0), add.getLeft().getPred(1), offset);
                    } else {
                        // Add(r1, c)
                        return of(add.getLeft(), null, offset);
                    }
                } else if (isAddConst(add.getLeft())) {
                    // Add(Add(r1, c), r2)
                    return of(add.getLeft().getPred(0), add.getRight(), add.getLeft().getPred(1));
                } else if (isAddConst(add.getRight())) {
                    // Add(r1, Add(r2, c))
                    return of(add.getLeft(), add.getRight().getPred(0), add.getRight().getPred(1));
                } else {
                    // Add(r1, r2)
                    return of(add.getLeft(), add.getRight(), null);
                }
            } else {
                // r1
                return of(node, null, null);
            }
        }

        private static boolean isAddConst(Node node) {
            return node.getOpCode() == ir_opcode.iro_Add
                    && isOffset(node.getPred(1));
        }

        private static boolean isOffset(Node node) {
            if (node.getOpCode() == ir_opcode.iro_Const) {
                TargetValue value = ((Const) node).getTarval();
                return !Util.isOverflow(value, RegisterSize.DOUBLE);
            } else {
                return false;
            }
        }
    }
}
