package edu.kit.compiler.codegen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import edu.kit.compiler.codegen.pattern.InstructionMatch;
import edu.kit.compiler.codegen.pattern.InstructionMatchVisitor;
import edu.kit.compiler.intermediate_lang.RegisterSize;
import firm.Graph;
import firm.bindings.binding_irnode.ir_opcode;
import firm.nodes.Node;

public class MatcherState {

    private final Map<Integer, InstructionMatch> matches = new HashMap<>();

    protected final Graph graph;
    protected final Map<Integer, Integer> phiRegisters = new HashMap<>();
    protected final List<RegisterSize> registerSizes = new ArrayList<>();

    // protected int registerCount;

    public MatcherState(Graph graph, int initialCount) {
        this.graph = graph;

        for (int i = 0; i < initialCount; ++i) {
            registerSizes.add(null);
        }
    }

    public int getNewRegister(RegisterSize size) {
        var register = registerSizes.size();
        registerSizes.add(size);
        return register;
    }

    public InstructionMatch getMatch(Node node) {
        return matches.get(node.getNr());
    }

    /**
     * !! NOT the same as `getMatch(node).getRegister()` !!
     */
    public Optional<Integer> getRegister(Node node) {
        return  switch (node.getOpCode()) {
            case iro_Phi -> {
                var size = Util.getSize(node.getMode());
                yield Optional.of(getPhiRegister(node, size));
            }
            default -> {
                var match = getMatch(node);
                yield match == null ? Optional.empty() : match.getTargetRegister();
            }
        };
    }

    public RegisterSize getRegisterSize(int register) {
        return registerSizes.get(register);
    }

    public int getPhiRegister(Node phi, RegisterSize size) {
        assert phi.getOpCode() == ir_opcode.iro_Phi;
        return getPhiRegister(phi.getNr(), size);
    }

    public void setMatch(Node node, InstructionMatch match) {
        assert node.getNr() == match.getNode().getNr();
        matches.put(node.getNr(), match);
    }

    public void setRegisterSize(int register, RegisterSize size) {
        assert registerSizes.get(register) == null;
        registerSizes.set(register, size);
    }

    public void walkTopological(InstructionMatchVisitor walker) {
        graph.incVisited();
        walkTopological(walker, graph.getEnd());
    }

    private void walkTopological(InstructionMatchVisitor walker, Node node) {
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

    private int getPhiRegister(int node, RegisterSize size) {
        return phiRegisters.computeIfAbsent(node, i -> getNewRegister(size));
    }
}
