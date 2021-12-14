package edu.kit.compiler.codegen.pattern;

import edu.kit.compiler.codegen.NodeRegisters;
import firm.nodes.Node;

public interface Pattern<T extends Match> {
    public T match(Node node, NodeRegisters registers);
}
