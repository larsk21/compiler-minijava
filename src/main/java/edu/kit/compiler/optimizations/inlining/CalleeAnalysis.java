package edu.kit.compiler.optimizations.inlining;

import firm.Graph;
import firm.Mode;
import firm.nodes.Block;
import firm.nodes.Node;
import firm.nodes.NodeVisitor;
import firm.nodes.Proj;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.HashSet;
import java.util.Set;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class CalleeAnalysis {
    @Getter
    private final int numNodes;
    @Getter
    private final int numUsedArgs;

    public static CalleeAnalysis run(Graph graph) {
        var visitor = new CalleeVisitor(graph);
        graph.walkPostorder(visitor);
        return visitor.getResult();
    }

    @RequiredArgsConstructor
    private static class CalleeVisitor extends NodeVisitor.Default {
        private final Graph graph;
        private int numNodes = 0;
        private Set<Integer> usedArgs = new HashSet<>();

        @Override
        public void defaultVisit(Node node) {
            numNodes++;
        }

        @Override
        public void visit(Proj proj) {
            Node pred = proj.getPred();
            if (pred.getMode().equals(Mode.getT()) && pred.getPredCount() > 0
                    && pred.getPred(0).equals(graph.getStart())) {
                usedArgs.add(proj.getNum());
            }
            defaultVisit(proj);
        }

        @Override
        public void visit(Block block) {
            // do not count blocks (they don't contribute to code size)
        }

        public CalleeAnalysis getResult() {
            return  new CalleeAnalysis(numNodes, usedArgs.size());
        }
    }
}
