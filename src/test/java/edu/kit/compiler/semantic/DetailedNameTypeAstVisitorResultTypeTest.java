package edu.kit.compiler.semantic;

import edu.kit.compiler.data.DataType;
import edu.kit.compiler.data.DataType.DataTypeClass;
import edu.kit.compiler.data.Literal;
import edu.kit.compiler.data.Operator;
import edu.kit.compiler.data.ast_nodes.ClassNode;
import edu.kit.compiler.data.ast_nodes.ClassNode.ClassNodeField;
import edu.kit.compiler.data.ast_nodes.ExpressionNode.*;
import edu.kit.compiler.data.ast_nodes.MethodNode.DynamicMethodNode;
import edu.kit.compiler.data.ast_nodes.MethodNode.StaticMethodNode;
import edu.kit.compiler.data.ast_nodes.StatementNode.BlockStatementNode;
import edu.kit.compiler.data.ast_nodes.StatementNode.ExpressionStatementNode;
import edu.kit.compiler.data.ast_nodes.StatementNode.LocalVariableDeclarationStatementNode;
import edu.kit.compiler.lexer.StringTable;
import edu.kit.compiler.semantic.NamespaceMapper.ClassNamespace;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class DetailedNameTypeAstVisitorResultTypeTest {

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
    public void testBinaryExpression_FixedType() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable);

        BinaryExpressionNode binaryExpression;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(), Arrays.asList(
            new DynamicMethodNode(0, 0, new DataType(DataTypeClass.Void), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
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

        this.initializeNamespace(namespaceMapper, _class);

        _class.accept(visitor);

        assertEquals(new DataType(DataTypeClass.Int), binaryExpression.getResultType());
        assertFalse(binaryExpression.isHasError());
    }

    @Test
    public void testBinaryExpression_VariableType() {
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

        this.initializeNamespace(namespaceMapper, _class);

        _class.accept(visitor);

        assertEquals(new DataType(stringTable.insert("ClassA")), binaryExpression.getResultType());
        assertFalse(binaryExpression.isHasError());
    }

    @Test
    public void testBinaryExpression_VariableTypeWithAny() {
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

        this.initializeNamespace(namespaceMapper, _class);

        _class.accept(visitor);

        assertEquals(new DataType(stringTable.insert("ClassA")), binaryExpression.getResultType());
        assertFalse(binaryExpression.isHasError());
    }

    @Test
    public void testUnaryExpression() {
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

        this.initializeNamespace(namespaceMapper, _class);

        _class.accept(visitor);

        assertEquals(new DataType(DataTypeClass.Int), unaryExpression.getResultType());
        assertFalse(unaryExpression.isHasError());
    }

    @Test
    public void testMethodInvocation_Void() {
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

        this.initializeNamespace(namespaceMapper, _class);

        _class.accept(visitor);

        assertEquals(new DataType(DataTypeClass.Void), methodInvocation.getResultType());
        assertFalse(methodInvocation.isHasError());
    }

    @Test
    public void testMethodInvocation() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable);

        MethodInvocationExpressionNode methodInvocation;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(), Arrays.asList(
            new DynamicMethodNode(0, 0, new DataType(DataTypeClass.Int), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
                new BlockStatementNode(0, 0, Arrays.asList(
                    new ExpressionStatementNode(0, 0,
                        (methodInvocation = new MethodInvocationExpressionNode(0, 0, Optional.empty(), stringTable.insert("methodA"), Arrays.asList(), false)),
                    false)
                ), false),
            false)
        ), false);

        this.initializeNamespace(namespaceMapper, _class);

        _class.accept(visitor);

        assertEquals(new DataType(DataTypeClass.Int), methodInvocation.getResultType());
        assertFalse(methodInvocation.isHasError());
    }

    @Test
    public void testFieldAccess() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable);

        FieldAccessExpressionNode fieldAccess;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(
            new ClassNodeField(0, 0, new DataType(DataTypeClass.Int), stringTable.insert("fieldA"), false)
        ), Arrays.asList(), Arrays.asList(
            new DynamicMethodNode(0, 0, new DataType(stringTable.insert("ClassA")), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
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

        this.initializeNamespace(namespaceMapper, _class);

        _class.accept(visitor);

        assertEquals(new DataType(DataTypeClass.Int), fieldAccess.getResultType());
        assertFalse(fieldAccess.isHasError());
    }

    @Test
    public void testArrayAccess_SingleDimension() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable);

        ArrayAccessExpressionNode arrayAccess;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(), Arrays.asList(
            new DynamicMethodNode(0, 0, new DataType(stringTable.insert("ClassA")), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
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

        this.initializeNamespace(namespaceMapper, _class);

        _class.accept(visitor);

        assertEquals(new DataType(DataTypeClass.Int), arrayAccess.getResultType());
        assertFalse(arrayAccess.isHasError());
    }

    @Test
    public void testArrayAccess_MultipleDimensions() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable);

        ArrayAccessExpressionNode arrayAccess;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(), Arrays.asList(
            new DynamicMethodNode(0, 0, new DataType(stringTable.insert("ClassA")), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
                new BlockStatementNode(0, 0, Arrays.asList(
                    new LocalVariableDeclarationStatementNode(0, 0, new DataType(new DataType(new DataType(DataTypeClass.Int))), stringTable.insert("varA"), Optional.empty(), false),
                    new ExpressionStatementNode(0, 0,
                        (arrayAccess = new ArrayAccessExpressionNode(0, 0,
                            new IdentifierExpressionNode(0, 0, stringTable.insert("varA"), false),
                            new ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, Literal.ofValue(17), false),
                        false)),
                    false)
                ), false),
            false)
        ), false);

        this.initializeNamespace(namespaceMapper, _class);

        _class.accept(visitor);

        assertEquals(new DataType(new DataType(DataTypeClass.Int)), arrayAccess.getResultType());
        assertFalse(arrayAccess.isHasError());
    }

    @Test
    public void testIdentifier() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable);

        IdentifierExpressionNode identifier;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(), Arrays.asList(
            new DynamicMethodNode(0, 0, new DataType(stringTable.insert("ClassA")), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
                new BlockStatementNode(0, 0, Arrays.asList(
                    new LocalVariableDeclarationStatementNode(0, 0, new DataType(DataTypeClass.Int), stringTable.insert("varA"), Optional.empty(), false),
                    new ExpressionStatementNode(0, 0,
                        (identifier = new IdentifierExpressionNode(0, 0, stringTable.insert("varA"), false)),
                    false)
                ), false),
            false)
        ), false);

        this.initializeNamespace(namespaceMapper, _class);

        _class.accept(visitor);

        assertEquals(new DataType(DataTypeClass.Int), identifier.getResultType());
        assertFalse(identifier.isHasError());
    }

    @Test
    public void testThis() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable);

        ThisExpressionNode _this;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(), Arrays.asList(
            new DynamicMethodNode(0, 0, new DataType(stringTable.insert("ClassA")), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
                new BlockStatementNode(0, 0, Arrays.asList(
                    new LocalVariableDeclarationStatementNode(0, 0, new DataType(DataTypeClass.Int), stringTable.insert("varA"), Optional.empty(), false),
                    new ExpressionStatementNode(0, 0,
                        (_this = new ThisExpressionNode(0, 0, false)),
                    false)
                ), false),
            false)
        ), false);

        this.initializeNamespace(namespaceMapper, _class);

        _class.accept(visitor);

        assertEquals(new DataType(stringTable.insert("ClassA")), _this.getResultType());
        assertFalse(_this.isHasError());
    }

    @Test
    public void testValue_False() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable);

        ValueExpressionNode value;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(), Arrays.asList(
            new DynamicMethodNode(0, 0, new DataType(stringTable.insert("ClassA")), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
                new BlockStatementNode(0, 0, Arrays.asList(
                    new ExpressionStatementNode(0, 0,
                        (value = new ValueExpressionNode(0, 0, ValueExpressionType.False, false)),
                    false)
                ), false),
            false)
        ), false);

        this.initializeNamespace(namespaceMapper, _class);

        _class.accept(visitor);

        assertEquals(new DataType(DataTypeClass.Boolean), value.getResultType());
        assertFalse(value.isHasError());
    }

    @Test
    public void testValue_True() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable);

        ValueExpressionNode value;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(), Arrays.asList(
            new DynamicMethodNode(0, 0, new DataType(stringTable.insert("ClassA")), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
                new BlockStatementNode(0, 0, Arrays.asList(
                    new ExpressionStatementNode(0, 0,
                        (value = new ValueExpressionNode(0, 0, ValueExpressionType.True, false)),
                    false)
                ), false),
            false)
        ), false);

        this.initializeNamespace(namespaceMapper, _class);

        _class.accept(visitor);

        assertEquals(new DataType(DataTypeClass.Boolean), value.getResultType());
        assertFalse(value.isHasError());
    }

    @Test
    public void testValue_Integer() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable);

        ValueExpressionNode value;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(), Arrays.asList(
            new DynamicMethodNode(0, 0, new DataType(stringTable.insert("ClassA")), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
                new BlockStatementNode(0, 0, Arrays.asList(
                    new ExpressionStatementNode(0, 0,
                        (value = new ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, Literal.ofValue(17), false)),
                    false)
                ), false),
            false)
        ), false);

        this.initializeNamespace(namespaceMapper, _class);

        _class.accept(visitor);

        assertEquals(new DataType(DataTypeClass.Int), value.getResultType());
        assertFalse(value.isHasError());
    }

    @Test
    public void testValue_Null() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable);

        ValueExpressionNode value;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(), Arrays.asList(
            new DynamicMethodNode(0, 0, new DataType(stringTable.insert("ClassA")), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
                new BlockStatementNode(0, 0, Arrays.asList(
                    new ExpressionStatementNode(0, 0,
                        (value = new ValueExpressionNode(0, 0, ValueExpressionType.Null, false)),
                    false)
                ), false),
            false)
        ), false);

        this.initializeNamespace(namespaceMapper, _class);

        _class.accept(visitor);

        assertEquals(new DataType(DataTypeClass.Any), value.getResultType());
        assertFalse(value.isHasError());
    }

    @Test
    public void testNewObject() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable);

        NewObjectExpressionNode newObject;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(), Arrays.asList(
            new DynamicMethodNode(0, 0, new DataType(stringTable.insert("ClassA")), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
                new BlockStatementNode(0, 0, Arrays.asList(
                    new ExpressionStatementNode(0, 0,
                        (newObject = new NewObjectExpressionNode(0, 0, stringTable.insert("ClassA"), false)),
                    false)
                ), false),
            false)
        ), false);

        this.initializeNamespace(namespaceMapper, _class);

        _class.accept(visitor);

        assertEquals(new DataType(stringTable.insert("ClassA")), newObject.getResultType());
        assertFalse(newObject.isHasError());
    }

    @Test
    public void testNewArray_SingleDimension() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable);

        NewArrayExpressionNode newArray;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(), Arrays.asList(
            new DynamicMethodNode(0, 0, new DataType(stringTable.insert("ClassA")), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
                new BlockStatementNode(0, 0, Arrays.asList(
                    new ExpressionStatementNode(0, 0,
                        (newArray = new NewArrayExpressionNode(0, 0, new DataType(DataTypeClass.Int),
                            new ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, Literal.ofValue(17), false),
                        1, false)),
                    false)
                ), false),
            false)
        ), false);

        this.initializeNamespace(namespaceMapper, _class);

        _class.accept(visitor);

        assertEquals(new DataType(new DataType(DataTypeClass.Int)), newArray.getResultType());
        assertFalse(newArray.isHasError());
    }

    @Test
    public void testNewArray_MultipleDimensions() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable);

        NewArrayExpressionNode newArray;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(), Arrays.asList(
            new DynamicMethodNode(0, 0, new DataType(stringTable.insert("ClassA")), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
                new BlockStatementNode(0, 0, Arrays.asList(
                    new ExpressionStatementNode(0, 0,
                        (newArray = new NewArrayExpressionNode(0, 0, new DataType(DataTypeClass.Int),
                            new ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, Literal.ofValue(17), false),
                        3, false)),
                    false)
                ), false),
            false)
        ), false);

        this.initializeNamespace(namespaceMapper, _class);

        _class.accept(visitor);

        assertEquals(new DataType(new DataType(new DataType(new DataType(DataTypeClass.Int)))), newArray.getResultType());
        assertFalse(newArray.isHasError());
    }

}
