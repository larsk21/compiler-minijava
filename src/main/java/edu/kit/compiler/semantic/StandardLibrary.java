package edu.kit.compiler.semantic;

import java.util.Arrays;

import edu.kit.compiler.data.DataType;
import edu.kit.compiler.data.DataType.DataTypeClass;
import edu.kit.compiler.data.ast_nodes.ClassNode;
import edu.kit.compiler.data.ast_nodes.MethodNode;
import edu.kit.compiler.data.ast_nodes.ClassNode.ClassNodeField;
import edu.kit.compiler.data.ast_nodes.MethodNode.DynamicMethodNode;
import edu.kit.compiler.data.ast_nodes.MethodNode.StandardLibraryMethod;
import edu.kit.compiler.data.ast_nodes.MethodNode.StandardLibraryMethodNode;
import edu.kit.compiler.data.ast_nodes.MethodNode.StaticMethodNode;
import edu.kit.compiler.lexer.StringTable;
import edu.kit.compiler.semantic.NamespaceMapper.ClassNamespace;
import lombok.Getter;

/**
 * Represents the standard library "System" of MiniJava. StandardLibrary
 * implements Definition to allow for a global (outside of any class) variable
 * called System with StandardLibrary as its definition.
 * 
 * The standard library provides:
 * - class @System
 *   - field @System_in in
 *   - field @System_out out
 * - class @System_in
 *   - method read
 * - class @System_out
 *   - method write
 *   - method println
 *   - method flush
 */
public class StandardLibrary implements Definition {

    private StandardLibrary(int name, DataType type) {
        this.name = name;
        this.type = type;
    }

    @Getter
    private int name;
    @Getter
    private DataType type;

    @Override
    public int getLine() {
        return -1;
    }

    @Override
    public int getColumn() {
        return -1;
    }

    @Override
    public DefinitionKind getKind() {
        return DefinitionKind.GlobalVariable;
    }

    /**
     * Initialize the standard library. If there is no other type called
     * "System", this method adds the class namespaces "@System", "@System_in"
     * and "@System_out". In this case it also opens a new scope with the
     * global variable "System" of type "@System".
     */
    public static void initialize(NamespaceMapper namespaceMapper, StringTable stringTable, SymbolTable symbolTable) {
        if (!namespaceMapper.containsClassNamespace(stringTable.insert("System"))) {
            initializeNamespace(namespaceMapper, StandardLibrary.getSystemClass(stringTable));
            initializeNamespace(namespaceMapper, StandardLibrary.getSystemInClass(stringTable));
            initializeNamespace(namespaceMapper, StandardLibrary.getSystemOutClass(stringTable));

            symbolTable.enterScope();
            symbolTable.insert(new StandardLibrary(stringTable.insert("System"), new DataType(stringTable.insert("@System"))));
        }
    }

    private static void initializeNamespace(NamespaceMapper namespaceMapper, ClassNode classNode) {
        ClassNamespace namespace = namespaceMapper.insertSymbolTable(classNode);

        for (ClassNodeField field : classNode.getFields()) {
            namespace.getClassSymbols().put(field.getName(), field);
        }
        for (DynamicMethodNode method : classNode.getDynamicMethods()) {
            namespace.getDynamicMethods().put(method.getName(), method);
        }
        for (StaticMethodNode method : classNode.getStaticMethods()) {
            namespace.getStaticMethods().put(method.getName(), method);
        }
    }

    private static ClassNode getSystemClass(StringTable stringTable) {
        return new ClassNode(-1, -1, stringTable.insert("@System"), Arrays.asList(
            new ClassNode.ClassNodeField(-1, -1, new DataType(stringTable.insert("@System_in")), stringTable.insert("in"), false),
            new ClassNode.ClassNodeField(-1, -1, new DataType(stringTable.insert("@System_out")), stringTable.insert("out"), false)
        ), Arrays.asList(), Arrays.asList(), false);
    }

    private static ClassNode getSystemInClass(StringTable stringTable) {
        return new ClassNode(-1, -1, stringTable.insert("@System_in"), Arrays.asList(), Arrays.asList(), Arrays.asList(
            new StandardLibraryMethodNode(
                new DataType(DataType.DataTypeClass.Int),
                stringTable.insert("read"),
                Arrays.asList(),
                StandardLibraryMethod.Read
            )
        ), false);
    }

    private static ClassNode getSystemOutClass(StringTable stringTable) {
        return new ClassNode(-1, -1, stringTable.insert("@System_out"), Arrays.asList(), Arrays.asList(), Arrays.asList(
            new StandardLibraryMethodNode(
                new DataType(DataType.DataTypeClass.Void),
                stringTable.insert("write"),
                Arrays.asList(
                    new MethodNode.MethodNodeParameter(-1, -1, new DataType(DataTypeClass.Int), stringTable.insert("object"), false)
                ),
                StandardLibraryMethod.Write
            ),
            new StandardLibraryMethodNode(
                new DataType(DataType.DataTypeClass.Void),
                stringTable.insert("println"),
                Arrays.asList(
                    new MethodNode.MethodNodeParameter(-1, -1, new DataType(DataTypeClass.Int), stringTable.insert("object"), false)
                ),
                StandardLibraryMethod.PrintLn
            ),
            new StandardLibraryMethodNode(
                new DataType(DataType.DataTypeClass.Void),
                stringTable.insert("flush"),
                Arrays.asList(),
                StandardLibraryMethod.Flush
            )
        ), false);
    }

}
