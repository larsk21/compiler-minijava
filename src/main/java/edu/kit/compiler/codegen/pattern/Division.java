package edu.kit.compiler.codegen.pattern;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import edu.kit.compiler.codegen.MatcherState;
import edu.kit.compiler.codegen.Operand;
import edu.kit.compiler.intermediate_lang.Instruction;
import firm.bindings.binding_irnode.ir_opcode;
import firm.nodes.Node;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class Division implements Pattern<InstructionMatch> {

    private final Type type;
    private final Pattern<OperandMatch<Operand.Register>> left;
    private final Pattern<OperandMatch<Operand.Register>> right;

    @Override
    public InstructionMatch match(Node node, MatcherState matcher) {
        if (node.getOpCode() == type.getOpcode()) {
            assert node.getPredCount() == 3;

            var lhs = left.match(node.getPred(1), matcher);
            var rhs = right.match(node.getPred(2), matcher);
            var destination = matcher.getNewRegister();

            if (lhs.matches() && rhs.matches()) {
                return new DivisionMatch(node, lhs, rhs, destination);
            } else {
                return InstructionMatch.none();
            }
        } else {
            return InstructionMatch.none();
        }
    }

    public static enum Type {
        DIV(ir_opcode.iro_Div, "div") {
            @Override
            public Instruction getInstruction(int dividend, int divisor, int result) {
                return Instruction.newDiv(dividend, divisor, result);
            }
        },
        MOD(ir_opcode.iro_Mod, "mod") {
            @Override
            public Instruction getInstruction(int dividend, int divisor, int result) {
                return Instruction.newMod(dividend, divisor, result);
            }
        };

        @Getter
        private ir_opcode opcode;
        @Getter
        private String command;

        private Type(ir_opcode opcode, String command) {
            this.opcode = opcode;
            this.command = command;
        }

        public abstract Instruction getInstruction(int dividend, int divisor, int result);
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public final class DivisionMatch extends InstructionMatch.Basic {

        private final Node node;
        private final OperandMatch<Operand.Register> left;
        private final OperandMatch<Operand.Register> right;
        private final int destination;

        @Override
        public Node getNode() {
            return node;
        }

        @Override
        public List<Instruction> getInstructions() {
            return Arrays.asList(type.getInstruction(
                    left.getOperand().get(),
                    right.getOperand().get(),
                    destination));
        }

        @Override
        public Optional<Integer> getTargetRegister() {
            return Optional.of(destination);
        }

        @Override
        public Stream<Node> getPredecessors() {
            return Stream.concat(Stream.of(node.getPred(0)),
                    Stream.concat(left.getPredecessors(), right.getPredecessors()));
        }
    }
}
