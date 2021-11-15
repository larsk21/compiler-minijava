package edu.kit.compiler.semantic;

import edu.kit.compiler.data.CompilerException;
import edu.kit.compiler.data.ast_nodes.ClassNode;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

public class NamespaceMapper {

    private final Map<Integer, ClassNamespace> symbolTableMap = new HashMap<>();

    @AllArgsConstructor
    @Data
    public static class ClassNamespace {
        private final int classNodeRef;
        private SymbolTable methodSymbols;
        private SymbolTable fieldSymbols;
    }

    /**
     * Create a new symbol table once a new class is visited during recursive descent.
     * @param classNode The classnode for which the new namespaces are created.
     * @return classNamespace The class namespace which contains a reference to the class node and methods/fields
     */
    public ClassNamespace insertSymbolTable(ClassNode classNode) {
        int classNodeName = classNode.getName();
        if(symbolTableMap.containsKey(classNodeName)) {
            // this should never happend make sure to fix all issues where the same class would get added twice
            CompilerException.SourceLocation sl = new CompilerException.SourceLocation(classNode.getLine(), classNode.getColumn());
            throw new SemanticException("Cannot insert same class twice into namespace map", sl);
        }
        ClassNamespace classNamespace = new ClassNamespace(classNodeName, new SymbolTable(), new SymbolTable());
        symbolTableMap.put(classNodeName, classNamespace);

        return classNamespace;
    }

    /**
     * Get the namespace for this specific class node reference.
     * @param node the class node that the returned namespace belongs to.
     * @return the namespace that is assigned to this node
     */
    public ClassNamespace getClassNamespace(ClassNode node) {
        return symbolTableMap.get(node.getName());
    }

    /**
     * Get the namespace for this specific class node reference as identifier
     * @param nodeId the node id
     * @return the namespace that is assigned to this node
     */
    public ClassNamespace getClassNamespace(int nodeId) {
        return symbolTableMap.get(nodeId);
    }
}
