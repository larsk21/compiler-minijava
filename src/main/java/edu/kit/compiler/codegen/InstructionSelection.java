package edu.kit.compiler.codegen;

import java.util.List;

import edu.kit.compiler.codegen.pattern.InstructionMatch;
import edu.kit.compiler.codegen.pattern.MatchVisitor;
import firm.Graph;
import firm.MethodType;
import firm.bindings.binding_irnode.ir_opcode;
import firm.nodes.Jmp;
import firm.nodes.Node;
import firm.nodes.NodeVisitor;
import firm.nodes.Proj;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

public final class InstructionSelection {

    @Getter
    private final MatcherState matcher;

    @Getter
    private final BasicBlocks blocks;

    private InstructionSelection(Graph graph) {
        // todo is this the idiomatic way of getting number of parameters
        var type = (MethodType) graph.getEntity().getType();
        matcher = new MatcherState(graph, type.getNParams());
        blocks = new BasicBlocks(graph);
    }

    public static InstructionSelection apply(Graph graph, Patterns patterns) {
        var instance = new InstructionSelection(graph);

        var matchingVisitor = instance.new MatchingVisitor(patterns);
        graph.walkTopological(matchingVisitor);

        var collectingVisitor = instance.new CollectingVisitor(instance.blocks);
        instance.matcher.walkTopological(collectingVisitor);

        return instance;
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private final class CollectingVisitor implements MatchVisitor {

        private final BasicBlocks blocks;

        @Override
        public void visit(InstructionMatch.Block match) {
            // a block is visited before all of the contained nodes
            var block = match.getNode();
            for (var pred : block.getPreds()) {
                var entry = blocks.getEntry(pred.getBlock());
                if (pred.getOpCode() == ir_opcode.iro_Proj) {
                    entry.setDestination((Proj) pred, block);
                } else if (pred.getOpCode() == ir_opcode.iro_Jmp) {
                    entry.setDestination((Jmp) pred, block);
                }
            }
        }

        @Override
        public void visit(InstructionMatch.Basic match) {
            var node = match.getNode();
            var entry = blocks.getEntry(node.getBlock());
            entry.append(match);
        }

        @Override
        public void visit(InstructionMatch.Phi match) {
            var entry = blocks.getEntry(match.getNode().getBlock());
            entry.addPhi(match.getPhiInstruction());
        }

        @Override
        public void visit(InstructionMatch.Condition match) {
            var node = match.getNode();
            switch (node.getOpCode()) {
                case iro_Jmp, iro_Cond -> {
                    var entry = blocks.getEntry(node.getBlock());
                    entry.setExitCondition(match.getCondition());
                }
                default -> {
                }
            }
        }
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private final class MatchingVisitor extends NodeVisitor.Default {

        private final Patterns patterns;

        @Override
        public void defaultVisit(Node node) {
            var match = patterns.match(node, matcher);
            if (match.matches()) {
                matcher.setMatch(node, match);
            } else {
                var name = node.getGraph().getEntity().getName();
                throw new IllegalStateException(name + ": no match for node " + node.getNr());
            }
        }

        @Override
        public void visit(Proj node) {
            // todo put this in patterns?

            // todo: is comparison of Nr correct here?
            if (node.getPred().getNr() == node.getGraph().getArgs().getNr()) {
                // node is a parameter projection of the function
                matcher.setMatch(node, InstructionMatch.empty(node, node.getNum()));

            } else if (node.getPred().getOpCode() == ir_opcode.iro_Cond) {
                var preds = List.of(node.getPred());
                matcher.setMatch(node, InstructionMatch.empty(node, preds));

            } else if (node.getMode().isData()) {
                var pred = node.getPred();
                if (pred.getOpCode() == ir_opcode.iro_Proj) {
                    // Deal with double projection for results
                    pred = pred.getPred(0);
                }

                var predMatch = matcher.getMatch(pred);
                if (predMatch != null) {
                    var preds = List.of(pred);
                    matcher.setMatch(node, predMatch.getTargetRegister()
                            .map(r -> InstructionMatch.empty(node, preds, r))
                            .orElseGet(() -> InstructionMatch.empty(node, preds)));
                } else {
                    // todo
                    // defaultVisit(node);
                    // throw new UnsupportedOperationException("" + node.getNr());
                }
            } else {
                matcher.setMatch(node, InstructionMatch.empty(node, node.getPred()));
            }
        }
    }
}
