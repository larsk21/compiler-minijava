package edu.kit.compiler.codegen;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import edu.kit.compiler.codegen.pattern.InstructionMatch;
import edu.kit.compiler.codegen.pattern.MatchVisitor;
import firm.Graph;
import firm.bindings.binding_irnode.ir_opcode;
import firm.nodes.Node;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

public class MatcherState {

    private final Graph graph;
    private final Map<Integer, Entry> entries = new HashMap<>();

    protected int registerCount;

    public MatcherState(Graph graph, int initialCount) {
        this.graph = graph;
        this.registerCount = initialCount;
    }

    public int getNewRegister() {
        return registerCount++;
    }

    public Entry getEntry(Node node) {
        return entries.get(node.getNr());
    }

    public InstructionMatch getMatch(Node node) {
        return getEntry(node).getMatch();
    }

    public Optional<Integer> getRegister(Node node) {
        var entry = getEntry(node);
        if (entry != null) {
            return entry.getRegister();
        } else {
            return Optional.empty();
        }
    }

    public void setMatch(Node node, InstructionMatch match) {
        assert !entries.containsKey(node.getNr());
        entries.put(node.getNr(), new Entry(match));
    }

    public Shadow getShadow() {
        return new Shadow(this);
    }

    public void update(Shadow shadow) {
        this.registerCount = shadow.registerCount;
    }

    public void walkTopological(MatchVisitor walker) {
        graph.incVisited();
        walkTopological(walker, graph.getEnd());
        // var entry = getEntry(graph.getEnd());

    }

    private void walkTopological(MatchVisitor walker, Node node) {
        // based on GraphBase#walkTopologicalHelper of jFirm
        var entry = getEntry(node);

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
        entry.getMatch().getPredecessors()
                .forEach(pred -> walkTopological(walker, pred));

        if (isLoopBreaker || !node.visited()) {
            entry.getMatch().accept(walker);
        }
        node.markVisited();
    }

    @RequiredArgsConstructor
    private static final class Entry {
        @Getter
        private final InstructionMatch match;

        public Optional<Integer> getRegister() {
            return match.getTargetRegister();
        }
    }

    public static final class Shadow extends MatcherState {

        private final MatcherState subject;

        private Shadow(MatcherState subject) {
            super(subject.graph, subject.registerCount);
            this.subject = subject;
        }

        @Override
        public InstructionMatch getMatch(Node node) {
            return subject.getMatch(node);
        }

        @Override
        public Optional<Integer> getRegister(Node node) {
            return subject.getRegister(node);
        }

        @Override
        public void setMatch(Node node, InstructionMatch match) {
            throw new UnsupportedOperationException("not allowed to set registers on shadow");
        }
    }
}
