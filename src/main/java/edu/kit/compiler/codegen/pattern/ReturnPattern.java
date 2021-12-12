package edu.kit.compiler.codegen.pattern;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import edu.kit.compiler.codegen.NodeRegisters;
import edu.kit.compiler.codegen.Operand.Register;
import edu.kit.compiler.intermediate_lang.Instruction;
import firm.bindings.binding_irnode.ir_opcode;
import firm.nodes.Node;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

public class ReturnPattern implements Pattern<InstructionMatch> {

    public final Pattern<OperandMatch<Register>> pattern = new RegisterPattern();

    @Override
    public InstructionMatch match(Node node, NodeRegisters registers) {
        if (node.getOpCode() == ir_opcode.iro_Return) {
            Optional<OperandMatch<Register>> operand = Optional.empty();
            for (var pred : node.getPreds()) {
                var match = pattern.match(pred, registers);
                if (match.matches()) {
                    assert !operand.isPresent();
                    operand = Optional.of(match);
                }
            }
            return new ReturnMatch(operand);
        } else {
            return InstructionMatch.none();
        }
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class ReturnMatch extends InstructionMatch.Some {

        private final Optional<OperandMatch<Register>> match;

        @Override
        public List<Instruction> getInstructions() {
            var register = match.map(match -> match.getOperand().get());
            return Arrays.asList(Instruction.newRet(register));
        }

        @Override
        public Optional<Integer> getTargetRegister() {
            return Optional.empty();
        }
    }
}
