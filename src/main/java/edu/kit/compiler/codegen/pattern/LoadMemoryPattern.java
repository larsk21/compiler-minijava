package edu.kit.compiler.codegen.pattern;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import edu.kit.compiler.codegen.MatcherState;
import edu.kit.compiler.codegen.Operand;
import edu.kit.compiler.codegen.Util;
import edu.kit.compiler.intermediate_lang.Instruction;
import firm.Mode;
import firm.bindings.binding_irnode.ir_opcode;
import firm.nodes.Node;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

public class LoadMemoryPattern implements Pattern<InstructionMatch> {

    public final Pattern<OperandMatch<Operand.Memory>> memory = OperandPattern.memory();

    @Override
    public InstructionMatch match(Node node, MatcherState matcher) {
        if (node.getOpCode() == ir_opcode.iro_Load) {
            var match = memory.match(node.getPred(1), matcher);
            if (match.matches()) {
                var mode = ((firm.nodes.Load) node).getLoadMode();
                var destination = matcher.getNewRegister(Util.getSize(mode));
                return new LoadMemoryMatch(node, match, destination, mode);
            } else {
                return InstructionMatch.none();
            }
        } else {
            return InstructionMatch.none();
        }
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class LoadMemoryMatch extends InstructionMatch.Basic {

        private final Node node;
        private final OperandMatch<Operand.Memory> match;
        private final int register;
        private final Mode mode;

        @Override
        public Node getNode() {
            return node;
        }

        @Override
        public List<Instruction> getInstructions() {
            var target = Operand.register(mode, register);

            return List.of(Instruction.newOp(
                    Util.formatCmd("mov", Util.getSize(mode), match.getOperand(), target),
                    Collections.emptyList(), Optional.empty(), register));
        }

        @Override
        public Optional<Integer> getTargetRegister() {
            return Optional.of(register);
        }

        @Override
        public Stream<Node> getPredecessors() {
            return Stream.concat(Stream.of(node.getPred(0)), match.getPredecessors());
        }
    }
}
