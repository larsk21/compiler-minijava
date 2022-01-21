package edu.kit.compiler.optimizations.inlining;

import firm.*;
import firm.bindings.binding_irnode.ir_opcode;
import firm.nodes.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

import java.util.*;

/**
 * Performs the mechanical aspect of inlining one function into another,
 * i.e. copying the firm graph, splitting the target block and handling
 * nodes related to input or output.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Inliner {
    public static boolean canBeInlined(Graph graph, Graph callee) {
        if (graph.equals(callee)) {
            return false;
        }
        // Needed to work around some problem in the firm backend that appears when
        // the callee has no return value (i.e. is an endless loop).
        // It is sensible to don't inline that, anyway.
        return callee.getEndBlock().getPredCount() > 0;
    }

    public static void inline(Graph graph, Call call, Graph callee) {
        assert graph.equals(call.getGraph());
        assert canBeInlined(graph, callee);
        BackEdges.enable(callee);

        Block entryBlock = (Block) call.getBlock();
        Node jmp = graph.newJmp(entryBlock);

        var copyVisitor = new CopyNodes(call, graph, callee, jmp);
        callee.walkPostorder(copyVisitor);
        copyVisitor.replacePreds();

        Node[] phis = copyVisitor.createPhis();
        for (var edge: BackEdges.getOuts(call)) {
            Proj proj = (Proj) edge.node;
            if (proj.getMode().equals(Mode.getM())) {
                Graph.exchange(proj, phis[0]);
            } else if (proj.getMode().equals(Mode.getT())) {
                for (var retEdge: BackEdges.getOuts(proj)) {
                    // handle weird endless loop edge case
                    Node newNode = phis.length > 1 ? phis[1]
                            : graph.newConst(0, retEdge.node.getMode());
                    Graph.exchange(retEdge.node, newNode);
                }
            }
        }

        splitBlock(call, copyVisitor.getEndBlock(), jmp);
        BackEdges.disable(callee);
    }

    private static void splitBlock(Call call, Node endBlock, Node entryJmp) {
        // mark all nodes that remain in the current block
        Set<Integer> callPreds = new HashSet<>();
        callPreds.add(entryJmp.getNr());
        markPreds(callPreds, call, call.getBlock());

        // move the nodes to the new block
        for (var edge: BackEdges.getOuts(call.getBlock())) {
            Node node = edge.node;
            if (node.getBlock().equals(call.getBlock()) && !callPreds.contains(node.getNr())
                    && node.getOpCode() != ir_opcode.iro_Phi
                    && !mustBeInStartBlock(node)) {
                if (node.getOpCode() != ir_opcode.iro_Proj) {
                    node.setBlock(endBlock);
                } else if (!callPreds.contains(getNonProjPred(node).getNr())) {
                    // projections must be in same block as predecessor
                    node.setBlock(endBlock);
                }
            }
        }
    }

    private static void markPreds(Set<Integer> marked, Node current, Node block) {
        marked.add(current.getNr());
        for (int i = 0; i < current.getPredCount(); i++) {
            Node pred = current.getPred(i);
            if (!marked.contains(pred.getNr()) && pred.getBlock().equals(block)) {
                markPreds(marked, pred, block);
            }
        }
    }

    /**
     * Find the first predecessor of the projection that is not
     * itself a projection.
     */
    private static Node getNonProjPred(Node node) {
        assert node.getOpCode() == ir_opcode.iro_Proj;
        Node pred = ((Proj) node).getPred();
        if (pred.getOpCode() == ir_opcode.iro_Proj) {
            return getNonProjPred(pred);
        } else {
            return pred;
        }
    }

    private static boolean mustBeInStartBlock(Node node) {
        return node.getOpCode() == ir_opcode.iro_Const
                || node.getOpCode() == ir_opcode.iro_Address
                || node.getOpCode() == ir_opcode.iro_NoMem;
    }

    @RequiredArgsConstructor
    private static class CopyNodes extends NodeVisitor.Default {
        private final Map<Integer, Node> mapping = new HashMap<>();
        private final Call call;
        private final Graph graph;
        private final Graph callee;
        private final Node entryJmp;

        @Override
        public void defaultVisit(Node node) {
            Node copied = graph.copyNode(node);
            mapping.put(node.getNr(), copied);
            if (mustBeInStartBlock(node)) {
                copied.setBlock(graph.getStartBlock());
            }
        }

        @Override
        public void visit(Proj proj) {
            Node pred = proj.getPred();
            if (pred.equals(callee.getStart())) {
                if (proj.getMode().equals(Mode.getM())) {
                    // replace with the initial memory
                    mapping.put(proj.getNr(), call.getMem());
                }
            } else if (pred.getMode().equals(Mode.getT())
                    && getNonProjPred(proj).equals(callee.getStart())) {
                // replace with the correct argument
                mapping.put(proj.getNr(), call.getPred(proj.getNum() + 2));
            } else {
                defaultVisit(proj);
            }
        }

        @Override
        public void visit(Block block) {
            if (block.equals(callee.getStartBlock())) {
                mapping.put(block.getNr(), graph.newBlock(new Node[] {entryJmp}));
            } else {
                defaultVisit(block);
            }
        }

        @Override
        public void visit(Start start) {}

        @Override
        public void visit(End end) {
            // restore keepalives
            for (int i = 0; i < end.getPredCount(); i++) {
                graph.keepAlive(mapping.get(end.getPred(i).getNr()));
            }
        }

        public Node getEndBlock() {
            int id = callee.getEndBlock().getNr();
            return mapping.get(id);
        }

        public void replacePreds() {
            for (Node node: mapping.values()) {
                for (int i = 0; i < node.getPredCount(); i++) {
                    Node newPred = mapping.get(node.getPred(i).getNr());
                    if (newPred != null) {
                        node.setPred(i, newPred);
                    }
                }
                Node block = node.getBlock();
                if (block != null && block.getGraph().equals(callee)) {
                    node.setBlock(mapping.get(block.getNr()));
                }
            }
        }

        // Creates the memory phi for the last block, and, if the return type is not void,
        // the phi for the return value. Also replaces all returns with jumps.
        public Node[] createPhis() {
            // collect predecessors
            List<Node> memoryPreds = new ArrayList<>();
            List<Node> valuePreds = new ArrayList<>();
            for (Node pred: getEndBlock().getPreds()) {
                Return ret = (Return) pred;
                memoryPreds.add(ret.getMem());
                if (ret.getPredCount() > 1) {
                    valuePreds.add(ret.getPred(1));
                }
                Graph.exchange(pred, graph.newJmp(pred.getBlock()));
            }

            // handle the different cases
            Node memory;
            assert memoryPreds.size() > 0;
            if (memoryPreds.size() == 1) {
                memory = memoryPreds.get(0);
            } else {
                memory = graph.newPhi(getEndBlock(), memoryPreds.toArray(new Node[0]), Mode.getM());
            }
            if (valuePreds.size() == 1) {
                return new Node[] {memory, valuePreds.get(0)};
            } else if (valuePreds.size() > 1) {
                Mode mode = valuePreds.get(0).getMode();
                Node phi = graph.newPhi(getEndBlock(), valuePreds.toArray(new Node[0]), mode);
                return new Node[] {memory, phi};
            } else {
                return new Node[] {memory};
            }
        }
    }
}
