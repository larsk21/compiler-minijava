package edu.kit.compiler.semantic;

import edu.kit.compiler.data.DataType;
import edu.kit.compiler.data.DataType.DataTypeClass;
import edu.kit.compiler.data.Literal;
import edu.kit.compiler.data.Operator;
import edu.kit.compiler.data.ast_nodes.ClassNode;
import edu.kit.compiler.data.ast_nodes.ClassNode.ClassNodeField;
import edu.kit.compiler.data.ast_nodes.ExpressionNode.*;
import edu.kit.compiler.data.ast_nodes.MethodNode.DynamicMethodNode;
import edu.kit.compiler.data.ast_nodes.MethodNode.MethodNodeParameter;
import edu.kit.compiler.data.ast_nodes.MethodNode.StaticMethodNode;
import edu.kit.compiler.data.ast_nodes.StatementNode.*;
import edu.kit.compiler.lexer.StringTable;
import edu.kit.compiler.semantic.NamespaceMapper.ClassNamespace;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class DetailedNameTypeAstVisitorCorrectlyTypedTest {

    private void initializeNamespace(NamespaceMapper namespaceMapper, ClassNode classNode) {
        ClassNamespace namespace = namespaceMapper.insertClassNode(classNode);

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
    public void testField_PredefinedType() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable);

        ClassNodeField field;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(
            (field = new ClassNodeField(0, 0, new DataType(DataTypeClass.Int), stringTable.insert("fieldA"), false))
        ), Arrays.asList(), Arrays.asList(), false);
        assertFalse(field.isHasError());

        initializeNamespace(namespaceMapper, _class);

        assertDoesNotThrow(() -> _class.accept(visitor));
        assertFalse(field.isHasError());
    }

    @Test
    public void testField_KnownReferenceType() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable);

        ClassNodeField field;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(
            (field = new ClassNodeField(0, 0, new DataType(stringTable.insert("ClassA")), stringTable.insert("fieldA"), false))
        ), Arrays.asList(), Arrays.asList(), false);

        initializeNamespace(namespaceMapper, _class);

        assertDoesNotThrow(() -> _class.accept(visitor));
        assertFalse(field.isHasError());
    }

    @Test
    public void testField_UnknownReferenceType() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable);

        ClassNodeField field;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(
            (field = new ClassNodeField(0, 0, new DataType(stringTable.insert("ClassB")), stringTable.insert("fieldA"), false))
        ), Arrays.asList(), Arrays.asList(), false);

        initializeNamespace(namespaceMapper, _class);

        assertThrows(SemanticException.class, () -> _class.accept(visitor));
        assertTrue(field.isHasError());
    }

    @Test
    public void testField_Void() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable);

        ClassNodeField field;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(
            (field = new ClassNodeField(0, 0, new DataType(DataTypeClass.Void), stringTable.insert("fieldA"), false))
        ), Arrays.asList(), Arrays.asList(), false);

        initializeNamespace(namespaceMapper, _class);

        assertThrows(SemanticException.class, () -> _class.accept(visitor));
        assertTrue(field.isHasError());
    }

    @Test
    public void testMethod_Result_PredefinedType() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable);

        DynamicMethodNode method;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(), Arrays.asList(
            (method = new DynamicMethodNode(0, 0, new DataType(DataTypeClass.Int), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
                new BlockStatementNode(0, 0, Arrays.asList(), false),
            false))
        ), false);

        initializeNamespace(namespaceMapper, _class);

        assertDoesNotThrow(() -> _class.accept(visitor));
        assertFalse(method.isHasError());
    }

    @Test
    public void testMethod_Result_KnownReferenceType() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable);

        DynamicMethodNode method;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(), Arrays.asList(
            (method = new DynamicMethodNode(0, 0, new DataType(stringTable.insert("ClassA")), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
                new BlockStatementNode(0, 0, Arrays.asList(), false),
            false))
        ), false);

        initializeNamespace(namespaceMapper, _class);

        assertDoesNotThrow(() -> _class.accept(visitor));
        assertFalse(method.isHasError());
    }

    @Test
    public void testMethod_Result_UnknownReferenceType() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable);

        DynamicMethodNode method;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(), Arrays.asList(
            (method = new DynamicMethodNode(0, 0, new DataType(stringTable.insert("ClassB")), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
                new BlockStatementNode(0, 0, Arrays.asList(), false),
            false))
        ), false);

        initializeNamespace(namespaceMapper, _class);

        assertThrows(SemanticException.class, () -> _class.accept(visitor));
        assertTrue(method.isHasError());
    }

    @Test
    public void testMethod_Result_Void() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable);

        DynamicMethodNode method;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(), Arrays.asList(
            (method = new DynamicMethodNode(0, 0, new DataType(DataTypeClass.Void), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
                new BlockStatementNode(0, 0, Arrays.asList(), false),
            false))
        ), false);

        initializeNamespace(namespaceMapper, _class);

        assertDoesNotThrow(() -> _class.accept(visitor));
        assertFalse(method.isHasError());
    }

    @Test
    public void testMethod_Parameter_PredefinedType() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable);

        MethodNodeParameter parameter;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(), Arrays.asList(
            new DynamicMethodNode(0, 0, new DataType(DataTypeClass.Void), stringTable.insert("methodA"), Arrays.asList(
                (parameter = new MethodNodeParameter(0, 0, new DataType(DataTypeClass.Int), stringTable.insert("paramA"), false))
            ), Optional.empty(),
                new BlockStatementNode(0, 0, Arrays.asList(), false),
            false)
        ), false);

        initializeNamespace(namespaceMapper, _class);

        assertDoesNotThrow(() -> _class.accept(visitor));
        assertFalse(parameter.isHasError());
    }

    @Test
    public void testMethod_Parameter_KnownReferenceType() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable);

        MethodNodeParameter parameter;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(), Arrays.asList(
            new DynamicMethodNode(0, 0, new DataType(DataTypeClass.Void), stringTable.insert("methodA"), Arrays.asList(
                (parameter = new MethodNodeParameter(0, 0, new DataType(stringTable.insert("ClassA")), stringTable.insert("paramA"), false))
            ), Optional.empty(),
                new BlockStatementNode(0, 0, Arrays.asList(), false),
            false)
        ), false);

        initializeNamespace(namespaceMapper, _class);

        assertDoesNotThrow(() -> _class.accept(visitor));
        assertFalse(parameter.isHasError());
    }

    @Test
    public void testMethod_Parameter_UnknownReferenceType() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable);

        MethodNodeParameter parameter;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(), Arrays.asList(
            new DynamicMethodNode(0, 0, new DataType(DataTypeClass.Void), stringTable.insert("methodA"), Arrays.asList(
                (parameter = new MethodNodeParameter(0, 0, new DataType(stringTable.insert("ClassB")), stringTable.insert("paramA"), false))
            ), Optional.empty(),
                new BlockStatementNode(0, 0, Arrays.asList(), false),
            false)
        ), false);

        initializeNamespace(namespaceMapper, _class);

        assertThrows(SemanticException.class, () -> _class.accept(visitor));
        assertTrue(parameter.isHasError());
    }

    @Test
    public void testMethod_Parameter_Void() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable);

        MethodNodeParameter parameter;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(), Arrays.asList(
            new DynamicMethodNode(0, 0, new DataType(DataTypeClass.Void), stringTable.insert("methodA"), Arrays.asList(
                (parameter = new MethodNodeParameter(0, 0, new DataType(DataTypeClass.Void), stringTable.insert("paramA"), false))
            ), Optional.empty(),
                new BlockStatementNode(0, 0, Arrays.asList(), false),
            false)
        ), false);

        initializeNamespace(namespaceMapper, _class);

        assertThrows(SemanticException.class, () -> _class.accept(visitor));
        assertTrue(parameter.isHasError());
    }

    @Test
    public void testLocalVariableStatement_PredefinedType() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable);

        LocalVariableDeclarationStatementNode localVariable;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(), Arrays.asList(
            new DynamicMethodNode(0, 0, new DataType(DataTypeClass.Void), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
                new BlockStatementNode(0, 0, Arrays.asList(
                    (localVariable = new LocalVariableDeclarationStatementNode(0, 0, new DataType(DataTypeClass.Int), stringTable.insert("varA"), Optional.empty(), false))
                ), false),
            false)
        ), false);

        initializeNamespace(namespaceMapper, _class);

        assertDoesNotThrow(() -> _class.accept(visitor));
        assertFalse(localVariable.isHasError());
    }

    @Test
    public void testLocalVariableStatement_KnownReferenceType() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable);

        LocalVariableDeclarationStatementNode localVariable;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(), Arrays.asList(
            new DynamicMethodNode(0, 0, new DataType(DataTypeClass.Void), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
                new BlockStatementNode(0, 0, Arrays.asList(
                    (localVariable = new LocalVariableDeclarationStatementNode(0, 0, new DataType(stringTable.insert("ClassA")), stringTable.insert("varA"), Optional.empty(), false))
                ), false),
            false)
        ), false);

        initializeNamespace(namespaceMapper, _class);

        assertDoesNotThrow(() -> _class.accept(visitor));
        assertFalse(localVariable.isHasError());
    }

    @Test
    public void testLocalVariableStatement_UnknownReferenceType() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable);

        LocalVariableDeclarationStatementNode localVariable;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(), Arrays.asList(
            new DynamicMethodNode(0, 0, new DataType(DataTypeClass.Void), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
                new BlockStatementNode(0, 0, Arrays.asList(
                    (localVariable = new LocalVariableDeclarationStatementNode(0, 0, new DataType(stringTable.insert("ClassB")), stringTable.insert("varA"), Optional.empty(), false))
                ), false),
            false)
        ), false);

        initializeNamespace(namespaceMapper, _class);

        assertThrows(SemanticException.class, () -> _class.accept(visitor));
        assertTrue(localVariable.isHasError());
    }

    @Test
    public void testLocalVariableStatement_Void() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable);

        LocalVariableDeclarationStatementNode localVariable;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(), Arrays.asList(
            new DynamicMethodNode(0, 0, new DataType(DataTypeClass.Void), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
                new BlockStatementNode(0, 0, Arrays.asList(
                    (localVariable = new LocalVariableDeclarationStatementNode(0, 0, new DataType(DataTypeClass.Void), stringTable.insert("varA"), Optional.empty(), false))
                ), false),
            false)
        ), false);

        initializeNamespace(namespaceMapper, _class);

        assertThrows(SemanticException.class, () -> _class.accept(visitor));
        assertTrue(localVariable.isHasError());
    }

    @Test
    public void testLocalVariableStatement_ValidExpression() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable);

        LocalVariableDeclarationStatementNode localVariable;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(), Arrays.asList(
            new DynamicMethodNode(0, 0, new DataType(DataTypeClass.Void), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
                new BlockStatementNode(0, 0, Arrays.asList(
                    (localVariable = new LocalVariableDeclarationStatementNode(0, 0, new DataType(DataTypeClass.Int), stringTable.insert("varA"), Optional.of(
                        new ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, Literal.ofValue(17), false)
                    ), false))
                ), false),
            false)
        ), false);

        initializeNamespace(namespaceMapper, _class);

        assertDoesNotThrow(() -> _class.accept(visitor));
        assertFalse(localVariable.isHasError());
    }

    @Test
    public void testLocalVariableStatement_InvalidExpression() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable);

        LocalVariableDeclarationStatementNode localVariable;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(), Arrays.asList(
            new DynamicMethodNode(0, 0, new DataType(DataTypeClass.Void), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
                new BlockStatementNode(0, 0, Arrays.asList(
                    (localVariable = new LocalVariableDeclarationStatementNode(0, 0, new DataType(DataTypeClass.Int), stringTable.insert("varA"), Optional.of(
                        new ValueExpressionNode(0, 0, ValueExpressionType.False, false)
                    ), false))
                ), false),
            false)
        ), false);

        initializeNamespace(namespaceMapper, _class);

        assertThrows(SemanticException.class, () -> _class.accept(visitor));
        assertTrue(localVariable.isHasError());
    }

    @Test
    public void testIfStatementIsCorrectlyType_Boolean() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable);

        IfStatementNode ifStatement;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(), Arrays.asList(
            new DynamicMethodNode(0, 0, new DataType(DataTypeClass.Void), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
                new BlockStatementNode(0, 0, Arrays.asList(
                    (ifStatement = new IfStatementNode(0, 0,
                        new ValueExpressionNode(0, 0, ValueExpressionType.True, false),
                        new BlockStatementNode(0, 0, Arrays.asList(), false),
                    Optional.empty(), false))
                ), false),
            false)
        ), false);

        initializeNamespace(namespaceMapper, _class);

        assertDoesNotThrow(() -> _class.accept(visitor));
        assertFalse(ifStatement.isHasError());
    }

    @Test
    public void testIfStatementIsCorrectlyType_NonBoolean() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable);

        IfStatementNode ifStatement;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(), Arrays.asList(
            new DynamicMethodNode(0, 0, new DataType(DataTypeClass.Void), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
                new BlockStatementNode(0, 0, Arrays.asList(
                    (ifStatement = new IfStatementNode(0, 0,
                        new ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, Literal.ofValue(17), false),
                        new BlockStatementNode(0, 0, Arrays.asList(), false),
                    Optional.empty(), false))
                ), false),
            false)
        ), false);

        initializeNamespace(namespaceMapper, _class);

        assertThrows(SemanticException.class, () -> _class.accept(visitor));
        assertTrue(ifStatement.isHasError());
    }

    @Test
    public void testWhileStatementIsCorrectlyType_Boolean() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable);

        WhileStatementNode whileStatement;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(), Arrays.asList(
            new DynamicMethodNode(0, 0, new DataType(DataTypeClass.Void), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
                new BlockStatementNode(0, 0, Arrays.asList(
                    (whileStatement = new WhileStatementNode(0, 0,
                        new ValueExpressionNode(0, 0, ValueExpressionType.True, false),
                        new BlockStatementNode(0, 0, Arrays.asList(), false),
                    false))
                ), false),
            false)
        ), false);

        initializeNamespace(namespaceMapper, _class);

        assertDoesNotThrow(() -> _class.accept(visitor));
        assertFalse(whileStatement.isHasError());
    }

    @Test
    public void testWhileStatementIsCorrectlyType_NonBoolean() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable);

        WhileStatementNode whileStatement;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(), Arrays.asList(
            new DynamicMethodNode(0, 0, new DataType(DataTypeClass.Void), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
                new BlockStatementNode(0, 0, Arrays.asList(
                    (whileStatement = new WhileStatementNode(0, 0,
                        new ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, Literal.ofValue(17), false),
                        new BlockStatementNode(0, 0, Arrays.asList(), false),
                    false))
                ), false),
            false)
        ), false);

        initializeNamespace(namespaceMapper, _class);

        assertThrows(SemanticException.class, () -> _class.accept(visitor));
        assertTrue(whileStatement.isHasError());
    }

    @Test
    public void testReturnStatement_ExpectedVoid_Void() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable);

        ReturnStatementNode returnStatement;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(), Arrays.asList(
            new DynamicMethodNode(0, 0, new DataType(DataTypeClass.Void), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
                new BlockStatementNode(0, 0, Arrays.asList(
                    (returnStatement = new ReturnStatementNode(0, 0, Optional.empty(), false))
                ), false),
            false)
        ), false);

        initializeNamespace(namespaceMapper, _class);

        assertDoesNotThrow(() -> _class.accept(visitor));
        assertFalse(returnStatement.isHasError());
    }

    @Test
    public void testReturnStatement_ExpectedVoid_NonVoid() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable);

        ReturnStatementNode returnStatement;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(), Arrays.asList(
            new DynamicMethodNode(0, 0, new DataType(DataTypeClass.Void), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
                new BlockStatementNode(0, 0, Arrays.asList(
                    (returnStatement = new ReturnStatementNode(0, 0, Optional.of(
                        new ValueExpressionNode(0, 0, ValueExpressionType.True, false)
                    ), false))
                ), false),
            false)
        ), false);

        initializeNamespace(namespaceMapper, _class);

        assertThrows(SemanticException.class, () -> _class.accept(visitor));
        assertTrue(returnStatement.isHasError());
    }

    @Test
    public void testReturnStatement_ExpectedValue_Void() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable);

        ReturnStatementNode returnStatement;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(), Arrays.asList(
            new DynamicMethodNode(0, 0, new DataType(DataTypeClass.Boolean), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
                new BlockStatementNode(0, 0, Arrays.asList(
                    (returnStatement = new ReturnStatementNode(0, 0, Optional.empty(), false))
                ), false),
            false)
        ), false);

        initializeNamespace(namespaceMapper, _class);

        assertThrows(SemanticException.class, () -> _class.accept(visitor));
        assertTrue(returnStatement.isHasError());
    }

    @Test
    public void testReturnStatement_ExpectedValue_MatchingValue() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable);

        ReturnStatementNode returnStatement;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(), Arrays.asList(
            new DynamicMethodNode(0, 0, new DataType(DataTypeClass.Boolean), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
                new BlockStatementNode(0, 0, Arrays.asList(
                    (returnStatement = new ReturnStatementNode(0, 0, Optional.of(
                        new ValueExpressionNode(0, 0, ValueExpressionType.True, false)
                    ), false))
                ), false),
            false)
        ), false);

        initializeNamespace(namespaceMapper, _class);

        assertDoesNotThrow(() -> _class.accept(visitor));
        assertFalse(returnStatement.isHasError());
    }

    @Test
    public void testReturnStatement_ExpectedValue_NonMatchingValue() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable);

        ReturnStatementNode returnStatement;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(), Arrays.asList(
            new DynamicMethodNode(0, 0, new DataType(DataTypeClass.Boolean), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
                new BlockStatementNode(0, 0, Arrays.asList(
                    (returnStatement = new ReturnStatementNode(0, 0, Optional.of(
                        new ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, Literal.ofValue(17), false)
                    ), false))
                ), false),
            false)
        ), false);

        initializeNamespace(namespaceMapper, _class);

        assertThrows(SemanticException.class, () -> _class.accept(visitor));
        assertTrue(returnStatement.isHasError());
    }

    @Test
    public void testBinaryExpression_ArgumentsMatchOperator() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable);

        BinaryExpressionNode binaryExpression;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(), Arrays.asList(
            new DynamicMethodNode(0, 0, new DataType(DataTypeClass.Boolean), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
                new BlockStatementNode(0, 0, Arrays.asList(
                    new ExpressionStatementNode(0, 0,
                        (binaryExpression = new BinaryExpressionNode(0, 0, Operator.BinaryOperator.Addition,
                            new ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, Literal.ofValue(17), false),
                            new ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, Literal.ofValue(23), false),
                        false)),
                    false)
                ), false),
            false)
        ), false);

        initializeNamespace(namespaceMapper, _class);

        assertDoesNotThrow(() -> _class.accept(visitor));
        assertFalse(binaryExpression.isHasError());
    }

    @Test
    public void testBinaryExpression_LeftArgumentDoesNotMatchOperator() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable);

        BinaryExpressionNode binaryExpression;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(), Arrays.asList(
            new DynamicMethodNode(0, 0, new DataType(DataTypeClass.Void), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
                new BlockStatementNode(0, 0, Arrays.asList(
                    new ExpressionStatementNode(0, 0,
                        (binaryExpression = new BinaryExpressionNode(0, 0, Operator.BinaryOperator.Addition,
                            new ValueExpressionNode(0, 0, ValueExpressionType.False, false),
                            new ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, Literal.ofValue(23), false),
                        false)),
                    false)
                ), false),
            false)
        ), false);

        initializeNamespace(namespaceMapper, _class);

        assertThrows(SemanticException.class, () -> _class.accept(visitor));
        assertTrue(binaryExpression.isHasError());
    }

    @Test
    public void testBinaryExpression_RightArgumentDoesNotMatchOperator() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable);

        BinaryExpressionNode binaryExpression;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(), Arrays.asList(
            new DynamicMethodNode(0, 0, new DataType(DataTypeClass.Void), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
                new BlockStatementNode(0, 0, Arrays.asList(
                    new ExpressionStatementNode(0, 0,
                        (binaryExpression = new BinaryExpressionNode(0, 0, Operator.BinaryOperator.Addition,
                        new ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, Literal.ofValue(23), false),
                        new ValueExpressionNode(0, 0, ValueExpressionType.False, false),
                        false)),
                    false)
                ), false),
            false)
        ), false);

        initializeNamespace(namespaceMapper, _class);

        assertThrows(SemanticException.class, () -> _class.accept(visitor));
        assertTrue(binaryExpression.isHasError());
    }

    @Test
    public void testBinaryExpression_BothArgumentsDoNotMatchOperator() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable);

        BinaryExpressionNode binaryExpression;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(), Arrays.asList(
            new DynamicMethodNode(0, 0, new DataType(DataTypeClass.Void), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
                new BlockStatementNode(0, 0, Arrays.asList(
                    new ExpressionStatementNode(0, 0,
                        (binaryExpression = new BinaryExpressionNode(0, 0, Operator.BinaryOperator.Addition,
                        new ValueExpressionNode(0, 0, ValueExpressionType.False, false),
                        new ValueExpressionNode(0, 0, ValueExpressionType.False, false),
                        false)),
                    false)
                ), false),
            false)
        ), false);

        initializeNamespace(namespaceMapper, _class);

        assertThrows(SemanticException.class, () -> _class.accept(visitor));
        assertTrue(binaryExpression.isHasError());
    }

    @Test
    public void testBinaryExpression_VariableTypedArgsMatchOperator() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable);

        BinaryExpressionNode binaryExpression;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(), Arrays.asList(
            new DynamicMethodNode(0, 0, new DataType(DataTypeClass.Void), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
                new BlockStatementNode(0, 0, Arrays.asList(
                    new LocalVariableDeclarationStatementNode(0, 0, new DataType(stringTable.insert("ClassA")), stringTable.insert("varA"), Optional.empty(), false),
                    new ExpressionStatementNode(0, 0,
                        (binaryExpression = new BinaryExpressionNode(0, 0, Operator.BinaryOperator.Assignment,
                            new IdentifierExpressionNode(0, 0, stringTable.insert("varA"), false),
                            new IdentifierExpressionNode(0, 0, stringTable.insert("varA"), false),
                        false)),
                    false)
                ), false),
            false)
        ), false);

        initializeNamespace(namespaceMapper, _class);

        assertDoesNotThrow(() -> _class.accept(visitor));
        assertFalse(binaryExpression.isHasError());
    }

    @Test
    public void testBinaryExpression_VariableTypedArgsDoNotMatchOperator() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable);

        BinaryExpressionNode binaryExpression;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(), Arrays.asList(
            new DynamicMethodNode(0, 0, new DataType(DataTypeClass.Void), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
                new BlockStatementNode(0, 0, Arrays.asList(
                    new LocalVariableDeclarationStatementNode(0, 0, new DataType(stringTable.insert("ClassA")), stringTable.insert("varA"), Optional.empty(), false),
                    new ExpressionStatementNode(0, 0,
                        (binaryExpression = new BinaryExpressionNode(0, 0, Operator.BinaryOperator.Assignment,
                            new IdentifierExpressionNode(0, 0, stringTable.insert("varA"), false),
                            new ValueExpressionNode(0, 0, ValueExpressionType.False, false),
                        false)),
                    false)
                ), false),
            false)
        ), false);

        initializeNamespace(namespaceMapper, _class);

        assertThrows(SemanticException.class, () -> _class.accept(visitor));
        assertTrue(binaryExpression.isHasError());
    }

    @Test
    public void testBinaryExpression_VariableTypedArgsMatchOperatorWithAny() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable);

        BinaryExpressionNode binaryExpression;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(), Arrays.asList(
            new DynamicMethodNode(0, 0, new DataType(DataTypeClass.Void), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
                new BlockStatementNode(0, 0, Arrays.asList(
                    new LocalVariableDeclarationStatementNode(0, 0, new DataType(stringTable.insert("ClassA")), stringTable.insert("varA"), Optional.empty(), false),
                    new ExpressionStatementNode(0, 0,
                        (binaryExpression = new BinaryExpressionNode(0, 0, Operator.BinaryOperator.Assignment,
                            new IdentifierExpressionNode(0, 0, stringTable.insert("varA"), false),
                            new ValueExpressionNode(0, 0, ValueExpressionType.Null, false),
                        false)),
                    false)
                ), false),
            false)
        ), false);

        initializeNamespace(namespaceMapper, _class);

        assertDoesNotThrow(() -> _class.accept(visitor));
        assertFalse(binaryExpression.isHasError());
    }

    @Test
    public void testBinaryExpression_VariableTypedArgsMatchOperatorWithAnyAndPrimitiveType() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable);

        BinaryExpressionNode binaryExpression;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(), Arrays.asList(
            new DynamicMethodNode(0, 0, new DataType(DataTypeClass.Void), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
                new BlockStatementNode(0, 0, Arrays.asList(
                    new LocalVariableDeclarationStatementNode(0, 0, new DataType(DataTypeClass.Int), stringTable.insert("varA"), Optional.empty(), false),
                    new ExpressionStatementNode(0, 0,
                        (binaryExpression = new BinaryExpressionNode(0, 0, Operator.BinaryOperator.Assignment,
                            new IdentifierExpressionNode(0, 0, stringTable.insert("varA"), false),
                            new ValueExpressionNode(0, 0, ValueExpressionType.Null, false),
                        false)),
                    false)
                ), false),
            false)
        ), false);

        initializeNamespace(namespaceMapper, _class);

        assertThrows(SemanticException.class, () -> _class.accept(visitor));
        assertTrue(binaryExpression.isHasError());
    }

    @Test
    public void testUnaryExpression_ArgumentMatchesOperator() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable);

        UnaryExpressionNode unaryExpression;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(), Arrays.asList(
            new DynamicMethodNode(0, 0, new DataType(DataTypeClass.Void), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
                new BlockStatementNode(0, 0, Arrays.asList(
                    new ExpressionStatementNode(0, 0,
                        (unaryExpression = new UnaryExpressionNode(0, 0, Operator.UnaryOperator.ArithmeticNegation,
                            new ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, Literal.ofValue(17), false),
                        false)),
                    false)
                ), false),
            false)
        ), false);

        initializeNamespace(namespaceMapper, _class);

        assertDoesNotThrow(() -> _class.accept(visitor));
        assertFalse(unaryExpression.isHasError());
    }

    @Test
    public void testUnaryExpression_ArgumentDoesNotMatchOperator() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable);

        UnaryExpressionNode unaryExpression;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(), Arrays.asList(
            new DynamicMethodNode(0, 0, new DataType(DataTypeClass.Void), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
                new BlockStatementNode(0, 0, Arrays.asList(
                    new ExpressionStatementNode(0, 0,
                        (unaryExpression = new UnaryExpressionNode(0, 0, Operator.UnaryOperator.ArithmeticNegation,
                            new ValueExpressionNode(0, 0, ValueExpressionType.False, false),
                        false)),
                    false)
                ), false),
            false)
        ), false);

        initializeNamespace(namespaceMapper, _class);

        assertThrows(SemanticException.class, () -> _class.accept(visitor));
        assertTrue(unaryExpression.isHasError());
    }

    @Test
    public void testMethodInvocation_UnknownMethod() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable);

        MethodInvocationExpressionNode methodInvocation;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(), Arrays.asList(
            new DynamicMethodNode(0, 0, new DataType(DataTypeClass.Void), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
                new BlockStatementNode(0, 0, Arrays.asList(
                    new ExpressionStatementNode(0, 0,
                        (methodInvocation = new MethodInvocationExpressionNode(0, 0, Optional.empty(), stringTable.insert("methodB"), Arrays.asList(), false)),
                    false)
                ), false),
            false)
        ), false);

        initializeNamespace(namespaceMapper, _class);

        assertThrows(SemanticException.class, () -> _class.accept(visitor));
        assertTrue(methodInvocation.isHasError());
    }

    @Test
    public void testMethodInvocation_NoObject_StaticMethod() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable);

        MethodInvocationExpressionNode methodInvocation;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(
            new StaticMethodNode(0, 0, new DataType(DataTypeClass.Void), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
                new BlockStatementNode(0, 0, Arrays.asList(
                    new ExpressionStatementNode(0, 0,
                        (methodInvocation = new MethodInvocationExpressionNode(0, 0, Optional.empty(), stringTable.insert("methodA"), Arrays.asList(), false)),
                    false)
                ), false),
            false)
        ), Arrays.asList(), false);

        initializeNamespace(namespaceMapper, _class);

        assertThrows(SemanticException.class, () -> _class.accept(visitor));
        assertTrue(methodInvocation.isHasError());
    }

    @Test
    public void testMethodInvocation_NoObject_NoArgs() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable);

        MethodInvocationExpressionNode methodInvocation;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(), Arrays.asList(
            new DynamicMethodNode(0, 0, new DataType(DataTypeClass.Void), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
                new BlockStatementNode(0, 0, Arrays.asList(
                    new ExpressionStatementNode(0, 0,
                        (methodInvocation = new MethodInvocationExpressionNode(0, 0, Optional.empty(), stringTable.insert("methodA"), Arrays.asList(), false)),
                    false)
                ), false),
            false)
        ), false);

        initializeNamespace(namespaceMapper, _class);

        assertDoesNotThrow(() -> _class.accept(visitor));
        assertFalse(methodInvocation.isHasError());
    }

    @Test
    public void testMethodInvocation_NoObject_DifferentNumberOfArgs() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable);

        MethodInvocationExpressionNode methodInvocation;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(), Arrays.asList(
            new DynamicMethodNode(0, 0, new DataType(DataTypeClass.Void), stringTable.insert("methodA"), Arrays.asList(
                new MethodNodeParameter(0, 0, new DataType(DataTypeClass.Int), stringTable.insert("paramA"), false)
            ), Optional.empty(),
                new BlockStatementNode(0, 0, Arrays.asList(
                    new ExpressionStatementNode(0, 0,
                        (methodInvocation = new MethodInvocationExpressionNode(0, 0, Optional.empty(), stringTable.insert("methodA"), Arrays.asList(
                            new ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, Literal.ofValue(17), false),
                            new ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, Literal.ofValue(17), false)
                        ), false)),
                    false)
                ), false),
            false)
        ), false);

        initializeNamespace(namespaceMapper, _class);

        assertThrows(SemanticException.class, () -> _class.accept(visitor));
        assertTrue(methodInvocation.isHasError());
    }

    @Test
    public void testMethodInvocation_NoObject_WrongArgumentType() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable);

        MethodInvocationExpressionNode methodInvocation;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(), Arrays.asList(
            new DynamicMethodNode(0, 0, new DataType(DataTypeClass.Void), stringTable.insert("methodA"), Arrays.asList(
                new MethodNodeParameter(0, 0, new DataType(DataTypeClass.Int), stringTable.insert("paramA"), false),
                new MethodNodeParameter(0, 0, new DataType(DataTypeClass.Boolean), stringTable.insert("paramB"), false)
            ), Optional.empty(),
                new BlockStatementNode(0, 0, Arrays.asList(
                    new ExpressionStatementNode(0, 0,
                        (methodInvocation = new MethodInvocationExpressionNode(0, 0, Optional.empty(), stringTable.insert("methodA"), Arrays.asList(
                            new ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, Literal.ofValue(17), false),
                            new ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, Literal.ofValue(17), false)
                        ), false)),
                    false)
                ), false),
            false)
        ), false);

        initializeNamespace(namespaceMapper, _class);

        assertThrows(SemanticException.class, () -> _class.accept(visitor));
        assertTrue(methodInvocation.isHasError());
    }

    @Test
    public void testMethodInvocation_NoObject_CorrectArgs() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable);

        MethodInvocationExpressionNode methodInvocation;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(), Arrays.asList(
            new DynamicMethodNode(0, 0, new DataType(DataTypeClass.Void), stringTable.insert("methodA"), Arrays.asList(
                new MethodNodeParameter(0, 0, new DataType(DataTypeClass.Int), stringTable.insert("paramA"), false),
                new MethodNodeParameter(0, 0, new DataType(DataTypeClass.Int), stringTable.insert("paramB"), false)
            ), Optional.empty(),
                new BlockStatementNode(0, 0, Arrays.asList(
                    new ExpressionStatementNode(0, 0,
                        (methodInvocation = new MethodInvocationExpressionNode(0, 0, Optional.empty(), stringTable.insert("methodA"), Arrays.asList(
                            new ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, Literal.ofValue(17), false),
                            new ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, Literal.ofValue(17), false)
                        ), false)),
                    false)
                ), false),
            false)
        ), false);

        initializeNamespace(namespaceMapper, _class);

        assertDoesNotThrow(() -> _class.accept(visitor));
        assertFalse(methodInvocation.isHasError());
    }

    @Test
    public void testMethodInvocation_WithObject_PredefinedType() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable);

        MethodInvocationExpressionNode methodInvocation;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(), Arrays.asList(
            new DynamicMethodNode(0, 0, new DataType(DataTypeClass.Void), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
                new BlockStatementNode(0, 0, Arrays.asList(
                    new LocalVariableDeclarationStatementNode(0, 0, new DataType(DataTypeClass.Int), stringTable.insert("varA"), Optional.empty(), false),
                    new ExpressionStatementNode(0, 0,
                        (methodInvocation = new MethodInvocationExpressionNode(0, 0, Optional.of(
                            new IdentifierExpressionNode(0, 0, stringTable.insert("varA"), false)
                        ), stringTable.insert("methodB"), Arrays.asList(), false)),
                    false)
                ), false),
            false)
        ), false);

        initializeNamespace(namespaceMapper, _class);

        assertThrows(SemanticException.class, () -> _class.accept(visitor));
        assertTrue(methodInvocation.isHasError());
    }

    @Test
    public void testMethodInvocation_WithObject_UnknownMethod() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable);

        MethodInvocationExpressionNode methodInvocation;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(), Arrays.asList(
            new DynamicMethodNode(0, 0, new DataType(DataTypeClass.Void), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
                new BlockStatementNode(0, 0, Arrays.asList(
                    new LocalVariableDeclarationStatementNode(0, 0, new DataType(stringTable.insert("ClassA")), stringTable.insert("varA"), Optional.empty(), false),
                    new ExpressionStatementNode(0, 0,
                        (methodInvocation = new MethodInvocationExpressionNode(0, 0, Optional.of(
                            new IdentifierExpressionNode(0, 0, stringTable.insert("varA"), false)
                        ), stringTable.insert("methodB"), Arrays.asList(), false)),
                    false)
                ), false),
            false)
        ), false);

        initializeNamespace(namespaceMapper, _class);

        assertThrows(SemanticException.class, () -> _class.accept(visitor));
        assertTrue(methodInvocation.isHasError());
    }

    @Test
    public void testMethodInvocation_WithObject_KnownMethod() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable);

        MethodInvocationExpressionNode methodInvocation;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(), Arrays.asList(
            new DynamicMethodNode(0, 0, new DataType(DataTypeClass.Void), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
                new BlockStatementNode(0, 0, Arrays.asList(
                    new LocalVariableDeclarationStatementNode(0, 0, new DataType(stringTable.insert("ClassA")), stringTable.insert("varA"), Optional.empty(), false),
                    new ExpressionStatementNode(0, 0,
                        (methodInvocation = new MethodInvocationExpressionNode(0, 0, Optional.of(
                            new IdentifierExpressionNode(0, 0, stringTable.insert("varA"), false)
                        ), stringTable.insert("methodA"), Arrays.asList(), false)),
                    false)
                ), false),
            false)
        ), false);

        initializeNamespace(namespaceMapper, _class);

        assertDoesNotThrow(() -> _class.accept(visitor));
        assertFalse(methodInvocation.isHasError());
    }

    @Test
    public void testFieldAccess_PredefinedDataType() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable);

        FieldAccessExpressionNode fieldAccess;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(), Arrays.asList(
            new DynamicMethodNode(0, 0, new DataType(DataTypeClass.Void), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
                new BlockStatementNode(0, 0, Arrays.asList(
                    new ExpressionStatementNode(0, 0,
                        (fieldAccess = new FieldAccessExpressionNode(0, 0,
                            new ValueExpressionNode(0, 0, ValueExpressionType.False, false),
                        stringTable.insert("fieldA"), false)),
                    false)
                ), false),
            false)
        ), false);

        initializeNamespace(namespaceMapper, _class);

        assertThrows(SemanticException.class, () -> _class.accept(visitor));
        assertTrue(fieldAccess.isHasError());
    }

    @Test
    public void testFieldAccess_UnknownField() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable);

        FieldAccessExpressionNode fieldAccess;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(
            new ClassNode.ClassNodeField(0, 0, new DataType(DataTypeClass.Int), stringTable.insert("fieldA"), false)
        ), Arrays.asList(), Arrays.asList(
            new DynamicMethodNode(0, 0, new DataType(DataTypeClass.Void), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
                new BlockStatementNode(0, 0, Arrays.asList(
                    new LocalVariableDeclarationStatementNode(0, 0, new DataType(stringTable.insert("ClassA")), stringTable.insert("varA"), Optional.empty(), false),
                    new ExpressionStatementNode(0, 0,
                        (fieldAccess = new FieldAccessExpressionNode(0, 0,
                            new IdentifierExpressionNode(0, 0, stringTable.insert("varA"), false),
                        stringTable.insert("fieldB"), false)),
                    false)
                ), false),
            false)
        ), false);

        initializeNamespace(namespaceMapper, _class);

        assertThrows(SemanticException.class, () -> _class.accept(visitor));
        assertTrue(fieldAccess.isHasError());
    }

    @Test
    public void testFieldAccess_KnownField() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable);

        FieldAccessExpressionNode fieldAccess;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(
            new ClassNode.ClassNodeField(0, 0, new DataType(DataTypeClass.Int), stringTable.insert("fieldA"), false)
        ), Arrays.asList(), Arrays.asList(
            new DynamicMethodNode(0, 0, new DataType(DataTypeClass.Void), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
                new BlockStatementNode(0, 0, Arrays.asList(
                    new LocalVariableDeclarationStatementNode(0, 0, new DataType(stringTable.insert("ClassA")), stringTable.insert("varA"), Optional.empty(), false),
                    new ExpressionStatementNode(0, 0,
                        (fieldAccess = new FieldAccessExpressionNode(0, 0,
                            new IdentifierExpressionNode(0, 0, stringTable.insert("varA"), false),
                        stringTable.insert("fieldA"), false)),
                    false)
                ), false),
            false)
        ), false);

        initializeNamespace(namespaceMapper, _class);

        assertDoesNotThrow(() -> _class.accept(visitor));
        assertFalse(fieldAccess.isHasError());
    }

    @Test
    public void testArrayAccess_NoArrayType() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable);

        ArrayAccessExpressionNode arrayAccess;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(), Arrays.asList(
            new DynamicMethodNode(0, 0, new DataType(DataTypeClass.Void), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
                new BlockStatementNode(0, 0, Arrays.asList(
                    new LocalVariableDeclarationStatementNode(0, 0, new DataType(DataTypeClass.Int), stringTable.insert("varA"), Optional.empty(), false),
                    new ExpressionStatementNode(0, 0,
                        (arrayAccess = new ArrayAccessExpressionNode(0, 0,
                            new IdentifierExpressionNode(0, 0, stringTable.insert("varA"), false),
                            new ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, Literal.ofValue(17), false),
                        false)),
                    false)
                ), false),
            false)
        ), false);

        initializeNamespace(namespaceMapper, _class);

        assertThrows(SemanticException.class, () -> _class.accept(visitor));
        assertTrue(arrayAccess.isHasError());
    }

    @Test
    public void testArrayAccess_IndexIsNoInteger() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable);

        ArrayAccessExpressionNode arrayAccess;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(), Arrays.asList(
            new DynamicMethodNode(0, 0, new DataType(DataTypeClass.Void), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
                new BlockStatementNode(0, 0, Arrays.asList(
                    new LocalVariableDeclarationStatementNode(0, 0, new DataType(new DataType(DataTypeClass.Int)), stringTable.insert("varA"), Optional.empty(), false),
                    new ExpressionStatementNode(0, 0,
                        (arrayAccess = new ArrayAccessExpressionNode(0, 0,
                            new IdentifierExpressionNode(0, 0, stringTable.insert("varA"), false),
                            new ValueExpressionNode(0, 0, ValueExpressionType.False, false),
                        false)),
                    false)
                ), false),
            false)
        ), false);

        initializeNamespace(namespaceMapper, _class);

        assertThrows(SemanticException.class, () -> _class.accept(visitor));
        assertTrue(arrayAccess.isHasError());
    }

    @Test
    public void testArrayAccess_Correct() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable);

        ArrayAccessExpressionNode arrayAccess;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(), Arrays.asList(
            new DynamicMethodNode(0, 0, new DataType(DataTypeClass.Void), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
                new BlockStatementNode(0, 0, Arrays.asList(
                    new LocalVariableDeclarationStatementNode(0, 0, new DataType(new DataType(DataTypeClass.Int)), stringTable.insert("varA"), Optional.empty(), false),
                    new ExpressionStatementNode(0, 0,
                        (arrayAccess = new ArrayAccessExpressionNode(0, 0,
                            new IdentifierExpressionNode(0, 0, stringTable.insert("varA"), false),
                            new ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, Literal.ofValue(17), false),
                        false)),
                    false)
                ), false),
            false)
        ), false);

        initializeNamespace(namespaceMapper, _class);

        assertDoesNotThrow(() -> _class.accept(visitor));
        assertFalse(arrayAccess.isHasError());
    }

    @Test
    public void testNewObjectExpression_UnknownReferenceType() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable);

        NewObjectExpressionNode newObject;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(), Arrays.asList(
            new DynamicMethodNode(0, 0, new DataType(DataTypeClass.Void), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
                new BlockStatementNode(0, 0, Arrays.asList(
                    new ExpressionStatementNode(0, 0,
                        (newObject = new NewObjectExpressionNode(0, 0, stringTable.insert("ClassB"), false)),
                    false)
                ), false),
            false)
        ), false);

        initializeNamespace(namespaceMapper, _class);

        assertThrows(SemanticException.class, () -> _class.accept(visitor));
        assertTrue(newObject.isHasError());
    }

    @Test
    public void testNewObjectExpression_KnownReferenceType() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable);

        NewObjectExpressionNode newObject;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(), Arrays.asList(
            new DynamicMethodNode(0, 0, new DataType(DataTypeClass.Void), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
                new BlockStatementNode(0, 0, Arrays.asList(
                    new ExpressionStatementNode(0, 0,
                        (newObject = new NewObjectExpressionNode(0, 0, stringTable.insert("ClassA"), false)),
                    false)
                ), false),
            false)
        ), false);

        initializeNamespace(namespaceMapper, _class);

        assertDoesNotThrow(() -> _class.accept(visitor));
        assertFalse(newObject.isHasError());
    }

    @Test
    public void testNewArrayExpression_Void() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable);

        NewArrayExpressionNode newArray;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(), Arrays.asList(
            new DynamicMethodNode(0, 0, new DataType(DataTypeClass.Void), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
                new BlockStatementNode(0, 0, Arrays.asList(
                    new ExpressionStatementNode(0, 0,
                        (newArray = new NewArrayExpressionNode(0, 0, new DataType(DataTypeClass.Void),
                            new ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, Literal.ofValue(17), false),
                        1, false)),
                    false)
                ), false),
            false)
        ), false);

        initializeNamespace(namespaceMapper, _class);

        assertThrows(SemanticException.class, () -> _class.accept(visitor));
        assertTrue(newArray.isHasError());
    }

    @Test
    public void testNewArrayExpression_PredefinedDataType() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable);

        NewArrayExpressionNode newArray;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(), Arrays.asList(
            new DynamicMethodNode(0, 0, new DataType(DataTypeClass.Void), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
                new BlockStatementNode(0, 0, Arrays.asList(
                    new ExpressionStatementNode(0, 0,
                        (newArray = new NewArrayExpressionNode(0, 0, new DataType(DataTypeClass.Boolean),
                            new ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, Literal.ofValue(17), false),
                        1, false)),
                    false)
                ), false),
            false)
        ), false);

        initializeNamespace(namespaceMapper, _class);

        assertDoesNotThrow(() -> _class.accept(visitor));
        assertFalse(newArray.isHasError());
    }

    @Test
    public void testNewArrayExpression_UnknownReferenceType() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable);

        NewArrayExpressionNode newArray;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(), Arrays.asList(
            new DynamicMethodNode(0, 0, new DataType(DataTypeClass.Void), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
                new BlockStatementNode(0, 0, Arrays.asList(
                    new ExpressionStatementNode(0, 0,
                        (newArray = new NewArrayExpressionNode(0, 0, new DataType(stringTable.insert("ClassB")),
                            new ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, Literal.ofValue(17), false),
                        1, false)),
                    false)
                ), false),
            false)
        ), false);

        initializeNamespace(namespaceMapper, _class);

        assertThrows(SemanticException.class, () -> _class.accept(visitor));
        assertTrue(newArray.isHasError());
    }

    @Test
    public void testNewArrayExpression_KnownReferenceType() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable);

        NewArrayExpressionNode newArray;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(), Arrays.asList(
            new DynamicMethodNode(0, 0, new DataType(DataTypeClass.Void), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
                new BlockStatementNode(0, 0, Arrays.asList(
                    new ExpressionStatementNode(0, 0,
                        (newArray = new NewArrayExpressionNode(0, 0, new DataType(stringTable.insert("ClassA")),
                            new ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, Literal.ofValue(17), false),
                        1, false)),
                    false)
                ), false),
            false)
        ), false);

        initializeNamespace(namespaceMapper, _class);

        assertDoesNotThrow(() -> _class.accept(visitor));
        assertFalse(newArray.isHasError());
    }

    @Test
    public void testNewArrayExpression_LengthIsNoInteger() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable);

        NewArrayExpressionNode newArray;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(), Arrays.asList(
            new DynamicMethodNode(0, 0, new DataType(DataTypeClass.Void), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
                new BlockStatementNode(0, 0, Arrays.asList(
                    new ExpressionStatementNode(0, 0,
                        (newArray = new NewArrayExpressionNode(0, 0, new DataType(stringTable.insert("ClassA")),
                            new ValueExpressionNode(0, 0, ValueExpressionType.False, false),
                        1, false)),
                    false)
                ), false),
            false)
        ), false);

        initializeNamespace(namespaceMapper, _class);

        assertThrows(SemanticException.class, () -> _class.accept(visitor));
        assertTrue(newArray.isHasError());
    }

}
