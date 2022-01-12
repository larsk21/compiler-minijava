package edu.kit.compiler.codegen.pattern;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import edu.kit.compiler.codegen.MatcherState;
import edu.kit.compiler.codegen.Operand;
import edu.kit.compiler.codegen.Util;
import edu.kit.compiler.intermediate_lang.Instruction;
import firm.bindings.binding_irnode.ir_opcode;
import firm.nodes.Node;
import lombok.RequiredArgsConstructor;

public final class UnknownPattern implements Pattern<InstructionMatch> {

    @Override
    public InstructionMatch match(Node node, MatcherState matcher) {
        if (node.getOpCode() == ir_opcode.iro_Unknown) {
            var size = Util.getSize(node.getMode());
            var targetRegister = matcher.getNewRegister(size);
            return new UnknownMatch(node, targetRegister);
        } else {
            return InstructionMatch.none();
        }
    }

    @RequiredArgsConstructor
    private static final class UnknownMatch extends InstructionMatch.Basic {

        private final Node node;
        private final int targetRegister;

        @Override
        public Node getNode() {
            return node;
        }

        @Override
        public Optional<Integer> getTargetRegister() {
            return Optional.of(targetRegister);
        }

        @Override
        public Stream<Operand> getOperands() {
            return Stream.empty();
        }

        @Override
        public Stream<Node> getPredecessors() {
            return Stream.empty();
        }

        @Override
        public List<Instruction> getInstructions() {
            return List.of(Instruction.newOp("nop", List.of(),
                    Optional.empty(), targetRegister));
        }
    }
}
