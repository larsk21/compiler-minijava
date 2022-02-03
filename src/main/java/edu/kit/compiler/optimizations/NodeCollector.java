package edu.kit.compiler.optimizations;

import firm.nodes.Node;
import firm.nodes.NodeVisitor;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
public class NodeCollector extends NodeVisitor.Default {

    private final List<Node> nodes = new ArrayList<>();

    @Override
    public void defaultVisit(Node node) {
        nodes.add(node);
    }

}
