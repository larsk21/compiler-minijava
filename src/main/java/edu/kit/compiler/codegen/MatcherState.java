package edu.kit.compiler.codegen;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import edu.kit.compiler.codegen.pattern.InstructionMatch;
import edu.kit.compiler.codegen.pattern.MatchVisitor;
import firm.Graph;
import firm.bindings.binding_irnode.ir_opcode;
import firm.nodes.Node;

public class MatcherState {

    protected final Graph graph;
    private final Map<Integer, InstructionMatch> matches = new HashMap<>();
    protected final Map<Integer, Integer> phiRegisters = new HashMap<>();

    protected int registerCount;

    public MatcherState(Graph graph, int initialCount) {
        this.graph = graph;
        this.registerCount = initialCount;
    }

    public int getNewRegister() {
        return registerCount++;
    }

    public InstructionMatch getMatch(Node node) {
        return matches.get(node.getNr());
    }

    /**
     * !! NOT the same as `getMatch(node).getRegister()` !!
     */
    public Optional<Integer> getRegister(Node node) {
        if (node.getOpCode() == ir_opcode.iro_Phi) {
            return Optional.of(getPhiRegister(node.getNr()));
        } else {
            var match = getMatch(node);
            if (match != null) {
                return match.getTargetRegister();
            } else {
                return Optional.empty();
            }
        }
    }

    public int getPhiRegister(Node phi) {
        assert phi.getOpCode() == ir_opcode.iro_Phi;
        return getPhiRegister(phi.getNr());
    }

    public void setMatch(Node node, InstructionMatch match) {
        // todo is the separate param for node still necessary?
        assert node.getNr() == match.getNode().getNr();
        matches.put(node.getNr(), match);
    }

    public void walkTopological(MatchVisitor walker) {
        graph.incVisited();
        walkTopological(walker, graph.getEnd());
    }

    private void walkTopological(MatchVisitor walker, Node node) {
        // based on GraphBase#walkTopologicalHelper of jFirm
        if (node.visited()) {
            return;
        }

        boolean isLoopBreaker = node.getOpCode() == ir_opcode.iro_Phi
                || node.getOpCode() == ir_opcode.iro_Block;
        if (isLoopBreaker) {
            node.markVisited();
        }

        if (node.getBlock() != null) {
            walkTopological(walker, node.getBlock());
        }

        var match = getMatch(node);
        // ?! is this going to be a problem?
        match.getPredecessors().forEach(pred -> walkTopological(walker, pred));

        if (isLoopBreaker || !node.visited()) {
            match.accept(walker);
        }
        node.markVisited();
    }

    private int getPhiRegister(int node) {
        return phiRegisters.computeIfAbsent(node, i -> getNewRegister());
    }
}
