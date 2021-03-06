package edu.kit.compiler.codegen.pattern;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import edu.kit.compiler.codegen.MatcherState;
import edu.kit.compiler.codegen.Operand;
import edu.kit.compiler.codegen.PhiInstruction;
import edu.kit.compiler.codegen.Util;
import edu.kit.compiler.io.CommonUtil;

import firm.Mode;
import firm.bindings.binding_irnode.ir_opcode;
import firm.nodes.Node;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

public final class PhiPattern implements Pattern<InstructionMatch> {

    private static final Pattern<OperandMatch<Operand.Register>> REGISTER = OperandPattern.register();

    @Override
    public InstructionMatch match(Node node, MatcherState matcher) {
        if (node.getOpCode() != ir_opcode.iro_Phi) {
            return InstructionMatch.none();
        } else if (node.getMode().equals(Mode.getM())) {
            // node is a memory phi, match only needs to reference predecessors
            return InstructionMatch.empty(node, CommonUtil.toList(node.getPreds()));
        } else {
            // node is an actual phi
            assert node.getMode().isData();
            var preds = Util.streamPreds(node)
                    .map(pred -> REGISTER.match(pred, matcher))
                    .collect(Collectors.toList());

            if (preds.stream().allMatch(match -> match.matches())) {
                var size = Util.getSize(node.getMode());
                var targetRegister = matcher.getPhiRegister(node, size);
                return new PhiMatch(node, preds, targetRegister);
            } else {
                return InstructionMatch.none();
            }
        }
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private static final class PhiMatch extends InstructionMatch.Phi {

        private final Node node;
        private final List<OperandMatch<Operand.Register>> preds;
        private final int targetRegister;

        @Override
        public Node getNode() {
            return node;
        }

        @Override
        public PhiInstruction getPhiInstruction() {
            var phi = new PhiInstruction(targetRegister, node.getMode());

            assert node.getPredCount() == node.getBlock().getPredCount();
            for (int i = 0; i < node.getPredCount(); ++i) {
                var register = preds.get(i).getOperand();
                var predBlock = (firm.nodes.Block) node.getBlock().getPred(i).getBlock();
                phi.addEntry(predBlock, register);
            }

            return phi;
        }

        @Override
        public Optional<Integer> getTargetRegister() {
            return Optional.of(targetRegister);
        }

        @Override
        public Stream<Node> getPredecessors() {
            return preds.stream().flatMap(match -> match.getPredecessors());
        }

        @Override
        public Stream<Operand> getOperands() {
            return preds.stream().map(OperandMatch::getOperand);
        }
    }
}
