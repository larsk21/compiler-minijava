package edu.kit.compiler.semantic;

import edu.kit.compiler.data.ast_nodes.ClassNode;
import edu.kit.compiler.data.ast_nodes.MethodNode;
import lombok.Data;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class NamespaceMapper {

    private final Map<Integer, ClassNamespace> namespaceMap = new LinkedHashMap<>();

    @Data
    public static class ClassNamespace {
        private final ClassNode classNodeRef;
        private final Map<Integer, MethodNode.DynamicMethodNode> dynamicMethods = new HashMap<>();
        private final Map<Integer, MethodNode.StaticMethodNode> staticMethods = new HashMap<>();
        private final Map<Integer, ClassNode.ClassNodeField> classSymbols = new LinkedHashMap<>();

        public ClassNamespace(ClassNode classNodeRef) {
            this.classNodeRef = classNodeRef;
        }
    }

    /**
     * Create a new symbol table once a new class is visited during recursive descent.
     *
     * @param classNode The classnode for which the new namespaces are created.
     * @return classNamespace The class namespace which contains a reference to the class node and methods/fields
     */
    public ClassNamespace insertClassNode(ClassNode classNode) {
        int classNodeName = classNode.getName();
        if (this.namespaceMap.containsKey(classNodeName)) {
            // this should never happend make sure to fix all issues where the same class would get added twice
            throw new SemanticException("Cannot insert same class twice into namespace map", classNode);
        }
        ClassNamespace classNamespace = new ClassNamespace(classNode);
        this.namespaceMap.put(classNodeName, classNamespace);

        return classNamespace;
    }

    /**
     * Get the namespace for this specific class node reference.
     * @param node the class node that the returned namespace belongs to.
     * @return the namespace that is assigned to this node
     */
    public ClassNamespace getClassNamespace(ClassNode node) {
        return this.namespaceMap.get(node.getName());
    }

    /**
     * Get the namespace for this specific class node reference as identifier
     * @param nodeId the node id
     * @return the namespace that is assigned to this node
     */
    public ClassNamespace getClassNamespace(int nodeId) {
        return this.namespaceMap.get(nodeId);
    }

    /**
     * Return if the namespace for this class node exists.
     * 
     * @param nodeId class node id
     */
    public boolean containsClassNamespace(int nodeId) {
        return this.namespaceMap.containsKey(nodeId);
    }

    /**
     * Get an immutable view of all namespaces in this mapper.
     * 
     * @return an unmodifiable map of all namespaces.
     */
    public Map<Integer, ClassNamespace> getNamespaceMap() {
        return Collections.unmodifiableMap(namespaceMap);
    }
}
