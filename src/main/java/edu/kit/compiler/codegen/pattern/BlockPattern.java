package edu.kit.compiler.codegen.pattern;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import edu.kit.compiler.codegen.MatcherState;
import edu.kit.compiler.codegen.Operand;
import firm.bindings.binding_irnode.ir_opcode;
import firm.nodes.Node;
import firm.nodes.Block;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

public class BlockPattern implements Pattern<InstructionMatch> {

    @Override
    public InstructionMatch match(Node node, MatcherState matcher) {
        if (node.getOpCode() == ir_opcode.iro_Block) {
            return new BlockMatch((Block) node, StreamSupport
                    .stream(node.getPreds().spliterator(), false)
                    .collect(Collectors.toList()));
        } else {
            return InstructionMatch.none();
        }
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private static final class BlockMatch extends InstructionMatch.Block {

        private final firm.nodes.Block block;
        private final List<Node> predecessors;

        @Override
        public firm.nodes.Block getNode() {
            return block;
        }

        @Override
        public Optional<Integer> getTargetRegister() {
            return Optional.empty();
        }

        @Override
        public Stream<Node> getPredecessors() {
            return predecessors.stream();
        }

        @Override
        public Stream<Operand> getOperands() {
            return Stream.empty();
        }
    }
}
