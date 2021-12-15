package edu.kit.compiler.codegen.pattern;

import edu.kit.compiler.codegen.MatcherState;
import firm.nodes.Node;

public interface Pattern<T extends Match> {
    public T match(Node node, MatcherState matcher);
}
