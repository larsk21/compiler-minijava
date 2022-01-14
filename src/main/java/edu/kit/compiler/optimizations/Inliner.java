package edu.kit.compiler.optimizations;

import firm.BackEdges;
import firm.Dump;
import firm.Graph;
import firm.Mode;
import firm.bindings.binding_irgopt;
import firm.bindings.binding_irnode.ir_opcode;
import firm.nodes.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.*;

@RequiredArgsConstructor
public class Inliner implements Optimization {
    private final Map<String, Graph> available_callees;
    private Graph graph;

    @Override
    public boolean optimize(Graph graph) {
        this.graph = graph;

        // we transform the nodes in reverse postorder, i.e. we can access the
        // unchanged predecessors of a node when transforming it
        List<Call> calls = new ArrayList<>();
        graph.walk(new NodeVisitor.Default() {
            @Override
            public void visit(Call node) {
                calls.add(node);
            }
        });

        BackEdges.enable(graph);

        boolean changes = false;
        for (Call call: calls) {
            changes |= inline(call);
        }

        BackEdges.disable(graph);

        binding_irgopt.remove_bads(graph.ptr);
        binding_irgopt.remove_unreachable_code(graph.ptr);
        binding_irgopt.remove_bads(graph.ptr);

        return changes;
    }

    private boolean inline(Call call) {
        String name = getName(call);
        if (available_callees.containsKey(name)) {
            Graph callee = available_callees.get(name);
            BackEdges.enable(callee);

            Block entryBlock = (Block) call.getBlock();
            Node jmp = graph.newJmp(entryBlock);

            var copyVisitor = new CopyNodes(call, graph, callee, jmp);
            callee.walkTopological(copyVisitor);
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
            return true;
        }
        return false;
    }

    private static void splitBlock(Call call, Node endBlock, Node entryJmp) {
        // mark all nodes that remain in the current block
        Set<Integer> callPreds = new HashSet<>();
        callPreds.add(entryJmp.getNr());
        markPreds(callPreds, call, call.getBlock());

        for (var edge: BackEdges.getOuts(call.getBlock())) {
            Node node = edge.node;
            if (!callPreds.contains(node.getNr())
                    && node.getOpCode() != ir_opcode.iro_Phi
                    && node.getOpCode() != ir_opcode.iro_Const) {
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
        @Getter
        private Node endBlock;

        @Override
        public void defaultVisit(Node node) {
            Node copied = copyNode(node);
            int blockId = copied.getBlock().getNr();
            copied.setBlock(mapping.get(blockId));
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
                Node copied = copyNode(block);
                if (block.equals(callee.getEndBlock())) {
                    endBlock = copied;
                }
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

        public void replacePreds() {
            for (Node node: mapping.values()) {
                for (int i = 0; i < node.getPredCount(); i++) {
                    Node newPred = mapping.get(node.getPred(i).getNr());
                    if (newPred != null) {
                        node.setPred(i, newPred);
                    }
                }
            }
        }

        // Creates the memory phi for the last block, and, if the return type is not void,
        // the phi for the return value. Also replaces all returns with jumps.
        public Node[] createPhis() {
            // collect predecessors
            List<Node> memoryPreds = new ArrayList<>();
            List<Node> valuePreds = new ArrayList<>();
            for (Node pred: endBlock.getPreds()) {
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
                memory = graph.newPhi(endBlock, memoryPreds.toArray(new Node[0]), Mode.getM());
            }
            if (valuePreds.size() == 1) {
                return new Node[] {memory, valuePreds.get(0)};
            } else if (valuePreds.size() > 1) {
                Mode mode = valuePreds.get(0).getMode();
                Node phi = graph.newPhi(endBlock, valuePreds.toArray(new Node[0]), mode);
                return new Node[] {memory, phi};
            } else {
                return new Node[] {memory};
            }
        }

        private Node copyNode(Node node) {
            Node copied = graph.copyNode(node);
            mapping.put(node.getNr(), copied);
            return copied;
        }
    }
}
