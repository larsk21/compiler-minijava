package edu.kit.compiler.optimizations.common_subexpression;

import com.sun.jna.Pointer;
import edu.kit.compiler.io.DefaultWorklist;
import edu.kit.compiler.io.Worklist;
import edu.kit.compiler.optimizations.Optimization;
import edu.kit.compiler.optimizations.OptimizationState;
import edu.kit.compiler.optimizations.WorklistFiller;
import firm.*;
import firm.bindings.binding_irdom;
import firm.bindings.binding_irgopt;
import firm.bindings.binding_irnode;
import firm.nodes.*;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.util.*;

@RequiredArgsConstructor
@Data
public class CommonSubexpressionElimination implements Optimization.Local {

    // map that holds nodes that are to be exchanged with equivalent other nodes
    private final HashMap<Node, Node> nodeValues = new HashMap<>();
    private Worklist<Node> worklist = new DefaultWorklist<>();
    private final Map<Pointer, Map<Pointer, Integer>> dominateMap = new HashMap<>();

    @Override
    public boolean optimize(Graph g, OptimizationState state) {
        // compare new change map with old one
        boolean backEdgesEnabled = BackEdges.enabled(g);
        if (!backEdgesEnabled) {
            BackEdges.enable(g);
        }

        boolean hadChange = false;
        boolean changes;
        int maxChanges = 0;
        Dump.dumpGraph(g, "before-opt");
        do {
            changes = false;
            nodeValues.clear();
            dominateMap.clear();
            worklist = new DefaultWorklist<>();

            CSEVisitor visitor = new CSEVisitor();


            g.walkTopological(new WorklistFiller(worklist));
            while (!worklist.isEmpty()) {
                Node n = worklist.dequeue();
                n.accept(visitor);
            }

            for (var replacement : nodeValues.entrySet()) {
                // for each replacement merge nodes together
                Node orig = replacement.getKey();
                Node n = replacement.getValue();

                changes |= transform(g, orig, n, orig.getOpCode());
            }
            if (changes) {
                hadChange = true;
            }
            maxChanges++;
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
    private boolean transform(Graph g, Node orig, Node replacement, binding_irnode.ir_opcode opcode) {
        Node newNode = switch (opcode) {
            case iro_Add -> {
                Add add = (Add) orig;
                yield g.newAdd(orig.getBlock(), add.getLeft(), add.getRight());
            }
            case iro_And -> {
                And and = (And) orig;
                yield g.newAnd(orig.getBlock(), and.getLeft(), and.getRight());
            }
            case iro_Or -> {
                Or or = (Or) orig;
                yield g.newOr(orig.getBlock(), or.getLeft(), or.getRight());
            }
            case iro_Sub -> {
                Sub sub = (Sub) orig;
                yield g.newSub(orig.getBlock(), sub.getLeft(), sub.getRight());
            }
            case iro_Eor -> {
                Eor eor = (Eor) orig;
                yield g.newEor(orig.getBlock(), eor.getLeft(), eor.getRight());
            }
            case iro_Mul -> {
                Mul mul = (Mul) orig;
                yield g.newMul(orig.getBlock(), mul.getLeft(), mul.getRight());
            }
            case iro_Minus -> {
                Minus minus = (Minus) orig;
                yield g.newMinus(orig.getBlock(), minus.getOp());
            }
            case iro_Const -> {
                Const cons = (Const) orig;
                yield g.newConst(cons.getTarval().asInt(), cons.getMode());
            }
            case iro_Sel -> {
                Sel sel = (Sel) orig;
                yield g.newSel(orig.getBlock(), sel.getPtr(), sel.getIndex(), sel.getType());
            }
            case iro_Not -> {
                Not not = (Not) orig;
                yield g.newNot(orig.getBlock(), not.getOp());
            }
            case iro_Conv -> {
                Conv conv = (Conv) orig;
                yield g.newConv(orig.getBlock(), conv.getOp(), conv.getMode());
            }
            default -> null;
        };


        if (newNode != null) {
            Pointer newPointer = newNode.getBlock().ptr;
            Pointer origPointer = orig.getBlock().ptr;
            Pointer replacementPointer = replacement.getBlock().ptr;

            int dominatesOld = dominates(newPointer, origPointer);
            int dominatesNew = dominates(newPointer, replacementPointer);

            if (dominatesOld == 0 || dominatesNew == 0) {
                // can not replace node in this case since new node does not dominate one of the replacements
                return false;
            }
            // new node has to dominate all uses
            boolean usesDominated = dominatesUses(newNode, orig);
            boolean usesDominatedRep = dominatesUses(newNode, replacement);

            if (hasCallAsChild(orig) || hasCallAsChild(replacement)) {
                System.out.println("call as child do not replace");
                return false;
            }
            if (usesDominated && usesDominatedRep) {
                //      System.out.println("replaced node " + orig + " with " + newNode + " in graph " + g);
                Graph.exchange(orig, newNode);
                Graph.exchange(replacement, newNode);
                return true;
            } else {
                return false;
            }
        }
        return false;
    }

    private boolean hasCallAsChild(Node n) {
        for (BackEdges.Edge edge : BackEdges.getOuts(n)) {
            Node node = edge.node;
            if (node.getOpCode() == binding_irnode.ir_opcode.iro_Call) {
                return true;
            }
        }
        return false;
    }

    private int dominates(Pointer source, Pointer target) {
        int dominates = 0;

        if (!dominateMap.containsKey(source)) {
            // we have no entry yet, compute both values
            dominates = binding_irdom.block_dominates(source, target);

            HashMap<Pointer, Integer> cachedResults = new HashMap<>();
            cachedResults.put(target, dominates);
            dominateMap.put(source, cachedResults);

        } else {
            Map<Pointer, Integer> cachedResults = dominateMap.get(source);
            dominates = cachedResults.computeIfAbsent(target, p -> binding_irdom.block_dominates(source, target));
        }
        return dominates;
    }

    private boolean dominatesUses(Node newNode, Node orig) {
        for (BackEdges.Edge edge : BackEdges.getOuts(orig)) {
            Node n = edge.node;
            if (dominates(newNode.getBlock().ptr, n.getBlock().ptr) == 0) {
                return false;
            }
        }
        return true;
    }


    private final class CSEVisitor extends NodeVisitor.Default {

        private final Map<TargetValue, Node> constCache = new HashMap<>();
        private final Map<Entity, Node> addressCache = new HashMap<>();
        private final Map<NodePreds, Node> nodeCache = new HashMap<>();

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
                // can exchange node with already found in list
                nodeValues.put(node, n);
                // enqueue outs
            } else {
                nodeCache.put(np, node);
            }
        }

        private void addOuts(Node n) {
            for (var out : BackEdges.getOuts(n)) {
                worklist.enqueue(out.node);
            }
        }

        private Node[] iterableToArray(Iterable<Node> iterable) {
            List<Node> list = new ArrayList<>();
            for(var entry : iterable) {
                list.add(entry);
            }
            return list.toArray(new Node[0]);
        }

        @Override
        public void visit(Add node) {
            visitPreds(node, iterableToArray(node.getPreds()), binding_irnode.ir_opcode.iro_Add);
        }

        @Override
        public void visit(And node) {
            visitPreds(node, iterableToArray(node.getPreds()), binding_irnode.ir_opcode.iro_And);
        }

        @Override
        public void visit(Cmp node) {
            // do nothing
        }

        @Override
        public void visit(Cond node) {
            // do nothing
        }

        @Override
        public void visit(Proj proj) {
            // do nothing
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
            visitPreds(call, iterableToArray(call.getPreds()), binding_irnode.ir_opcode.iro_Call);
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
            visitPreds(node, iterableToArray(node.getPreds()), binding_irnode.ir_opcode.iro_Conv);
        }

        @Override
        public void visit(Div node) {
            visitPreds(node, iterableToArray(node.getPreds()), binding_irnode.ir_opcode.iro_Div);
        }

        @Override
        public void visit(Eor node) {
            visitPreds(node, iterableToArray(node.getPreds()), binding_irnode.ir_opcode.iro_Eor);
        }

        @Override
        public void visit(Minus node) {
            visitPreds(node, iterableToArray(node.getPreds()), binding_irnode.ir_opcode.iro_Minus);
        }

        @Override
        public void visit(Mod node) {
            visitPreds(node, iterableToArray(node.getPreds()), binding_irnode.ir_opcode.iro_Mod);
        }

        @Override
        public void visit(Mul node) {
            visitPreds(node, iterableToArray(node.getPreds()), binding_irnode.ir_opcode.iro_Mul);
        }

        @Override
        public void visit(Not node) {
            visitPreds(node, iterableToArray(node.getPreds()), binding_irnode.ir_opcode.iro_Not);
        }

        @Override
        public void visit(Or node) {
            visitPreds(node, iterableToArray(node.getPreds()), binding_irnode.ir_opcode.iro_Or);
        }

        @Override
        public void visit(Phi node) {
            // do nothing
        }

        @Override
        public void visit(Sub node) {
            visitPreds(node, iterableToArray(node.getPreds()), binding_irnode.ir_opcode.iro_Sub);
        }

        @Override
        public void visit(Shr node) {
            visitPreds(node, iterableToArray(node.getPreds()), binding_irnode.ir_opcode.iro_Shr);
        }

        @Override
        public void visit(Shl node) {
            visitPreds(node, iterableToArray(node.getPreds()), binding_irnode.ir_opcode.iro_Shl);
        }

        @Override
        public void visit(Shrs node) {
            visitPreds(node, iterableToArray(node.getPreds()), binding_irnode.ir_opcode.iro_Shrs);
        }

        @Override
        public void visit(Sel node) {
            visitPreds(node, iterableToArray(node.getPreds()), binding_irnode.ir_opcode.iro_Sel);
        }

        @Override
        public void visit(Load node) {
            visitPreds(node, iterableToArray(node.getPreds()), binding_irnode.ir_opcode.iro_Load);
        }

        @Override
        public void visit(Store node) {
            visitPreds(node, iterableToArray(node.getPreds()), binding_irnode.ir_opcode.iro_Store);
        }
    }
}
