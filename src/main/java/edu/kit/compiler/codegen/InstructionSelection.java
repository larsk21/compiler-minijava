package edu.kit.compiler.codegen;

import edu.kit.compiler.codegen.pattern.Comparison;
import firm.Graph;
import firm.MethodType;
import firm.bindings.binding_irnode.ir_opcode;
import firm.nodes.Block;
import firm.nodes.Cond;
import firm.nodes.End;
import firm.nodes.Jmp;
import firm.nodes.Node;
import firm.nodes.NodeVisitor;
import firm.nodes.Proj;
import firm.nodes.Start;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

public final class InstructionSelection {

    @Getter
    private final BasicBlocks blocks;

    @Getter
    private final NodeRegisters registers;

    private InstructionSelection(Graph graph) {
        // todo is this the idiomatic way of getting number of parameters
        var type = (MethodType) graph.getEntity().getType();
        registers = new NodeRegisters(type.getNParams());
        blocks = new BasicBlocks(graph);
    }

    public static InstructionSelection apply(Graph graph, Patterns patterns) {
        var instance = new InstructionSelection(graph);

        var visitor = instance.new Visitor(patterns);
        graph.walkTopological(visitor);

        return instance;
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private final class Visitor extends NodeVisitor.Default {

        private final Patterns patterns;

        @Override
        public void defaultVisit(Node node) {
            var match = patterns.match(node, registers);
            if (match.matches()) {
                blocks.getEntry(node.getBlock()).append(match);
                var targetRegister = match.getTargetRegister();
                if (targetRegister.isPresent()) {
                    registers.setRegister(node, targetRegister.get());
                }
            } else {
                // throw new IllegalStateException();
            }
        }

        @Override
        public void visit(Block node) {
            // a block is visited before all of the contained nodes
            blocks.register(node);
            for (var pred : node.getPreds()) {
                var entry = blocks.getEntry(pred.getBlock());
                if (pred.getOpCode() == ir_opcode.iro_Proj) {
                    entry.setDestination((Proj)pred, node);
                } else if (pred.getOpCode() == ir_opcode.iro_Jmp) {
                    entry.setDestination((Jmp)pred, node);
                }
            }
        }

        @Override
        public void visit(Proj node) {
            var args = node.getGraph().getArgs();
            // todo: is comparison of Nr correct here?
            if (node.getPred().getNr() == args.getNr()) {
                // node is a parameter projection of the function
                registers.setRegister(node, node.getNum());
            } else if (node.getPred().getOpCode() == ir_opcode.iro_Cond) {
                // Nothing to do for sucessor of Cond
            } else {
                var register = registers.getRegister(node.getPred());
                if (node.getMode().isData() && register >= 0) {
                    registers.setRegister(node, register);
                } else {
                    defaultVisit(node);
                }
            }
        }


        @Override
        public void visit(Cond node) {
            var pattern = new Comparison();
            var match = pattern.match(node.getSelector(), registers.clone());
            if (match.matches()) {
                var entry = blocks.getEntry(node.getBlock());
                entry.setExitCondition(match.getCondition());
            } else {
                throw new UnsupportedOperationException();
            }
        }

        @Override
        public void visit(Start node) {
        }

        @Override
        public void visit(End node) {
        }
    }
}
