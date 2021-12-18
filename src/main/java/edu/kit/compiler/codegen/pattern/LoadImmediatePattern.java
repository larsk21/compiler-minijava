package edu.kit.compiler.codegen.pattern;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import edu.kit.compiler.codegen.MatcherState;
import edu.kit.compiler.codegen.Operand;
import edu.kit.compiler.codegen.Util;
import edu.kit.compiler.intermediate_lang.Instruction;
import firm.nodes.Node;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

public class LoadImmediatePattern implements Pattern<InstructionMatch> {

    public final Pattern<OperandMatch<Operand.Immediate>> pattern = OperandPattern.immediate();

    @Override
    public InstructionMatch match(Node node, MatcherState matcher) {
        var match = pattern.match(node, matcher);
        if (match.matches()) {
            var targetRegister = matcher.getNewRegister(Util.getSize(node.getMode()));
            return new LoadImmediateMatch(node, match, targetRegister);
        } else {
            return InstructionMatch.none();
        }
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class LoadImmediateMatch extends InstructionMatch.Basic {

        private final Node node;
        private final OperandMatch<Operand.Immediate> source;
        private final int targetRegister;

        @Override
        public Node getNode() {
            return node;
        }

        @Override
        public List<Instruction> getInstructions() {
            var sourceOperand = source.getOperand();
            var mode = sourceOperand.getMode();
            var targetOperand = Operand.register(mode, targetRegister);
            
            if (sourceOperand.get().isNull()) {
                return List.of(getZero(targetOperand));
            } else {
                return List.of(getNonZero(sourceOperand, targetOperand));
            }
        }

        public Instruction getZero(Operand target) {
            return Instruction.newOp(
                Util.formatCmd("xor", target.getSize(), target, target),
                Collections.emptyList(), Optional.empty(), targetRegister);
        }

        public Instruction getNonZero(Operand source, Operand target) {
            return Instruction.newOp(
                    Util.formatCmd("mov", target.getSize(), source, target),
                    Collections.emptyList(), Optional.empty(), targetRegister);
        }

        @Override
        public Optional<Integer> getTargetRegister() {
            return Optional.of(targetRegister);
        }

        @Override
        public Stream<Node> getPredecessors() {
            return source.getPredecessors();
        }
    }
}
