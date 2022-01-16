package edu.kit.compiler.optimizations.inlining;

import firm.BackEdges;
import firm.Graph;
import firm.Mode;
import firm.bindings.binding_irnode.ir_opcode;
import firm.nodes.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

import java.util.*;


// TODO: dont inline endless loop?
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Inliner {
    public static void inline(Graph graph, Call call, Graph callee) {BackEdges.enable(callee);

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
                    Graph.exchange(retEdge.node, phis[1]);
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
                    && node.getOpCode() != ir_opcode.iro_Phi && node.getOpCode() != ir_opcode.iro_Const) {
                node.setBlock(endBlock);
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

    private static String getName(Call call) {
        var addr = (Address) call.getPtr();
        return addr.getEntity().getLdName();
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
        }

        @Override
        public void visit(Proj proj) {
            Node pred = proj.getPred();
            if (pred.equals(callee.getStart())) {
                if (proj.getMode().equals(Mode.getM())) {
                    // replace with the initial memory
                    mapping.put(proj.getNr(), call.getMem());
                }
            } else if (pred.getMode().equals(Mode.getT()) && pred.getPredCount() > 0
                    && pred.getPred(0).equals(callee.getStart())) {
                // TODO: not sure whether this is the best way for detecting an argument projection
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
            if (memoryPreds.size() == 1) {
                memory = memoryPreds.get(0);
            } else {
                assert memoryPreds.size() > 1;
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
