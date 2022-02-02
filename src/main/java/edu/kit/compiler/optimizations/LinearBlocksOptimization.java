package edu.kit.compiler.optimizations;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.kit.compiler.io.CommonUtil;
import edu.kit.compiler.optimizations.Util.BlockNodeMapper;

import firm.BackEdges;
import firm.BlockWalker;
import firm.Graph;
import firm.Mode;
import firm.BackEdges.Edge;
import firm.bindings.binding_irgopt;
import firm.bindings.binding_irgraph;
import firm.nodes.Block;
import firm.nodes.Jmp;
import firm.nodes.Node;
import firm.nodes.Phi;

import lombok.Getter;

/**
 * Optimization that eliminates linear blocks, i.e. blocks that appear directly
 * after each other with deterministic control flow between them. Linear blocks
 * are removed by moving the nodes from following blocks to their predecessor
 * block.
 * 
 * Cond nodes with the same block following both branches are removed if linear
 * blocks are created from it. The following block will then be included in its
 * predecessor as usual.
 */
public class LinearBlocksOptimization implements Optimization.Local {

    private Graph graph;
    /**
     * Maps a block to the nodes contained in it.
     */
    private Map<Block, List<Node>> blockNodes;

    @Override
    public boolean optimize(Graph graph, OptimizationState state) {
        this.graph = graph;

        blockNodes = new HashMap<>();
        graph.walkPostorder(new BlockNodeMapper(blockNodes));

        boolean backEdgesEnabled = BackEdges.enabled(graph);
        if (!backEdgesEnabled) {
            BackEdges.enable(graph);
        }

        LinearBlockVisitor visitor = new LinearBlockVisitor();
        graph.walkBlocksPostorder(visitor);

        if (!backEdgesEnabled) {
            BackEdges.disable(graph);
        }

        graph.confirmProperties(binding_irgraph.ir_graph_properties_t.IR_GRAPH_PROPERTIES_NONE);
        binding_irgopt.remove_unreachable_code(graph.ptr);
        binding_irgopt.remove_bads(graph.ptr);

        return visitor.isChange();
    }

    /**
     * Block visitor for finding and eliminating linear blocks. Only one
     * iteration is required if the blocks are visited in postorder.
     */
    private class LinearBlockVisitor implements BlockWalker {

        /**
         * If the graph was changed by removing linear blocks.
         */
        @Getter
        private boolean change = false;

        @Override
        public void visitBlock(Block block) {
            List<Node> nodePreds = CommonUtil.toList(block.getPreds());

            // if block has no or more than one distinct predecessor block -> keep (block is needed to unify control flow)
            if (nodePreds.stream().map(pred -> pred.getBlock()).distinct().count() != 1) return;
            Block pred = (Block) nodePreds.get(0).getBlock();

            // if block is end block -> keep (end block is special)
            if (block.equals(graph.getEndBlock())) return;

            // if predecessor block has multiple distinct successors and block contains nodes -> keep (execution is conditional)
            List<Block> predSuccs = getSuccBlocks(pred);
            List<Node> nodes = blockNodes.get(block);
            if (predSuccs.stream().distinct().count() > 1 && nodes.stream().anyMatch(item -> !(item instanceof Jmp))) return;

            // if block contains Phi -> keep (predecessors are needed to resolve Phi)
            if (nodes.stream().anyMatch(item -> item instanceof Phi)) return;

            List<Node> predNodes = blockNodes.get(pred);

            // skip conditional control flow in predecessor block
            Node nodePred;
            if (nodePreds.size() > 1) {
                nodePred = graph.newJmp(pred);
                predNodes.add(nodePred);
            } else {
                nodePred = nodePreds.get(0);
            }

            // move all nodes to predecessor block (except Jmp nodes)
            // change predecessor of Jmp node successors to node in predecessor block
            for (Node node : nodes) {
                if (node instanceof Jmp) {
                    for (Edge edge : BackEdges.getOuts(node)) {
                        edge.node.setPred(edge.pos, nodePred);
                    }
                } else {
                    node.setBlock(pred);
                    predNodes.add(node);
                }
            }
            blockNodes.remove(block);

            // exclude block from control flow
            for (int i = 0; i < block.getPredCount(); i++) {
                block.setPred(i, graph.newBad(Mode.getX()));
            }

            // graph has changed
            change = true;
        }

        /**
         * Get the control flow successor blocks of a block.
         */
        private List<Block> getSuccBlocks(Block block) {
            List<Block> succBlocks = new ArrayList<>();

            for (Node node : blockNodes.get(block)) {
                for (Edge edge : BackEdges.getOuts(node)) {
                    if (edge.node instanceof Block) {
                        succBlocks.add((Block) edge.node);
                    }
                }
            }

            return succBlocks;
        }

    }

}
