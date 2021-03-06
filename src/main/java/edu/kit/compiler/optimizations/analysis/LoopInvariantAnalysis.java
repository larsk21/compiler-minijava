package edu.kit.compiler.optimizations.analysis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.kit.compiler.io.CommonUtil;
import edu.kit.compiler.optimizations.Util;
import edu.kit.compiler.optimizations.Util.NodeListFiller;

import firm.Graph;
import firm.Mode;
import firm.nodes.Block;
import firm.nodes.Cmp;
import firm.nodes.Node;
import firm.nodes.Phi;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Analysis that finds all loop-invariant nodes in a graph. The result is
 * provided as a map from loop entry points (see LoopAnalysis) to the nodes
 * invariant to the representing loop. A node can be invariant to multiple
 * loops.
 * 
 * This analysis respects the pin state of nodes. In addition, control flow and
 * Phi nodes are never marked as invariant.
 */
@RequiredArgsConstructor
public class LoopInvariantAnalysis {

    private final Graph graph;

    /**
     * Loops in the graph as received from LoopAnalysis.
     */
    @Getter
    private Map<Block, Set<Block>> loops;
    /**
     * Map from loop entry point to nodes invariant to the represented loop. A
     * node can be invariant to multiple loops. The nodes invariant to each
     * loop are in global postorder.
     */
    @Getter
    private Map<Block, List<Node>> loopInvariantNodes;

    /**
     * Analyze the given graph to find loop-invariant nodes.
     */
    public void analyze() {
        // the nodes are visited in postorder such that invariant predecessors
        // are already found when visiting a node
        List<Node> nodes = new ArrayList<>();
        graph.walkPostorder(new NodeListFiller(nodes));

        LoopAnalysis loopAnalysis = new LoopAnalysis(graph);
        loopAnalysis.analyze();

        loops = loopAnalysis.getLoops();
        loopInvariantNodes = new HashMap<>();

        for (Map.Entry<Block, Set<Block>> loop : loops.entrySet()) {
            Block loopEntryPoint = loop.getKey();
            Set<Block> loopBlocks = loop.getValue();

            List<Node> invariantNodes = new ArrayList<>();
            nodes.stream().filter(item -> loopBlocks.contains(item.getBlock())).forEach(node -> {
                if (isLoopInvariant(node, loopBlocks, invariantNodes)) {
                    invariantNodes.add(node);
                }
            });

            loopInvariantNodes.put(loopEntryPoint, invariantNodes);
        }
    }

    /**
     * Whether a given node is invariant to the loop containing the given loop
     * blocks given a list of nodes invariant to that loop.
     */
    public boolean isLoopInvariant(Node node, Set<Block> loopBlocks, List<Node> loopInvariantNodes) {
        if (Util.isPinned(node)) {
            return false;
        } else if (node.getMode().equals(Mode.getX())) {
            return false;
        } else if (node instanceof Phi || node instanceof Cmp) {
            return false;
        }

        return CommonUtil.stream(node.getPreds())
            .allMatch(pred ->
                !loopBlocks.contains(pred.getBlock()) ||
                loopInvariantNodes.contains(pred)
            );
    }

}
