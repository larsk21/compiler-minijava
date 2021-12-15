package edu.kit.compiler.codegen.pattern;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import edu.kit.compiler.codegen.ExitCondition;
import edu.kit.compiler.codegen.MatcherState;
import edu.kit.compiler.codegen.Operand;
import edu.kit.compiler.codegen.Util;
import edu.kit.compiler.intermediate_lang.Instruction;
import firm.nodes.Node;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

public class LoadImmediate implements Pattern<InstructionMatch> {

    public final Pattern<OperandMatch<Operand.Immediate>> immediate = OperandPattern.immediate();

    @Override
    public InstructionMatch match(Node node, MatcherState matcher) {
        var match = immediate.match(node, matcher);
        if (match.matches()) {
            return new LoadImmediateMatch(match, matcher.getNewRegister());
        } else {
            return InstructionMatch.none();
        }
    }
    
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class LoadImmediateMatch extends InstructionMatch.Some {

        private final OperandMatch<Operand.Immediate> match;
        private final int register;

        @Override
        public List<Instruction> getInstructions() {
            var operand = match.getOperand();
            var mode = operand.getMode();
            var target = Operand.register(mode, register);
            return Arrays.asList(Instruction.newOp(
                Util.formatCmd("mov", Util.getSize(mode), operand, target),
                Collections.emptyList(), Optional.empty(), register));
        }

        @Override
        public Optional<Integer> getTargetRegister() {
            return Optional.of(register);
        }

        @Override
        public Stream<Node> getPredecessors() {
            return match.getPredecessors();
        }

        @Override
        public Optional<ExitCondition> getCondition() {
            return Optional.empty();
        }
    }
}
