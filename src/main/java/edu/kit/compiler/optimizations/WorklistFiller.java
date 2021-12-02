package edu.kit.compiler.optimizations;

import edu.kit.compiler.io.Worklist;
import firm.nodes.Node;

/**
 * Firm node visitor that inserts all visited nodes in the given Worklist.
 */
public class WorklistFiller implements PartialNodeVisitor {

    /**
     * Create a new WorklistFiller with a given Worklist.
     */
    public WorklistFiller(Worklist<Node> worklist) {
        this.worklist = worklist;
    }

    private Worklist<Node> worklist;

    @Override
    public void visitUnknown(Node node) {
        worklist.enqueueInOrder(node);
    }

}
