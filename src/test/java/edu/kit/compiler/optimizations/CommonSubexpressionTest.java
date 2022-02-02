package edu.kit.compiler.optimizations;

import edu.kit.compiler.optimizations.common_subexpression.CommonSubexpressionElimination;
import firm.Dump;
import firm.Graph;
import firm.bindings.binding_ircons;
import firm.bindings.binding_irnode;
import firm.nodes.Div;
import firm.nodes.Node;
import firm.nodes.NodeVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CommonSubexpressionTest {

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

        Dump.dumpGraph(g, "before-opt");
        int nodeSizeBefore = getNodeSize(g);

        CommonSubexpressionElimination optimization = new CommonSubexpressionElimination();
        optimization.optimize(g, null);


        int nodeSizeAfter = getNodeSize(g);

        Dump.dumpGraph(g, "after-opt");

        assertContainsOpCode(g, binding_irnode.ir_opcode.iro_Minus);
        assertContainsOpCode(g, binding_irnode.ir_opcode.iro_Add);
        assertTrue(nodeSizeAfter < nodeSizeBefore);
    }

    @Test
    public void testNoMem() throws IOException {
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

        List<Node> nodes = getNodes(g);
        Node noMem = g.newNoMem();
        for (var n : nodes) {
            if (n.getNr() == 133 || n.getNr() == 158) {
                // found our div so we can replace it now
                Div nDiv = (Div) n;
                Node div = g.newDiv(n.getBlock(), noMem, nDiv.getLeft(), nDiv.getRight(), binding_ircons.op_pin_state.op_pin_state_pinned);
                Graph.exchange(n, div);
            }
        }

        int nodeSizeBefore = getNodeSize(g);

        CommonSubexpressionElimination optimization = new CommonSubexpressionElimination();
        try {
            while (optimization.optimize(g, null)) {
                //
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        int nodeSizeAfter = getNodeSize(g);

        assertContainsOpCode(g, binding_irnode.ir_opcode.iro_Add);
        assertTrue(nodeSizeAfter < nodeSizeBefore);
    }

}
