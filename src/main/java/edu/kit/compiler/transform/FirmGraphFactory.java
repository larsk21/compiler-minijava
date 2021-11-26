package edu.kit.compiler.transform;

import edu.kit.compiler.data.ast_nodes.ProgramNode;
import edu.kit.compiler.lexer.StringTable;
import edu.kit.compiler.semantic.NamespaceMapper;
import firm.Construction;
import firm.Dump;
import firm.Entity;
import firm.Graph;
import firm.nodes.Node;
import lombok.Data;

import java.io.IOException;
import java.util.UUID;

@Data
public class FirmGraphFactory {

    private final NamespaceMapper namespaceMapper;
    private StringTable stringTable;
    private Graph graph;

    public FirmGraphFactory(NamespaceMapper namespaceMapper, StringTable stringTable) {
        this.namespaceMapper = namespaceMapper;
        this.stringTable = stringTable;
    }

    public Graph createGraphFromAst(ProgramNode ast) {
        TypeMapper entityMapper = new TypeMapper(namespaceMapper, stringTable);

        Entity mainMethod = entityMapper.getMainMethod();
        // fill the graph with the main functions entity
        this.graph = new Graph(mainMethod, 50);
        Construction construction = new Construction(graph);

        TransformVisitor transformVisitor = new TransformVisitor(entityMapper);
        // visit program node and iterate over methods to create stubs
        transformVisitor.visit(ast);
        Node mainReturn = construction.newReturn(construction.getCurrentMem(), new Node[]{});

        graph.getEndBlock().addPred(mainReturn);
        construction.finish();
        return graph;
    }

    public static void dumpTypeGraph() throws IOException {
        Dump.dumpTypeGraph("type-system.vcg");
    }

    public static void dumpGraph(Graph g) {
        Dump.dumpGraph(g, UUID.randomUUID().toString());
    }
}
