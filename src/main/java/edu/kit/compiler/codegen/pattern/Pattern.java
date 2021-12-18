package edu.kit.compiler.codegen.pattern;

import edu.kit.compiler.codegen.MatcherState;
import firm.nodes.Node;

/**
 * A pattern that can be matched with a node and its children.
 */
public interface Pattern<T extends Match> {
    /**
     * Tries to match pattern against the given node. The `matches()` function
     * of the return value can be used to check if the node matches the pattern.
     * The given MatcherState is used to query for matches and allocated
     * register of predecessors, as well as to allocate new registers if needed.
     */
    T match(Node node, MatcherState matcher);
}
