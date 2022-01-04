package edu.kit.compiler.codegen.pattern;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import edu.kit.compiler.codegen.MatcherState;
import edu.kit.compiler.codegen.Operand;
import edu.kit.compiler.codegen.Util;
import edu.kit.compiler.intermediate_lang.Instruction;
import firm.Mode;
import firm.nodes.Load;
import firm.bindings.binding_irnode.ir_opcode;
import firm.nodes.Node;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

public class LoadMemoryPattern implements Pattern<InstructionMatch> {

    private static final Pattern<OperandMatch<Operand.Memory>> MEMORY = OperandPattern.memory();

    @Override
    public InstructionMatch match(Node node, MatcherState matcher) {
        if (node.getOpCode() == ir_opcode.iro_Load) {
            var match = MEMORY.match(node.getPred(1), matcher);
            if (match.matches()) {
                var mode = ((Load) node).getLoadMode();
                var targetRegister = matcher.getNewRegister(Util.getSize(mode));
                return new LoadMemoryMatch(node, match, targetRegister, mode);
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
        private final OperandMatch<Operand.Memory> source;
        private final int targetRegister;
        private final Mode mode;

        @Override
        public Node getNode() {
            return node;
        }

        @Override
        public List<Instruction> getInstructions() {
            var targetOperand = Operand.register(mode, targetRegister);

            return List.of(Instruction.newOp(
                    Util.formatCmd("mov", Util.getSize(mode), source.getOperand(), targetOperand),
                    source.getOperand().getSourceRegisters(), Optional.empty(), targetRegister));
        }

        @Override
        public Optional<Integer> getTargetRegister() {
            return Optional.of(targetRegister);
        }

        @Override
        public Stream<Node> getPredecessors() {
            return Stream.concat(Stream.of(node.getPred(0)), source.getPredecessors());
        }

        @Override
        public Stream<Operand> getOperands() {
            return Stream.of(source.getOperand());
        }
    }
}
