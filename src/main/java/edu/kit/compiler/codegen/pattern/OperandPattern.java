package edu.kit.compiler.codegen.pattern;

import java.util.Collections;
import java.util.List;

import edu.kit.compiler.codegen.MatcherState;
import edu.kit.compiler.codegen.Operand;
import edu.kit.compiler.codegen.Operand.Immediate;
import edu.kit.compiler.codegen.Operand.Memory;
import edu.kit.compiler.codegen.Operand.Register;
import firm.Mode;
import firm.bindings.binding_irnode.ir_opcode;
import firm.nodes.Const;
import firm.nodes.Node;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * A collection of patterns for various basic operands.
 */
public final class OperandPattern {

    /**
     * Return a pattern that will match any Const node.
     */
    public static Pattern<OperandMatch<Immediate>> immediate() {
        return new ImmediatePattern();
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

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class ImmediatePattern implements Pattern<OperandMatch<Immediate>> {
        @Override
        public OperandMatch<Immediate> match(Node node, MatcherState matcher) {
            if (node.getOpCode() == ir_opcode.iro_Const) {
                var value = ((Const) node).getTarval();
                var operand = Operand.immediate(value);
                return OperandMatch.some(operand, Collections.emptyList());
            } else {
                return OperandMatch.none();
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
