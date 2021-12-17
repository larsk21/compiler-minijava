package edu.kit.compiler.codegen.pattern;

import java.util.stream.Stream;

import firm.nodes.Node;

public interface Match {

    public boolean matches();

    public Stream<Node> getPredecessors();
}
