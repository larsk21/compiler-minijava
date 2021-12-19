package edu.kit.compiler.codegen;

import java.util.ArrayList;
import java.util.Collections;
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

/**
 * Holds state during the matching phase of instruction selection.
 */
public class MatcherState {

    private final Map<Integer, InstructionMatch> matches = new HashMap<>();

    protected final Graph graph;
    protected final Map<Integer, Integer> phiRegisters = new HashMap<>();
    protected final List<RegisterSize> registerSizes;

    /**
     * Initializes the state with the given Firm graph.
     */
    public MatcherState(Graph graph) {
        this(graph, new ArrayList<>());
    }

    /**
     * Initializes the state with the given Firm graph and the given registers.
     * This constructor is used to initialize parameters of a function.
     */
    public MatcherState(Graph graph, List<RegisterSize> initialRegisters) {
        this.graph = graph;
        this.registerSizes = initialRegisters;
    }

    /**
     * Returns the number of a new virtual register, unique to this function.
     * The size of the register is set according to the given RegisterSize.
     */
    public int getNewRegister(RegisterSize size) {
        var register = registerSizes.size();
        registerSizes.add(size);
        return register;
    }

    /**
     * Returns the match found for the given Firm node, or null if no match has
     * been registered for the node yet.
     */
    public InstructionMatch getMatch(Node node) {
        return matches.get(node.getNr());
    }

    /**
     * Returns the target register of the given node. For almost all nodes this
     * equates to looking up the match for the node and returning its target
     * register. If the node is a Phi, the register is instead queried
     * separately and a new register is allocated if necessary.
     * This special handling for Phi registers is need to deal with cycles in
     * the Firm graph. The target register of a Phi may be needed for it and
     * all its predecessors can be matched.
     */
    public Optional<Integer> getRegister(Node node) {
        return switch (node.getOpCode()) {
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

    /**
     * Returns the size of the virtual register with the given number.
     */
    public RegisterSize getRegisterSize(int register) {
        return registerSizes.get(register);
    }

    /**
     * Returns the existing target register of the given Phi node, or allocates
     * it using the given size if necessary.
     */
    public int getPhiRegister(Node phi, RegisterSize size) {
        assert phi.getOpCode() == ir_opcode.iro_Phi;
        return getPhiRegister(phi.getNr(), size);
    }

    /**
     * Return an immutable list containing the sizes of all registers.
     */
    public List<RegisterSize> getRegisterSizes() {
        return Collections.unmodifiableList(registerSizes);
    }

    /**
     * Register the given match for the given node.
     */
    public void setMatch(Node node, InstructionMatch match) {
        assert node.getNr() == match.getNode().getNr();
        matches.put(node.getNr(), match);
    }

    /**
     * Walk the matches in topological order. This is functionally the same as
     * the function implemented for Firm graphs, except that instead of using
     * the predecessors of a node to recurse, it uses those nodes return by
     * `Match#getPredecessors`. 
     */
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
        Iterable<Node> iterable = () -> match.getPredecessors().iterator();
        for (var pred : iterable) {
            walkTopological(walker, pred);
        }

        if (isLoopBreaker || !node.visited()) {
            match.accept(walker);
        }
        node.markVisited();
    }

    private int getPhiRegister(int node, RegisterSize size) {
        return phiRegisters.computeIfAbsent(node, i -> getNewRegister(size));
    }
}
