package edu.kit.compiler.codegen;

import java.util.ArrayList;
import java.util.List;

import edu.kit.compiler.codegen.pattern.InstructionMatch;
import edu.kit.compiler.codegen.pattern.InstructionMatchVisitor;
import edu.kit.compiler.intermediate_lang.RegisterSize;
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

    private InstructionSelection(Graph graph, int blockId) {
        var type = (MethodType) graph.getEntity().getType();
        blocks = new BasicBlocks(graph, blockId);

        var parameters = new ArrayList<RegisterSize>(type.getNParams());
        for (int i = 0; i < type.getNParams(); ++i) {
            parameters.add(Util.getSize(type.getParamType(i).getMode()));
        }
        matcher = new MatcherState(graph, parameters);
    }

    public static InstructionSelection apply(Graph graph, PatternCollection patterns, int blockId) {
        var instance = new InstructionSelection(graph, blockId);

        var matchingVisitor = instance.new MatchingVisitor(patterns);
        graph.walkTopological(matchingVisitor);

        var collectingVisitor = instance.new CollectingVisitor(instance.blocks);
        instance.matcher.walkTopological(collectingVisitor);

        return instance;
    }

    /**
     * Collects the found matches into an instance of BasicBlocks.
     */
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private final class CollectingVisitor implements InstructionMatchVisitor {

        private final BasicBlocks blocks;

        // Possible improvement: separate list for Phi and condition operands

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
            match.getOperands()
                    .flatMap(Operand::getInstructions)
                    .forEach(entry::add);
            entry.append(match.getInstructions());
        }

        @Override
        public void visit(InstructionMatch.Phi match) {
            var entry = blocks.getEntry(match.getNode().getBlock());
            var phi = match.getPhiInstruction();
            entry.addPhi(phi);

            for (var phiEntry : phi.getEntries()) {
                // add necessary instruction for operand to be available
                var predBlock = blocks.getEntry(phiEntry.getPredBlock());
                phiEntry.getOperand().getInstructions().forEach(predBlock::add);
            }
        }

        @Override
        public void visit(InstructionMatch.Condition match) {
            var node = match.getNode();
            switch (node.getOpCode()) {
                case iro_Jmp, iro_Cond -> {
                    var entry = blocks.getEntry(node.getBlock());
                    entry.setExitCondition(match.getCondition());
                    match.getOperands()
                            .flatMap(Operand::getInstructions)
                            .forEach(entry::add);
                }
                default -> throw new IllegalStateException(
                        "only Jmp and Cond control flow supported");
            }
        }
    }

    /**
     * Tries to match every node in the graph using the given set of patterns.
     * Will throw an IllegalStateException if no match is found for any node.
     */
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private final class MatchingVisitor extends NodeVisitor.Default {

        private final PatternCollection patterns;

        @Override
        public void defaultVisit(Node node) {
            var match = patterns.match(node, matcher);
            if (match.matches()) {
                matcher.setMatch(node, match);
            } else {
                throw new IllegalStateException(String.format("%s: no match found for node %s",
                        node.getGraph().getEntity().getName(), node));
            }
        }

        @Override
        public void visit(Proj node) {
            if (node.getPred().equals(node.getGraph().getArgs())) {
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
                    throw new IllegalStateException(String.format(
                            "%s: no match found for projection %s",
                            node.getGraph().getEntity().getName(), node));
                }
            } else {
                matcher.setMatch(node, InstructionMatch.empty(node, node.getPred()));
            }
        }
    }
}
