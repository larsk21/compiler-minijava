package edu.kit.compiler.transform;

import edu.kit.compiler.data.ast_nodes.ProgramNode;
import edu.kit.compiler.lexer.StringTable;
import edu.kit.compiler.semantic.NamespaceMapper;
import firm.Dump;
import lombok.Data;

import java.io.IOException;

@Data
public class FirmGraphFactory {

    private final NamespaceMapper namespaceMapper;
    private StringTable stringTable;

    public FirmGraphFactory(NamespaceMapper namespaceMapper, StringTable stringTable) {
        this.namespaceMapper = namespaceMapper;
        this.stringTable = stringTable;
    }

    public void visitAST(ProgramNode ast) {
        TypeMapper entityMapper = new TypeMapper(namespaceMapper, stringTable);
        IRVisitor IRVisitor = new IRVisitor(entityMapper);
        // visit program node and iterate over methods to create stubs
        IRVisitor.visit(ast);
    }

    public static void dumpTypeGraph() throws IOException {
        Dump.dumpTypeGraph("type-system.vcg");
    }

}
