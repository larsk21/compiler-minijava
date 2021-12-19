package edu.kit.compiler.codegen.pattern;

import java.util.stream.Stream;

import firm.nodes.Node;

/**
 * Base for all matches used during instruction selection. Matches are returned
 * by `Pattern#match(..)`.
 * In practice two fundamentally distinct types for match are used to match
 * either entire instructions or operands to instructions.
 */
public interface Match {
    /**
     * Returns true if the match is valid. It is only safe to call other
     * other functions of a match, if this method return true.
     */
    boolean matches();

    /**
     * Returns a stream of all predecessors of this match. This is used to build
     * a graph of matches closely related to the Firm graph. However, not all
     * predecessors present in the firm graph must also be part of this stream.
     * For example, for a node like `Add(Rest, Const)` may not include the Const
     * node in its predecessors.
     * This graph of matches is need to only generate instruction that are
     * actually need, while maintaining their correct order.
     */
    Stream<Node> getPredecessors();
}
