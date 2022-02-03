package edu.kit.compiler.optimizations.common_subexpression;

import com.sun.jna.Pointer;
import edu.kit.compiler.optimizations.Optimization;
import edu.kit.compiler.optimizations.OptimizationState;
import edu.kit.compiler.optimizations.Util;
import firm.*;
import firm.bindings.binding_irdom;
import firm.bindings.binding_irgopt;
import firm.bindings.binding_ircons.op_pin_state;
import firm.bindings.binding_irnode;
import firm.nodes.*;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.util.*;

import static firm.bindings.binding_irgraph.ir_graph_properties_t.IR_GRAPH_PROPERTY_CONSISTENT_DOMINANCE;

@RequiredArgsConstructor
@Data
public class CommonSubexpressionElimination implements Optimization.Local {

    // map that holds nodes that are to be exchanged with equivalent other nodes
    private final HashMap<Node, Node> nodeValues = new HashMap<>();
    private final Map<Pointer, Map<Pointer, Integer>> dominateMap = new HashMap<>();

    @Override
    public boolean optimize(Graph g, OptimizationState state) {
        g.assureProperties(IR_GRAPH_PROPERTY_CONSISTENT_DOMINANCE);

        boolean backEdgesEnabled = BackEdges.enabled(g);
        if (!backEdgesEnabled) {
            BackEdges.enable(g);
        }

        boolean hadChange = false;
        boolean changes;
        int maxChanges = 0;

        do {
            changes = false;
            nodeValues.clear();
            dominateMap.clear();

            CSEVisitor visitor = new CSEVisitor(state);

            List<Node> nodes = new ArrayList<>();
            Util.NodeListFiller nodeCollector = new Util.NodeListFiller(nodes);
            g.walkTopological(nodeCollector);
            while (!nodes.isEmpty()) {
                Node n = nodes.remove(0);
                n.accept(visitor);
            }

            HashMap<Node, Node> replacementMap = new HashMap<>();
            for (var replacement : nodeValues.entrySet()) {
                // for each replacement merge nodes together
                Node orig = replacement.getKey();
                Node n = replacement.getValue();

                int maxIndirections = 150;
                while (orig.getOpCode() == binding_irnode.ir_opcode.iro_Deleted && maxIndirections > 0) {
                    orig = replacementMap.getOrDefault(orig, orig);
                    maxIndirections--;
                }

                while (n.getOpCode() == binding_irnode.ir_opcode.iro_Deleted && maxIndirections > 0) {
                    n = replacementMap.getOrDefault(n, n);
                    maxIndirections--;
                }

                if (maxIndirections == 0) {
                    continue;
                }

                if (orig.getOpCode() != n.getOpCode()) {
                    throw new RuntimeException("wrrong");
                }
                changes |= transform(g, orig, n, orig.getOpCode(), replacementMap);
            }
            if (changes) {
                hadChange = true;
                maxChanges++;
            }

            binding_irgopt.remove_bads(g.ptr);
            binding_irgopt.remove_unreachable_code(g.ptr);
            binding_irgopt.remove_bads(g.ptr);
        } while (changes && maxChanges < 10);

        if (!backEdgesEnabled) {
            BackEdges.disable(g);
        }
        binding_irgopt.remove_bads(g.ptr);
        binding_irgopt.remove_unreachable_code(g.ptr);
        binding_irgopt.remove_bads(g.ptr);

        return hadChange;
    }

    /**
     * Transform the given node with the given associated lattice element if
     * that element is constant.
     */
    private boolean transform(Graph g, Node orig, Node replacement, binding_irnode.ir_opcode opcode, HashMap<Node, Node> replacementMap) {
        Node newNode = switch (opcode) {
            case iro_Add -> {
                Add add = (Add) replacement;
                yield g.newAdd(replacement.getBlock(), add.getLeft(), add.getRight());
            }
            case iro_And -> {
                And and = (And) replacement;
                yield g.newAnd(replacement.getBlock(), and.getLeft(), and.getRight());
            }
            case iro_Or -> {
                Or or = (Or) replacement;
                yield g.newOr(replacement.getBlock(), or.getLeft(), or.getRight());
            }
            case iro_Sub -> {
                Sub sub = (Sub) replacement;
                yield g.newSub(replacement.getBlock(), sub.getLeft(), sub.getRight());
            }
            case iro_Eor -> {
                Eor eor = (Eor) replacement;
                yield g.newEor(replacement.getBlock(), eor.getLeft(), eor.getRight());
            }
            case iro_Mul -> {
                Mul mul = (Mul) replacement;
                yield g.newMul(replacement.getBlock(), mul.getLeft(), mul.getRight());
            }
            case iro_Minus -> {
                Minus minus = (Minus) replacement;
                yield g.newMinus(replacement.getBlock(), minus.getOp());
            }
            case iro_Const -> {
                Const cons = (Const) replacement;
                yield g.newConst(cons.getTarval());
            }
            case iro_Not -> {
                Not not = (Not) replacement;
                yield g.newNot(replacement.getBlock(), not.getOp());
            }
            case iro_Conv -> {
                Conv conv = (Conv) replacement;
                yield g.newConv(replacement.getBlock(), conv.getOp(), conv.getMode());
            }
            case iro_Proj -> {
                Proj proj = (Proj) replacement;
                yield g.newProj(proj.getPred(), proj.getMode(), proj.getNum());
            }
            case iro_Shr -> {
                Shr shr = (Shr) replacement;
                yield g.newShr(replacement.getBlock(), shr.getLeft(), shr.getRight());
            }
            case iro_Shl -> {
                Shl shl = (Shl) replacement;
                yield g.newShl(replacement.getBlock(), shl.getLeft(), shl.getRight());
            }
            case iro_Shrs -> {
                Shrs shrs = (Shrs) replacement;
                yield g.newShrs(replacement.getBlock(), shrs.getLeft(), shrs.getRight());
            }
            case iro_Div -> {
                Div div = (Div) replacement;
                yield g.newDiv(replacement.getBlock(), div.getMem(), div.getLeft(),
                        div.getRight(), op_pin_state.op_pin_state_pinned);
            }
            case iro_Mod -> {
                Mod mod = (Mod) replacement;
                yield g.newMod(replacement.getBlock(), mod.getMem(), mod.getLeft(),
                        mod.getRight(), op_pin_state.op_pin_state_pinned);
            }
            case iro_Call -> {
                Call call = (Call) replacement;
                Node[] preds = Util.iterableToArray(call.getPreds());
                int newSize = preds.length - 2;
                Node[] ins = new Node[newSize];
                if (preds.length - 2 >= 0) {
                    System.arraycopy(preds, 2, ins, 0, preds.length - 2);
                }
                yield g.newCall(replacement.getBlock(), call.getMem(), call.getPtr(), ins, call.getType());
            }
            default -> null;
        };


        if (newNode != null) {
            Pointer newPointer = newNode.getBlock().ptr;
            Pointer origPointer = orig.getBlock().ptr;
            Pointer replacementPointer = replacement.getBlock().ptr;

            boolean dominatesOld = dominates(newPointer, origPointer);
            boolean dominatesNew = dominates(newPointer, replacementPointer);

            if (!dominatesOld || !dominatesNew) {
                // can not replace node if the new node isnt dominating both former nodes.
                return false;
            }
            // new node has to dominate all uses
            boolean usesDominated = dominatesUses(newNode, orig);
            boolean usesDominatedRep = dominatesUses(newNode, replacement);

            if (usesDominated && usesDominatedRep) {
                replacementMap.put(replacement, newNode);
                replacementMap.put(orig, newNode);
                Graph.exchange(replacement, newNode);
                Graph.exchange(orig, newNode);
                return true;
            } else {
                return false;
            }
        }
        return false;
    }

    private boolean dominates(Pointer source, Pointer target) {
        int dominates = 0;
        dominates = binding_irdom.block_dominates(source, target);
        return dominates != 0;
    }

    private boolean dominatesUses(Node newNode, Node orig) {
        for (BackEdges.Edge edge : BackEdges.getOuts(orig)) {
            Node n = edge.node;
            if (!dominates(newNode.getBlock().ptr, n.getBlock().ptr)) {
                return false;
            }
        }
        return true;
    }

    private final class CSEVisitor extends NodeVisitor.Default {

        private OptimizationState s;

        public CSEVisitor(OptimizationState s) {
            this.s = s;
        }

        private final Map<TargetValue, Node> constCache = new HashMap<>();
        private final Map<Entity, Node> addressCache = new HashMap<>();
        private final Map<NodePreds, Node> nodeCache = new HashMap<>();
        private final Map<ProjPreds, Node> projCache = new HashMap<>();

        @RequiredArgsConstructor
        private class ProjPreds {
            private final Node pred;
            private final int num;
            private final Mode mode;

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;
                ProjPreds projPreds = (ProjPreds) o;
                return num == projPreds.num && pred.equals(projPreds.pred) && mode.equals(projPreds.mode);
            }

            @Override
            public int hashCode() {
                return Objects.hash(pred, num, mode);
            }
        }

        @RequiredArgsConstructor
        private class NodePreds {
            private final Node[] preds;
            private final binding_irnode.ir_opcode opcode;
            private final Mode mode;

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;
                NodePreds nodePreds = (NodePreds) o;
                return Arrays.equals(preds, nodePreds.preds) && opcode == nodePreds.opcode && mode.equals(nodePreds.mode);
            }

            @Override
            public int hashCode() {
                int result = Objects.hash(opcode, mode);
                result = 31 * result + Arrays.hashCode(preds);
                return result;
            }
        }

        @Override
        public void defaultVisit(Node node) {
            // change nothing
        }

        private void visitPreds(Node node, Node[] preds, binding_irnode.ir_opcode opcode) {
            NodePreds np = new NodePreds(preds, opcode, node.getMode());
            if (nodeCache.containsKey(np)) {
                Node n = nodeCache.get(np);
                if (dominates(n.getBlock().ptr, node.getBlock().ptr)) {
                    nodeValues.put(node, n);
                } else {
                    // replace and update cache
                    nodeValues.put(n, node);
                    nodeCache.put(np, node);
                }
            } else {
                nodeCache.put(np, node);
            }
        }


        @Override
        public void visit(Add node) {
            visitPreds(node, Util.iterableToArray(node.getPreds()), binding_irnode.ir_opcode.iro_Add);
        }

        @Override
        public void visit(And node) {
            visitPreds(node, Util.iterableToArray(node.getPreds()), binding_irnode.ir_opcode.iro_And);
        }

        @Override
        public void visit(Cond node) {
            // do nothing
        }

        @Override
        public void visit(Proj proj) {
            ProjPreds projPreds = new ProjPreds(proj.getPred(), proj.getNum(), proj.getMode());
            if (projCache.containsKey(projPreds)) {
                Node n = projCache.get(projPreds);
                if (dominates(n.getBlock().ptr, proj.getBlock().ptr)) {
                    nodeValues.put(proj, n);
                } else {
                    nodeValues.put(n, proj);
                    projCache.put(projPreds, proj);
                }
            } else {
                projCache.put(projPreds, proj);
            }
        }

        @Override
        public void visit(Address addr) {
            Entity ent = addr.getEntity();
            if (addressCache.containsKey(ent)) {
                Node newAddr = addressCache.get(ent);
                Graph.exchange(addr, newAddr);
            } else {
                addressCache.put(ent, addr);
            }
        }

        @Override
        public void visit(Call call) {
            var analysis = s.getAttributeAnalysis();
            var attributes = analysis.getAttributes(Util.getCallee(call));
            if (!Util.isPinned(call) && attributes.canDeduplicate()) {
                visitPreds(call, Util.iterableToArray(call.getPreds()), binding_irnode.ir_opcode.iro_Call);
            }
        }

        @Override
        public void visit(Const node) {
            TargetValue tarVal = node.getTarval();
            if (constCache.containsKey(tarVal)) {
                Node newConst = constCache.get(tarVal);
                Graph.exchange(node, newConst);
            } else {
                constCache.put(tarVal, node);
            }
        }

        @Override
        public void visit(Conv node) {
            visitPreds(node, Util.iterableToArray(node.getPreds()), binding_irnode.ir_opcode.iro_Conv);
        }

        @Override
        public void visit(Div node) {
            visitPreds(node, Util.iterableToArray(node.getPreds()), binding_irnode.ir_opcode.iro_Div);
        }

        @Override
        public void visit(Eor node) {
            visitPreds(node, Util.iterableToArray(node.getPreds()), binding_irnode.ir_opcode.iro_Eor);
        }

        @Override
        public void visit(Minus node) {
            visitPreds(node, Util.iterableToArray(node.getPreds()), binding_irnode.ir_opcode.iro_Minus);
        }

        @Override
        public void visit(Mod node) {
            visitPreds(node, Util.iterableToArray(node.getPreds()), binding_irnode.ir_opcode.iro_Mod);
        }

        @Override
        public void visit(Mul node) {
            visitPreds(node, Util.iterableToArray(node.getPreds()), binding_irnode.ir_opcode.iro_Mul);
        }

        @Override
        public void visit(Not node) {
            visitPreds(node, Util.iterableToArray(node.getPreds()), binding_irnode.ir_opcode.iro_Not);
        }

        @Override
        public void visit(Or node) {
            visitPreds(node, Util.iterableToArray(node.getPreds()), binding_irnode.ir_opcode.iro_Or);
        }

        @Override
        public void visit(Phi node) {
            // do nothing
        }

        @Override
        public void visit(Sub node) {
            visitPreds(node, Util.iterableToArray(node.getPreds()), binding_irnode.ir_opcode.iro_Sub);
        }

        @Override
        public void visit(Shr node) {
            visitPreds(node, Util.iterableToArray(node.getPreds()), binding_irnode.ir_opcode.iro_Shr);
        }

        @Override
        public void visit(Shl node) {
            visitPreds(node, Util.iterableToArray(node.getPreds()), binding_irnode.ir_opcode.iro_Shl);
        }

        @Override
        public void visit(Shrs node) {
            visitPreds(node, Util.iterableToArray(node.getPreds()), binding_irnode.ir_opcode.iro_Shrs);
        }

        @Override
        public void visit(Load node) {
            visitPreds(node, Util.iterableToArray(node.getPreds()), binding_irnode.ir_opcode.iro_Load);
        }

        @Override
        public void visit(Store node) {
            visitPreds(node, Util.iterableToArray(node.getPreds()), binding_irnode.ir_opcode.iro_Store);
        }
    }
}
