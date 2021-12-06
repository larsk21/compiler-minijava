package edu.kit.compiler.optimizations;

import edu.kit.compiler.io.Worklist;
import firm.nodes.Node;
import firm.nodes.NodeVisitor;

/**
 * Firm node visitor that inserts all visited nodes in the given Worklist.
 */
public class WorklistFiller extends NodeVisitor.Default {

    /**
     * Create a new WorklistFiller with a given Worklist.
     */
    public WorklistFiller(Worklist<Node> worklist) {
        this.worklist = worklist;
    }

    private Worklist<Node> worklist;

    @Override
    public void defaultVisit(Node node) {
        worklist.enqueueInOrder(node);
    }

}
