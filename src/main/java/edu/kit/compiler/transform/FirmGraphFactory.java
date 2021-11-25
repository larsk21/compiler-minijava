package edu.kit.compiler.transform;

import edu.kit.compiler.lexer.StringTable;
import edu.kit.compiler.semantic.NamespaceMapper;
import firm.Construction;
import firm.Dump;
import firm.Entity;
import firm.Graph;
import firm.nodes.Node;
import lombok.Data;

import java.io.IOException;

@Data
public class FirmGraphFactory {

    private final NamespaceMapper namespaceMapper;
    private final StringTable stringTable;
    private Graph graph;

    public FirmGraphFactory(NamespaceMapper namespaceMapper, StringTable stringTable) {
        this.namespaceMapper = namespaceMapper;
        this.stringTable = stringTable;
    }

    public Graph createGraphFromAst() {
        TypeMapper entityMapper = new TypeMapper(namespaceMapper, stringTable);

        Entity mainMethod = entityMapper.getMainMethod();
        // fill the graph with the main functions entity
        this.graph = new Graph(mainMethod, 50);
        Construction construction = new Construction(graph);

        Node mainReturn = construction.newReturn(construction.getCurrentMem(), new Node[]{});

        graph.getEndBlock().addPred(mainReturn);
        construction.finish();
        return graph;
    }

    public void dumpGraph() throws IOException {
        Dump.dumpTypeGraph("type-system.vcg");
    }
}
