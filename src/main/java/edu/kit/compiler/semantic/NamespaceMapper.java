package edu.kit.compiler.semantic;

import edu.kit.compiler.data.CompilerException;
import edu.kit.compiler.data.ast_nodes.ClassNode;
import edu.kit.compiler.data.ast_nodes.MethodNode;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

public class NamespaceMapper {

    private final Map<Integer, ClassNamespace> symbolTableMap = new HashMap<>();

    @Data
    public static class ClassNamespace {
        private final ClassNode classNodeRef;
        private final Map<Integer, MethodNode.DynamicMethodNode> dynamicMethods = new HashMap<>();
        private final Map<Integer, MethodNode.StaticMethodNode> staticMethods = new HashMap<>();
        private final Map<Integer, ClassNode.ClassNodeField> classSymbols = new HashMap<>();

        public ClassNamespace(ClassNode classNodeRef) {
            this.classNodeRef = classNodeRef;
        }
    }


    /**
     * Create a new symbol table once a new class is visited during recursive descent.
     * @param classNode The classnode for which the new namespaces are created.
     * @return classNamespace The class namespace which contains a reference to the class node and methods/fields
     */
    public ClassNamespace insertSymbolTable(ClassNode classNode) {
        int classNodeName = classNode.getName();
        if(this.symbolTableMap.containsKey(classNodeName)) {
            // this should never happend make sure to fix all issues where the same class would get added twice
            CompilerException.SourceLocation sl = new CompilerException.SourceLocation(classNode.getLine(), classNode.getColumn());
            throw new SemanticException("Cannot insert same class twice into namespace map", sl);
        }
        ClassNamespace classNamespace = new ClassNamespace(classNode);
        this.symbolTableMap.put(classNodeName, classNamespace);

        return classNamespace;
    }

    /**
     * Get the namespace for this specific class node reference.
     * @param node the class node that the returned namespace belongs to.
     * @return the namespace that is assigned to this node
     */
    public ClassNamespace getClassNamespace(ClassNode node) {
        return this.symbolTableMap.get(node.getName());
    }

    /**
     * Get the namespace for this specific class node reference as identifier
     * @param nodeId the node id
     * @return the namespace that is assigned to this node
     */
    public ClassNamespace getClassNamespace(int nodeId) {
        return this.symbolTableMap.get(nodeId);
    }
}
