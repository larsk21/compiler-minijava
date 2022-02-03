package edu.kit.compiler.optimizations;

import com.sun.jna.Pointer;
import edu.kit.compiler.optimizations.attributes.Attributes;
import firm.BackEdges;
import firm.Graph;
import firm.Mode;
import firm.bindings.binding_irdom;
import firm.bindings.binding_irgopt;
import firm.bindings.binding_irnode;
import firm.nodes.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.util.*;
import java.util.stream.Collectors;

import static firm.bindings.binding_irgraph.ir_graph_properties_t.IR_GRAPH_PROPERTY_CONSISTENT_DOMINANCE;
import static firm.bindings.binding_irnode.ir_opcode.*;

public class LoadStoreOptimization implements Optimization.Local {

    @Override
    public boolean optimize(Graph g, OptimizationState state) {
        g.assureProperties(IR_GRAPH_PROPERTY_CONSISTENT_DOMINANCE);
        // compare new change map with old one
        boolean backEdgesEnabled = BackEdges.enabled(g);
        if (!backEdgesEnabled) {
            BackEdges.enable(g);
        }

        LoadStoreVisitor visitor = new LoadStoreVisitor(g.getStart());

        List<Node> nodes = new ArrayList<>();
        Util.NodeListFiller filler = new Util.NodeListFiller(nodes);
        g.walkTopological(filler);
        while (!nodes.isEmpty()) {
            Node n = nodes.remove(0);
            n.accept(visitor);
        }

        // calculate dominances for graph
        visitor.setPreds();
        DominanceVisitor domVisitor = new DominanceVisitor(visitor.getMemNodeMap());
        g.walkPostorder(domVisitor);

        for (var entry : visitor.getMemNodeMap().entrySet()) {
            Set<MemNode> dominators = entry.getValue().getDominatingMem();
            for (var dominator : dominators) {
                dominator.getDominatedChildren().add(entry.getValue());
            }
        }

        boolean hadChanges = false;
        nodes = new ArrayList<>();
        filler = new Util.NodeListFiller(nodes);
        g.walkTopological(filler);

        RedundantLoadVisitor rlVisitor = new RedundantLoadVisitor(visitor.getMemNodeMap());
        while (!nodes.isEmpty()) {
            Node n = nodes.remove(0);
            n.accept(rlVisitor);
        }

        hadChanges |= rlVisitor.isChanges();

        if (!backEdgesEnabled) {
            BackEdges.disable(g);
        }

        binding_irgopt.remove_bads(g.ptr);
        binding_irgopt.remove_unreachable_code(g.ptr);
        binding_irgopt.remove_bads(g.ptr);
        g.assureProperties(IR_GRAPH_PROPERTY_CONSISTENT_DOMINANCE);

        return hadChanges;
    }

    public static boolean dominates(Pointer source, Pointer target) {
        int dominates = binding_irdom.block_dominates(source, target);
        return dominates != 0;
    }

    @Getter
    @RequiredArgsConstructor
    private static final class LoadStoreVisitor extends NodeVisitor.Default {
        private final Start start;
        private LoadStoreOptimization.MemNode rootMemNode = null;
        private final Map<Node, LoadStoreOptimization.MemNode> memNodeMap = new HashMap<>();

        @Override
        public void defaultVisit(Node node) {
            // change nothing
            // the node is not in our memory path
        }

        private List<Node> getNodeChildren(Node mem) {
            List<Node> children = new ArrayList<>();
            for (var out : BackEdges.getOuts(mem)) {
                Node n = out.node;
                children.add(n);
            }
            return children;
        }

        private Optional<Node> getChildMemNode(Node n) {
            List<Node> children = getNodeChildren(n);
            return children.stream().filter(
                    f -> Objects.equals(f.getMode(), Mode.getM())
            ).findFirst();
        }

        private void traceNode(Node orig) {
            Optional<Node> memNode = getChildMemNode(orig);

            // return nodes do not have any outgoing memory refernces
            if (memNode.isEmpty() && orig.getOpCode() == binding_irnode.ir_opcode.iro_Return) {
                memNodeMap.put(orig, new MemNode(orig, null, null));
                return;
            }
            if (orig.getOpCode() == iro_Phi && orig.getMode().equals(Mode.getM())) {
                // phi nodes are themselves memory nodes
                memNode = Optional.of(orig);
            } else if (memNode.isEmpty()) {
                return;
            }

            if (!memNodeMap.containsKey(orig)) {
                memNodeMap.put(orig, null);
                List<Node> memOuts = traceMemOuts(memNode.get());
                MemNode newMem = new MemNode(orig, memNode.get(), memOuts);
                memNodeMap.put(orig, newMem);
            }
        }

        // follows the memory dependency chain recursively
        private List<Node> traceMemOuts(Node memNode) {
            List<Node> children = getNodeChildren(memNode);
            List<Node> memOuts = new ArrayList<>();
            for (var child : children) {
                switch (child.getOpCode()) {
                    case iro_Div, iro_Load, iro_Store, iro_Mod, iro_Call, iro_Return -> {
                        memOuts.add(child);
                        // go into childs recursively to build graph
                        child.accept(this);
                    }
                    case iro_Phi -> {
                        if (Objects.equals(child.getMode(), Mode.getM())) {
                            // this is a memory node itself thus we just follow it again
                            memOuts.add(child);
                            child.accept(this);
                        }
                    }
                    case iro_Sync -> {
                        return traceMemOuts(child);
                    }
                }
            }
            return memOuts;
        }

        private void setPreds() {
            for (var entryMemNode : memNodeMap.entrySet()) {
                LoadStoreOptimization.MemNode m = entryMemNode.getValue();
                if (m.getMemOuts() == null) {
                    continue;
                }
                for (var out : m.getMemOuts()) {
                    LoadStoreOptimization.MemNode outMemNode = memNodeMap.get(out);
                    if (outMemNode == null) {
                        continue;
                    }
                    outMemNode.getMemPreds().add(m);
                }
            }
        }

        @Override
        public void visit(Call node) {
            traceNode(node);
        }

        @Override
        public void visit(Start node) {
            traceNode(node);
        }

        @Override
        public void visit(Div node) {
            traceNode(node);
        }

        @Override
        public void visit(Mod node) {
            traceNode(node);
        }

        @Override
        public void visit(Load load) {
            traceNode(load);
        }

        @Override
        public void visit(Store store) {
            traceNode(store);
        }

        @Override
        public void visit(Phi phi) {
            if (Objects.equals(phi.getMode(), Mode.getM())) {
                traceNode(phi);
            }
        }

        @Override
        public void visit(Return ret) {
            traceNode(ret);
        }
    }

    @RequiredArgsConstructor
    private static class RedundantLoadVisitor extends NodeVisitor.Default {
        private final Map<Node, MemNode> memNodeMap;
        @Getter
        private boolean changes = false;

        @Override
        public void defaultVisit(Node node) {
            // do nothing
        }

        @Override
        public void visit(Load load) {
            MemNode memNode = memNodeMap.get(load);
            if (memNode == null || memNode.getDominatingMem() == null) {
                return;
            }
            if (memNode.getDominatingMem().size() != 1) {
                // can not replace in case of multiple incoming paths that may dominate this one
                return;
            }

            MemNode dominatingMem = memNode.getDominatingMem().stream().findFirst().get();

            if (dominatingMem.getN().getOpCode() == iro_Store) {
                // eliminate load after store
                Store store = (Store) dominatingMem.getN();
                Node value = store.getValue();
                replaceLoadWithValue(load, value, memNode, dominatingMem, store.getPtr());
            } else if (dominatingMem.getN().getOpCode() == iro_Load) {
                // eliminate load after load
                Load prev = (Load) dominatingMem.getN();
                Node value = load.getGraph().newProj(prev, prev.getMode(), Load.pnRes);
                replaceLoadWithValue(load, value, memNode, dominatingMem, prev.getPtr());
            }
        }

        private void replaceLoadWithValue(Load load, Node value, MemNode memNode, MemNode dominatingMem, Node ptr) {
            if (ptr.equals(load.getPtr())) {
                // replace result of the load for each successor
                for (var edge : BackEdges.getOuts(load)) {
                    Node node = edge.node;
                    if (node.getOpCode() == iro_Proj && !node.getMode().equals(Mode.getM())) {
                        Proj proj = (Proj) node;
                        for (var out : BackEdges.getOuts(proj)) {
                            out.node.setPred(out.pos, value);
                        }
                    }
                }

                // replace mem node out with preds
                this.changes = true;
                Graph.exchange(memNode.getMem(), load.getMem());
                // replace reference of mem to dominated mem node
                for (var memOutNode : memNode.getMemOuts()) {
                    if (memOutNode.getOpCode() != iro_Deleted) {
                        memOutNode.setPred(0, dominatingMem.getMem());
                    }
                }
                Graph.killNode(load);
            }
        }
    }

    @RequiredArgsConstructor
    private static class DominanceVisitor extends NodeVisitor.Default {
        private final Map<Node, MemNode> memNodeMap;

        @Override
        public void defaultVisit(Node node) {
            // just check if this is a memory node of some kind and then update dominance from preds
            // preds are visited first so we should have a valid dominant node here
            switch (node.getOpCode()) {
                case iro_Start -> {
                    MemNode start = memNodeMap.get(node);
                    start.getDominatingMem().add(start);
                }
                case iro_Div, iro_Load, iro_Store, iro_Mod, iro_Call, iro_Return, iro_Phi -> {
                    if (node.getOpCode() == iro_Phi) {
                        if (!Objects.equals(node.getMode(), Mode.getM())) {
                            return;
                        }
                    }
                    MemNode mem = memNodeMap.get(node);
                    if (mem == null) {
                        return;
                    }

                    // check all pred nodes if they have the same dominator otherwise set this as dominating
                    for (var pred : mem.getMemPreds()) {
                        if (pred.getN().getOpCode() == binding_irnode.ir_opcode.iro_Store || pred.getN().getOpCode() == iro_Call) {
                            mem.getDominatingMem().add(pred);
                        } else if (pred.getDominatingMem().size() > 1) {
                            // in case we have more incoming predecessors just merge them here
                            mem.getDominatingMem().add(pred);
                        } else {
                            // take dominance from predecessors
                            mem.getDominatingMem().addAll(pred.getDominatingMem());
                        }
                    }

                }
            }
        }
    }

    @Getter
    @Setter
    @RequiredArgsConstructor
    private static class MemNode {
        /**
         * the associated node
         */
        private final Node n;
        /**
         * succesor node with Mode M
         */
        private final Node mem;
        private final List<Node> memOuts;
        private Set<MemNode> dominatingMem = new HashSet<>();
        private Set<MemNode> memPreds = new HashSet<>();
        private final Set<MemNode> dominatedChildren = new HashSet<>();

        @Override
        public String toString() {
            String domMem;
            if (dominatingMem == null) {
                domMem = "dominated by none";
            } else {
                domMem = dominatingMem.stream().map(MemNode::getN).collect(Collectors.toList()).toString();
            }
            StringBuilder memPredString = new StringBuilder();
            for (var pred : memPreds) {
                memPredString.append(pred.getN());
            }
            String memOutsString = memOuts == null ? "" : memOuts.toString();
            return n.toString() + " memouts:" + memOutsString + " mempreds:" + memPredString.toString() + " dominated by " + domMem;
        }
    }

}
