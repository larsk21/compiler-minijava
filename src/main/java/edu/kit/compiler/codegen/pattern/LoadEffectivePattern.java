package edu.kit.compiler.codegen.pattern;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import edu.kit.compiler.codegen.MatcherState;
import edu.kit.compiler.codegen.Operand;
import edu.kit.compiler.codegen.Util;
import edu.kit.compiler.intermediate_lang.Instruction;
import edu.kit.compiler.intermediate_lang.RegisterSize;
import firm.nodes.Node;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

public final class LoadEffectivePattern implements Pattern<InstructionMatch> {

    private static final Pattern<OperandMatch<Operand.Memory>> MEMORY = OperandPattern.memory();

    @Override
    public InstructionMatch match(Node node, MatcherState matcher) {
        var match = MEMORY.match(node, matcher);

        if (match.matches()) {
            var size = Util.getSize(node.getMode());

            // x86 only supports double-and quad-words in addressing modes
            return switch (size) {
                case DOUBLE, QUAD -> {
                    var targetRegister = matcher.getNewRegister(size);
                    yield new LoadEffectiveMatch(node, match, size, targetRegister);
                }
                default -> InstructionMatch.none();
            };
        } else {
            return InstructionMatch.none();
        }
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private final class LoadEffectiveMatch extends InstructionMatch.Basic {

        private final Node node;
        private final OperandMatch<Operand.Memory> source;
        private final RegisterSize size;
        private final int targetRegister;

        @Override
        public Node getNode() {
            return node;
        }

        @Override
        public List<Instruction> getInstructions() {
            return List.of(Instruction.newOp(
                    Util.formatCmd("lea", size, source.getOperand(), targetRegister),
                    source.getOperand().getSourceRegisters(), Optional.empty(), targetRegister));
        }

        @Override
        public Optional<Integer> getTargetRegister() {
            return Optional.of(targetRegister);
        }

        @Override
        public Stream<Operand> getOperands() {
            return Stream.of(source.getOperand());
        }

        @Override
        public Stream<Node> getPredecessors() {
            return source.getPredecessors();
        }
    }
}
