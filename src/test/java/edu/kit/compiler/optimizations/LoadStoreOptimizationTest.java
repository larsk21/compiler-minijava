package edu.kit.compiler.optimizations;

import firm.Graph;
import firm.bindings.binding_irnode;
import firm.nodes.Node;
import firm.nodes.NodeVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LoadStoreOptimizationTest {

    private Graph build(String fileName) throws IOException {
        int numGraphs = FirmGraphGenerator.getGraphCount();
        List<Graph> graphs = FirmGraphGenerator.getFirmGraphs(fileName);
        // return new graphs that are generated here
        return graphs.get(numGraphs);
    }

    private String surroundWithIO(String program, String expression) {
        return "System.in.read()\n"
                + program + "\n"
                + "System.out.write(" + expression + ")";
    }

    private Node getNodeById(int id, Graph graph) {
        List<Node> nodes = new ArrayList<>();
        NodeVisitor nodeVisitor = new NodeVisitor.Default() {
            @Override
            public void defaultVisit(Node node) {
                nodes.add(node);
            }
        };
        graph.walkPostorder(nodeVisitor);
        for (var n : nodes) {
            if (n.getNr() == id) {
                return n;
            }
        }
        throw new RuntimeException("node not found " + id);
    }

    private List<Node> getNodes(Graph graph) {
        List<Node> nodes = new ArrayList<>();
        NodeVisitor nodeVisitor = new NodeVisitor.Default() {
            @Override
            public void defaultVisit(Node node) {
                nodes.add(node);
            }
        };
        graph.walkPostorder(nodeVisitor);
        return nodes;
    }

    private int getNodeSize(Graph graph) {
        return getNodes(graph).size();
    }

    private void assertContainsOpCode(Graph g, binding_irnode.ir_opcode opcode) {
        List<Node> nodes = getNodes(g);
        assertTrue(nodes.stream().anyMatch(node -> node.getOpCode() == opcode));
    }

    private void assertDoesNotContainOpCode(Graph g, binding_irnode.ir_opcode opcode) {
        List<Node> nodes = getNodes(g);
        assertFalse(nodes.stream().anyMatch(node -> node.getOpCode() == opcode));
    }

    @Test
    public void testSimple() throws IOException {
        // int a = b * c + g;
        // int d = b * c * e;
        // return a + d;
        // ->
        // int tmp = b * c;
        // int a = tmp + g;
        // int d = tmp * e;
        // return a + d;
        String file = "edu/kit/compiler/optimizations/Subexpressions.java";
        Graph g = build(file);
        LoadStoreOptimization optimization = new LoadStoreOptimization();
        boolean changed = optimization.optimize(g, new OptimizationState());
    }


    @Test
    public void testLoadInLoop() throws IOException {
        // int a = b * c + g;
        // int d = b * c * e;
        // return a + d;
        // ->
        // int tmp = b * c;
        // int a = tmp + g;
        // int d = tmp * e;
        // return a + d;
        String file = "edu/kit/compiler/optimizations/LoadInLoop.java";
        Graph g = build(file);
        LoadStoreOptimization optimization = new LoadStoreOptimization();
        boolean changed = optimization.optimize(g, new OptimizationState());
    }

}
