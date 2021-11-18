package edu.kit.compiler.semantic;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import edu.kit.compiler.data.DataType;
import edu.kit.compiler.data.Literal;
import edu.kit.compiler.data.DataType.DataTypeClass;
import edu.kit.compiler.data.ast_nodes.ClassNode;
import edu.kit.compiler.data.ast_nodes.ProgramNode;
import edu.kit.compiler.data.ast_nodes.ClassNode.*;
import edu.kit.compiler.data.ast_nodes.ExpressionNode.*;
import edu.kit.compiler.data.ast_nodes.MethodNode.*;
import edu.kit.compiler.data.ast_nodes.StatementNode.*;
import edu.kit.compiler.lexer.StringTable;
import edu.kit.compiler.semantic.NamespaceMapper.ClassNamespace;

public class DetailedNameTypeAstVisitorSystem {

    private void initializeNamespace(NamespaceMapper namespaceMapper, ClassNode classNode) {
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

    @Test
    public void testSystemCall_Correct() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();

        ClassNode _class;
        ProgramNode program = new ProgramNode(0, 0, Arrays.asList(
            (_class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(), Arrays.asList(
                new DynamicMethodNode(0, 0, new DataType(DataTypeClass.Void), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
                    new BlockStatementNode(0, 0, Arrays.asList(
                        new ExpressionStatementNode(0, 0,
                            new MethodInvocationExpressionNode(0, 0, Optional.of(
                                new FieldAccessExpressionNode(0, 0,
                                    new IdentifierExpressionNode(0, 0, stringTable.insert("System"), false),
                                stringTable.insert("out"), false)
                            ), stringTable.insert("println"), Arrays.asList(
                                new ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, Literal.ofValue(17), false)
                            ), false),
                        false)
                    ), false),
                false)
            ), false))
        ), false);

        initializeNamespace(namespaceMapper, _class);

        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable);

        assertDoesNotThrow(() -> program.accept(visitor));
    }

    @Test
    public void testSystemCall_ShadowedByType() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();

        ClassNode _class;
        ProgramNode program = new ProgramNode(0, 0, Arrays.asList(
            (_class = new ClassNode(0, 0, stringTable.insert("System"), Arrays.asList(), Arrays.asList(), Arrays.asList(
                new DynamicMethodNode(0, 0, new DataType(DataTypeClass.Void), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
                    new BlockStatementNode(0, 0, Arrays.asList(
                        new ExpressionStatementNode(0, 0,
                            new MethodInvocationExpressionNode(0, 0, Optional.of(
                                new FieldAccessExpressionNode(0, 0,
                                    new IdentifierExpressionNode(0, 0, stringTable.insert("System"), false),
                                stringTable.insert("out"), false)
                            ), stringTable.insert("println"), Arrays.asList(
                                new ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, Literal.ofValue(17), false)
                            ), false),
                        false)
                    ), false),
                false)
            ), false))
        ), false);

        initializeNamespace(namespaceMapper, _class);

        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable);

        assertThrows(SemanticException.class, () -> program.accept(visitor));
    }

    @Test
    public void testSystemCall_ShadowedByField() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();

        ClassNode _class;
        ProgramNode program = new ProgramNode(0, 0, Arrays.asList(
            (_class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(
                new ClassNode.ClassNodeField(0, 0, new DataType(stringTable.insert("ClassA")), stringTable.insert("System"), false)
            ), Arrays.asList(), Arrays.asList(
                new DynamicMethodNode(0, 0, new DataType(DataTypeClass.Void), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
                    new BlockStatementNode(0, 0, Arrays.asList(
                        new ExpressionStatementNode(0, 0,
                            new MethodInvocationExpressionNode(0, 0, Optional.of(
                                new FieldAccessExpressionNode(0, 0,
                                    new IdentifierExpressionNode(0, 0, stringTable.insert("System"), false),
                                stringTable.insert("out"), false)
                            ), stringTable.insert("println"), Arrays.asList(
                                new ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, Literal.ofValue(17), false)
                            ), false),
                        false)
                    ), false),
                false)
            ), false))
        ), false);

        initializeNamespace(namespaceMapper, _class);

        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable);

        assertThrows(SemanticException.class, () -> program.accept(visitor));
    }

    @Test
    public void testSystemCall_ShadowedByParameter() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();

        ClassNode _class;
        ProgramNode program = new ProgramNode(0, 0, Arrays.asList(
            (_class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(), Arrays.asList(
                new DynamicMethodNode(0, 0, new DataType(DataTypeClass.Void), stringTable.insert("methodA"), Arrays.asList(
                    new MethodNodeParameter(0, 0, new DataType(stringTable.insert("ClassA")), stringTable.insert("System"), false)
                ), Optional.empty(),
                    new BlockStatementNode(0, 0, Arrays.asList(
                        new ExpressionStatementNode(0, 0,
                            new MethodInvocationExpressionNode(0, 0, Optional.of(
                                new FieldAccessExpressionNode(0, 0,
                                    new IdentifierExpressionNode(0, 0, stringTable.insert("System"), false),
                                stringTable.insert("out"), false)
                            ), stringTable.insert("println"), Arrays.asList(
                                new ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, Literal.ofValue(17), false)
                            ), false),
                        false)
                    ), false),
                false)
            ), false))
        ), false);

        initializeNamespace(namespaceMapper, _class);

        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable);

        assertThrows(SemanticException.class, () -> program.accept(visitor));
    }

    @Test
    public void testSystemCall_ShadowedByLocalVariable() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();

        ClassNode _class;
        ProgramNode program = new ProgramNode(0, 0, Arrays.asList(
            (_class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(), Arrays.asList(
                new DynamicMethodNode(0, 0, new DataType(DataTypeClass.Void), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
                    new BlockStatementNode(0, 0, Arrays.asList(
                        new LocalVariableDeclarationStatementNode(0, 0, new DataType(stringTable.insert("ClassA")), stringTable.insert("System"), Optional.empty(), false),
                        new ExpressionStatementNode(0, 0,
                            new MethodInvocationExpressionNode(0, 0, Optional.of(
                                new FieldAccessExpressionNode(0, 0,
                                    new IdentifierExpressionNode(0, 0, stringTable.insert("System"), false),
                                stringTable.insert("out"), false)
                            ), stringTable.insert("println"), Arrays.asList(
                                new ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, Literal.ofValue(17), false)
                            ), false),
                        false)
                    ), false),
                false)
            ), false))
        ), false);

        initializeNamespace(namespaceMapper, _class);

        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable);

        assertThrows(SemanticException.class, () -> program.accept(visitor));
    }

    @Test
    public void testSystemCall_Void() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();

        ClassNode _class;
        MethodInvocationExpressionNode methodInvocation;
        ProgramNode program = new ProgramNode(0, 0, Arrays.asList(
            (_class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(), Arrays.asList(
                new DynamicMethodNode(0, 0, new DataType(DataTypeClass.Void), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
                    new BlockStatementNode(0, 0, Arrays.asList(
                        new ExpressionStatementNode(0, 0,
                            (methodInvocation = new MethodInvocationExpressionNode(0, 0, Optional.of(
                                new FieldAccessExpressionNode(0, 0,
                                    new IdentifierExpressionNode(0, 0, stringTable.insert("System"), false),
                                stringTable.insert("out"), false)
                            ), stringTable.insert("println"), Arrays.asList(
                                new MethodInvocationExpressionNode(0, 0, Optional.empty(), stringTable.insert("methodA"), Arrays.asList(), false)
                            ), false)),
                        false)
                    ), false),
                false)
            ), false))
        ), false);

        initializeNamespace(namespaceMapper, _class);

        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable);

        assertThrows(SemanticException.class, () -> program.accept(visitor));
        assertTrue(methodInvocation.isHasError());
    }

}
