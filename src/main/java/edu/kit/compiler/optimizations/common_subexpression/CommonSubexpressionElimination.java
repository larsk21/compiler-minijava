package edu.kit.compiler.optimizations.common_subexpression;

import com.sun.jna.Pointer;

import edu.kit.compiler.io.StackWorklist;
import edu.kit.compiler.optimizations.Optimization;
import edu.kit.compiler.optimizations.OptimizationState;
import edu.kit.compiler.optimizations.Util;
import firm.*;
import firm.bindings.binding_irdom;
import firm.bindings.binding_irgopt;
import firm.bindings.binding_irnode.ir_opcode;
import firm.bindings.binding_irnode;
import firm.nodes.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;

import java.util.*;

import static firm.bindings.binding_irgraph.ir_graph_properties_t.IR_GRAPH_PROPERTY_CONSISTENT_DOMINANCE;

@RequiredArgsConstructor
@Data
public class CommonSubexpressionElimination implements Optimization.Local {

    private static final int MAX_INDIRECTIONS = 128;
    private static final int MAX_CHANGES = 10;

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

        boolean changes;
        int numChanges = 0;

        do {
            changes = false;
            nodeValues.clear();
            dominateMap.clear();

            CSEVisitor visitor = new CSEVisitor(state);

            var worklist = new StackWorklist<Node>(false);
            g.walkTopological(new Util.NodeWorklistFiller(worklist));
            while (!worklist.isEmpty()) {
                worklist.dequeue().accept(visitor);
            }

            HashMap<Node, Node> replacementMap = new HashMap<>();
            for (var replacement : nodeValues.entrySet()) {
                // for each replacement merge nodes together
                Node orig = findReplacement(replacement.getKey(), replacementMap);
                Node node = findReplacement(replacement.getValue(), replacementMap);

                if (orig != null && node != null) {
                    assert orig.getOpCode() == node.getOpCode();
                    changes |= transform(g, orig, node, orig.getOpCode(), replacementMap);
                }
            }

            binding_irgopt.remove_bads(g.ptr);
            binding_irgopt.remove_unreachable_code(g.ptr);
            binding_irgopt.remove_bads(g.ptr);
        } while (changes && (numChanges += 1) < MAX_CHANGES);

        if (!backEdgesEnabled) {
            BackEdges.disable(g);
        }

        return numChanges != 0;
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
            case iro_Call -> {
                Call call = (Call) replacement;
                Node[] preds = Util.iterableToArray(call.getPreds());
                int newSize = preds.length - 2;
                Node[] ins = new Node[newSize];
                if (preds.length - 2 >= 0) {
                    System.arraycopy(preds, 2, ins, 0, preds.length - 2);
                }
                yield g.newCall(call.getBlock(), call.getMem(), call.getPtr(), ins, call.getType());
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
        return binding_irdom.block_dominates(source, target) != 0;
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

    private static Node findReplacement(Node node, Map<Node, Node> replacements) {
        for (int i = 0; i < MAX_INDIRECTIONS; ++i) {
            node = replacements.get(node);
            if (node == null || node.getOpCode() != ir_opcode.iro_Deleted) {
                return node;
            }
        }
        return null;
    }

    @RequiredArgsConstructor
    private final class CSEVisitor extends NodeVisitor.Default {

        private final OptimizationState s;

        private final Map<TargetValue, Node> constCache = new HashMap<>();
        private final Map<Entity, Node> addressCache = new HashMap<>();
        private final Map<NodePreds, Node> nodeCache = new HashMap<>();
        private final Map<ProjPreds, Node> projCache = new HashMap<>();

        @RequiredArgsConstructor
        @EqualsAndHashCode
        private class ProjPreds {
            private final Node pred;
            private final int num;
            private final Mode mode;
        }

        @RequiredArgsConstructor
        @EqualsAndHashCode
        private class NodePreds {
            private final Node[] preds;
            private final binding_irnode.ir_opcode opcode;
            private final Mode mode;
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
        public void visit(Eor node) {
            visitPreds(node, Util.iterableToArray(node.getPreds()), binding_irnode.ir_opcode.iro_Eor);
        }

        @Override
        public void visit(Minus node) {
            visitPreds(node, Util.iterableToArray(node.getPreds()), binding_irnode.ir_opcode.iro_Minus);
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
    }
}
