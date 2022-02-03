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

        LoadStoreVisitor visitor = new LoadStoreVisitor();

        List<Node> nodes = new ArrayList<>();
        Util.NodeListFiller filler = new Util.NodeListFiller(nodes);
        g.walkTopological(filler);
        while (!nodes.isEmpty()) {
            Node n = nodes.remove(0);
            n.accept(visitor);
        }

        // calculate dominances for graph
        Map<MemNode, Boolean> hadDom = new HashMap<>();
        visitor.setPreds();
        DominanceVisitor domVisitor = new DominanceVisitor(visitor.getMemNodeMap(), state);

        // this is needed for loops etc that are not handled by post order very well
        g.walkPostorder(domVisitor);
        g.walkPostorder(domVisitor);
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

        // rewire everything
//        for (var entry : visitor.getMemNodeMap().values()) {
//            if (entry.getDominatingMem().size() == 1) {
//                Node n = entry.getN();
//                switch (n.getOpCode()) {
//                    case iro_Load -> {
//                        MemNode dominatingNode = entry.getDominatingMem().stream().findFirst().get();
//                        Node newMem = dominatingNode.getMem();
//                        if (newMem.getOpCode() == iro_Phi) {
//                            // phis produce errors so do not exchange them
//                            continue;
//                        }
//
//                        Load l = (Load) n;
//
//                        if (l.getMem() == newMem) {
//                            continue;
//                        } else if (!dominates(newMem.getBlock().ptr, l.getMem().getBlock().ptr)) {
//                            continue;
//                        }
//
//                        l.setMem(newMem);
//
//                        Node rhsMem = g.newProj(l, Mode.getM(), 0);
//                        // search in this block for nodes
//                        for (var dominatedMem : dominatingNode.getDominatedChildren()) {
//                            firm.bindings.binding_irnode.ir_opcode opcode = dominatedMem.getN().getOpCode();
//                            if (opcode == iro_Call) {
//                                Call c = (Call) dominatedMem.getN();
//                                Attributes a = state.getAttributeAnalysis().getAttributes(Util.getCallee(c));
//                                if (a.isPure()) {
//                                    continue;
//                                }
//                            }
//                            if (opcode == iro_Store || opcode == iro_Call) {
//                                Node memPred = dominatedMem.getN().getPred(0);
//                                if (memPred.getOpCode() == iro_Sync) {
//                                    Sync s = (Sync) memPred;
//                                    if (!dominates(rhsMem.getBlock().ptr, s.getBlock().ptr)) {
//                                        continue;
//                                    }
//
//                                    List<Node> newL = new ArrayList<>();
//                                    for (var nn : s.getPreds()) {
//                                        newL.add(nn);
//                                    }
//
//                                    newL.add(rhsMem);
//                                    Node newSync = g.newSync(s.getBlock(), newL.toArray(new Node[0]));
//                                    Graph.exchange(s, newSync);
//                                    deduplicateEquivalentOutMemNodes(n);
//                                } else {
//                                    Node[] nodes = new Node[]{rhsMem, memPred};
//                                    Pointer newBlockPtr = l.getBlock().ptr;
//                                    if (!dominates(rhsMem.getBlock().ptr, newBlockPtr) || !dominates(memPred.getBlock().ptr, newBlockPtr)) {
//                                        continue;
//                                    }
//                                    if (!dominates(l.getBlock().ptr, dominatedMem.getN().getBlock().ptr)) {
//                                        continue;
//                                    }
//                                    Node s = g.newSync(l.getBlock(), nodes);
//                                    dominatedMem.getN().setPred(0, s);
//                                    deduplicateEquivalentOutMemNodes(n);
//                                }
//
//                            }
//                        }
//                    }
//                }
//            }
//        }

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

    private void deduplicateEquivalentOutMemNodes(Node node) {
        // map from target to mem
        Map<Node, Node> singularOutMems = new HashMap<>();
        // map from node to replacement node
        Map<Node, Node> markedForDeletion = new HashMap<>();

        for (var edge : BackEdges.getOuts(node)) {
            Node n = edge.node;
            if (n.getOpCode() == iro_Proj && Objects.equals(n.getMode(), Mode.getM()) && n.getPredCount() == 1) {
                int countOuts = 0;
                for (var e : BackEdges.getOuts(n)) {
                    countOuts++;
                }
                if (countOuts != 1) {
                    continue;
                }
                Node target = BackEdges.getOuts(n).iterator().next().node;
                if (singularOutMems.containsKey(target)) {
                    markedForDeletion.put(n, singularOutMems.get(target));
                } else {
                    singularOutMems.put(target, n);
                }
            }
        }

        for (var del : markedForDeletion.entrySet()) {
            Graph.exchange(del.getKey(), del.getValue());
        }
    }

    @Getter
    private static final class LoadStoreVisitor extends NodeVisitor.Default {

        private Node rootNode = null;
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

        private Node getChildMemNode(Node n) {
            List<Node> children = getNodeChildren(n);
            Optional<Node> first = children.stream().filter(f -> Objects.equals(f.getMode(), Mode.getM())).findFirst();
            Optional<Node> returnNode = children.stream().filter(f -> f.getOpCode() == binding_irnode.ir_opcode.iro_Return).findFirst();

            if (first.isEmpty()) {
                if (returnNode.isEmpty()) {
                    if (n.getOpCode() == iro_Phi) {
                        // skip phis since they sometimes do not need mem
                        return null;
                    }
                    return null;
                } else {
                    return null;
                }
            }

            return first.get();
        }

        private void traceNode(Node orig) {
            if (rootNode == null) {
                rootNode = orig;
            }

            Node memNode = getChildMemNode(orig);

            // return nodes do not have any outgoing memory refernces
            if (memNode == null && orig.getOpCode() == binding_irnode.ir_opcode.iro_Return) {
                LoadStoreOptimization.MemNode newMem = new LoadStoreOptimization.MemNode(orig, memNode, null);
                memNodeMap.put(orig, newMem);
                return;
            }
            // phi nodes do not have to be memory nodes
            if (memNode == null && orig.getOpCode() != iro_Phi) {
                return;
            }
            // otherwise they themselves are mem
            if (orig.getOpCode() == iro_Phi) {
                memNode = orig;
            }

            if (!memNodeMap.containsKey(orig)) {
                memNodeMap.put(orig, null);

                List<Node> memOuts = traceMemOuts(memNode);
                LoadStoreOptimization.MemNode newMem = new LoadStoreOptimization.MemNode(orig, memNode, memOuts);
                if (orig == rootNode) {
                    newMem.getDominatingMem().add(newMem);
                    rootMemNode = newMem;
                }
                memNodeMap.put(orig, newMem);
            }
        }

        private List<Node> traceMemOuts(Node memNode) {
            List<Node> children = getNodeChildren(memNode);

            // create a new node that follows this nodes memory chain
            List<Node> memOuts = new ArrayList<>();
            for (var child : children) {
                switch (child.getOpCode()) {
                    case iro_Div, iro_Load, iro_Store, iro_Mod, iro_Call, iro_Return -> {
                        memOuts.add(child);
                        // go into childs recursively to build graph
                        child.accept(this);
                    }
                    case iro_Phi -> {
                        Phi phi = (Phi) child;
                        if (Objects.equals(phi.getMode(), Mode.getM())) {
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
            if (memNode == null) {
                return;
            }

            if (memNode.getDominatingMem() == null) {
                return;
            }

            if (memNode.getDominatingMem().size() > 1) {
                // can not replace in case of multiple incoming paths that may dominate this one
                return;
            }
            Optional<MemNode> dominatingMemOpt = memNode.getDominatingMem().stream().findFirst();
            if (dominatingMemOpt.isEmpty()) {
                // dominance analysis not deep enough just return and do nothing in this case
                return;
            }
            Node dominatingMem = dominatingMemOpt.get().getN();

            if (dominatingMem.getOpCode() == iro_Store) {// replace this node with stores input
                Store store = (Store) dominatingMem;
                Node value = store.getValue();

                int memCount = 0;
                for (var edge : BackEdges.getOuts(load)) {
                    Node node = edge.node;
                    if (Objects.equals(node.getMode(), Mode.getM())) {
                        memCount++;
                    }
                }

                if (memCount > 1) {
                    return;
                }

                // only replace load if they point to the same object
                if (store.getPtr().equals(load.getPtr())) {
                    for (var edge : BackEdges.getOuts(load)) {
                        Node node = edge.node;
                        if (node.getOpCode() == iro_Proj && !Objects.equals(node.getMode(), Mode.getM())) {
                            // replace projection output with
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
                            memOutNode.setPred(0, dominatingMemOpt.get().getMem());
                        }
                    }
                    Graph.killNode(load);
                }
            }
        }
    }

    @RequiredArgsConstructor
    private static class DominanceVisitor extends NodeVisitor.Default {

        private final Map<Node, MemNode> memNodeMap;
        private final OptimizationState state;

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
                        Phi phi = (Phi) node;
                        if (!Objects.equals(phi.getMode(), Mode.getM())) {
                            return;
                        }
                    }
                    MemNode mem = memNodeMap.get(node);
                    if (mem == null) {
                        return;
                    }

                    // check all pred nodes if they have the same dominator otherwise set this as dominating
                    for (var pred : mem.getMemPreds()) {
                        if (pred.getN().getOpCode() == iro_Call) {
                            Call c = (Call) pred.getN();
                            Attributes a = state.getAttributeAnalysis().getAttributes(Util.getCallee(c));
                            if (a.isPure()) {
                                mem.getDominatingMem().addAll(pred.getDominatingMem());
                                continue;
                            }
                        }

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
        private final Node n;
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
