package edu.kit.compiler.semantic;

import edu.kit.compiler.data.DataType;
import edu.kit.compiler.data.DataType.DataTypeClass;
import edu.kit.compiler.data.Literal;
import edu.kit.compiler.data.ast_nodes.ClassNode;
import edu.kit.compiler.data.ast_nodes.ClassNode.ClassNodeField;
import edu.kit.compiler.data.ast_nodes.ExpressionNode.IdentifierExpressionNode;
import edu.kit.compiler.data.ast_nodes.ExpressionNode.ThisExpressionNode;
import edu.kit.compiler.data.ast_nodes.ExpressionNode.ValueExpressionNode;
import edu.kit.compiler.data.ast_nodes.ExpressionNode.ValueExpressionType;
import edu.kit.compiler.data.ast_nodes.MethodNode.DynamicMethodNode;
import edu.kit.compiler.data.ast_nodes.MethodNode.MethodNodeParameter;
import edu.kit.compiler.data.ast_nodes.MethodNode.StaticMethodNode;
import edu.kit.compiler.data.ast_nodes.StatementNode.BlockStatementNode;
import edu.kit.compiler.data.ast_nodes.StatementNode.ExpressionStatementNode;
import edu.kit.compiler.data.ast_nodes.StatementNode.LocalVariableDeclarationStatementNode;
import edu.kit.compiler.lexer.StringTable;
import edu.kit.compiler.logger.Logger;
import edu.kit.compiler.semantic.NamespaceMapper.ClassNamespace;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class DetailedNameTypeAstVisitorTest {

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
    public void testAllUsedVariablesReferenceTheirDeclaration_Field() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        ErrorHandler errorHandler = new ErrorHandler(Logger.nullLogger());
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable, errorHandler);

        ClassNodeField definition;
        IdentifierExpressionNode usage;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(
            (definition = new ClassNodeField(0, 0, new DataType(DataTypeClass.Int), stringTable.insert("fieldA"), false))
        ), Arrays.asList(), Arrays.asList(
            new DynamicMethodNode(0, 0, new DataType(DataTypeClass.Void), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
                new BlockStatementNode(0, 0, Arrays.asList(
                    new ExpressionStatementNode(0, 0,
                        (usage = new IdentifierExpressionNode(0, 0, stringTable.insert("fieldA"), false)),
                    false)
                ), false),
            false)
        ), false);

        initializeNamespace(namespaceMapper, _class);

        _class.accept(visitor);

        assertEquals(definition, usage.getDefinition());
        assertFalse(usage.isHasError());
    }

    @Test
    public void testAllUsedVariablesReferenceTheirDeclaration_MethodParameter() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        ErrorHandler errorHandler = new ErrorHandler(Logger.nullLogger());
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable, errorHandler);

        MethodNodeParameter definition;
        IdentifierExpressionNode usage;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(), Arrays.asList(
            new DynamicMethodNode(0, 0, new DataType(DataTypeClass.Void), stringTable.insert("methodA"), Arrays.asList(
                (definition = new MethodNodeParameter(0, 0, new DataType(DataTypeClass.Int), stringTable.insert("paramA"), false))
            ), Optional.empty(),
                new BlockStatementNode(0, 0, Arrays.asList(
                    new ExpressionStatementNode(0, 0,
                        (usage = new IdentifierExpressionNode(0, 0, stringTable.insert("paramA"), false)),
                    false)
                ), false),
            false)
        ), false);

        initializeNamespace(namespaceMapper, _class);

        _class.accept(visitor);

        assertEquals(definition, usage.getDefinition());
        assertFalse(usage.isHasError());
    }

    @Test
    public void testAllUsedVariablesReferenceTheirDeclaration_LocalVariableOuterScope() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        ErrorHandler errorHandler = new ErrorHandler(Logger.nullLogger());
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable, errorHandler);

        LocalVariableDeclarationStatementNode definition;
        IdentifierExpressionNode usage;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(), Arrays.asList(
            new DynamicMethodNode(0, 0, new DataType(DataTypeClass.Void), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
                new BlockStatementNode(0, 0, Arrays.asList(
                    (definition = new LocalVariableDeclarationStatementNode(0, 0, new DataType(DataTypeClass.Int), stringTable.insert("varA"), Optional.empty(), false)),
                    new BlockStatementNode(0, 0, Arrays.asList(
                        new ExpressionStatementNode(0, 0,
                            (usage = new IdentifierExpressionNode(0, 0, stringTable.insert("varA"), false)),
                        false)
                    ), false)
                ), false),
            false)
        ), false);

        initializeNamespace(namespaceMapper, _class);

        _class.accept(visitor);

        assertEquals(definition, usage.getDefinition());
        assertFalse(usage.isHasError());
    }

    @Test
    public void testAllUsedVariablesReferenceTheirDeclaration_LocalVariableCurrentScope() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        ErrorHandler errorHandler = new ErrorHandler(Logger.nullLogger());
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable, errorHandler);

        LocalVariableDeclarationStatementNode definition;
        IdentifierExpressionNode usage;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(), Arrays.asList(
            new DynamicMethodNode(0, 0, new DataType(DataTypeClass.Void), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
                new BlockStatementNode(0, 0, Arrays.asList(
                    (definition = new LocalVariableDeclarationStatementNode(0, 0, new DataType(DataTypeClass.Int), stringTable.insert("varA"), Optional.empty(), false)),
                    new ExpressionStatementNode(0, 0,
                        (usage = new IdentifierExpressionNode(0, 0, stringTable.insert("varA"), false)),
                    false)
                ), false),
            false)
        ), false);

        initializeNamespace(namespaceMapper, _class);

        _class.accept(visitor);

        assertEquals(definition, usage.getDefinition());
        assertFalse(usage.isHasError());
    }

    @Test
    public void testAllUsedVariablesReferenceTheirDeclaration_FieldAndLocalVariable() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        ErrorHandler errorHandler = new ErrorHandler(Logger.nullLogger());
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable, errorHandler);

        LocalVariableDeclarationStatementNode definition;
        IdentifierExpressionNode usage;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(
            new ClassNodeField(0, 0, new DataType(DataTypeClass.Int), stringTable.insert("varA"), false)
        ), Arrays.asList(), Arrays.asList(
            new DynamicMethodNode(0, 0, new DataType(DataTypeClass.Void), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
                new BlockStatementNode(0, 0, Arrays.asList(
                    (definition = new LocalVariableDeclarationStatementNode(0, 0, new DataType(DataTypeClass.Int), stringTable.insert("varA"), Optional.empty(), false)),
                    new ExpressionStatementNode(0, 0,
                        (usage = new IdentifierExpressionNode(0, 0, stringTable.insert("varA"), false)),
                    false)
                ), false),
            false)
        ), false);

        initializeNamespace(namespaceMapper, _class);

        _class.accept(visitor);

        assertEquals(definition, usage.getDefinition());
        assertFalse(usage.isHasError());
    }

    @Test
    public void testAllUsedVariablesReferenceTheirDeclaration_NoDefinition() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        ErrorHandler errorHandler = new ErrorHandler(Logger.nullLogger());
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable, errorHandler);

        IdentifierExpressionNode usage;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(), Arrays.asList(
            new DynamicMethodNode(0, 0, new DataType(DataTypeClass.Void), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
                new BlockStatementNode(0, 0, Arrays.asList(
                    new ExpressionStatementNode(0, 0,
                        (usage = new IdentifierExpressionNode(0, 0, stringTable.insert("invalid"), false)),
                    false)
                ), false),
            false)
        ), false);

        initializeNamespace(namespaceMapper, _class);

        _class.accept(visitor);

        assertTrue(errorHandler.hasError());
        assertTrue(usage.isHasError());
    }

    @Test
    public void testAllUsedVariablesReferenceTheirDeclaration_LocalVariableInOwnExpression() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        ErrorHandler errorHandler = new ErrorHandler(Logger.nullLogger());
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable, errorHandler);

        LocalVariableDeclarationStatementNode definition;
        IdentifierExpressionNode usage;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(), Arrays.asList(
            new DynamicMethodNode(0, 0, new DataType(DataTypeClass.Void), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
                new BlockStatementNode(0, 0, Arrays.asList(
                    (definition = new LocalVariableDeclarationStatementNode(0, 0, new DataType(DataTypeClass.Int), stringTable.insert("varA"), Optional.of(
                        (usage = new IdentifierExpressionNode(0, 0, stringTable.insert("varA"), false))
                    ), false))
                ), false),
            false)
        ), false);

        initializeNamespace(namespaceMapper, _class);

        _class.accept(visitor);

        assertEquals(definition, usage.getDefinition());
        assertFalse(usage.isHasError());
    }

    @Test
    public void testNoVariableIsDeclaredTwiceInsideTheSameScope_SameScope() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        ErrorHandler errorHandler = new ErrorHandler(Logger.nullLogger());
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable, errorHandler);

        LocalVariableDeclarationStatementNode localVariable;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(), Arrays.asList(
            new DynamicMethodNode(0, 0, new DataType(DataTypeClass.Void), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
                new BlockStatementNode(0, 0, Arrays.asList(
                    new LocalVariableDeclarationStatementNode(0, 0, new DataType(DataTypeClass.Int), stringTable.insert("varA"), Optional.empty(), false),
                    (localVariable = new LocalVariableDeclarationStatementNode(0, 0, new DataType(DataTypeClass.Int), stringTable.insert("varA"), Optional.empty(), false))
                ), false),
            false)
        ), false);

        initializeNamespace(namespaceMapper, _class);

        _class.accept(visitor);

        assertTrue(errorHandler.hasError());
        assertTrue(localVariable.isHasError());
    }

    @Test
    public void testNoVariableIsDeclaredTwiceInsideTheSameScope_DifferentScopes() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        ErrorHandler errorHandler = new ErrorHandler(Logger.nullLogger());
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable, errorHandler);

        LocalVariableDeclarationStatementNode localVariable;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(), Arrays.asList(
            new DynamicMethodNode(0, 0, new DataType(DataTypeClass.Void), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
                new BlockStatementNode(0, 0, Arrays.asList(
                    new LocalVariableDeclarationStatementNode(0, 0, new DataType(DataTypeClass.Int), stringTable.insert("varA"), Optional.empty(), false),
                    new BlockStatementNode(0, 0, Arrays.asList(
                        (localVariable = new LocalVariableDeclarationStatementNode(0, 0, new DataType(DataTypeClass.Int), stringTable.insert("varA"), Optional.empty(), false))
                    ), false)
                ), false),
            false)
        ), false);

        initializeNamespace(namespaceMapper, _class);

        _class.accept(visitor);

        assertTrue(errorHandler.hasError());
        assertTrue(localVariable.isHasError());
    }

    @Test
    public void testNoVariableIsDeclaredTwiceInsideTheSameScope_Parameter() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        ErrorHandler errorHandler = new ErrorHandler(Logger.nullLogger());
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable, errorHandler);

        LocalVariableDeclarationStatementNode localVariable;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(), Arrays.asList(
            new DynamicMethodNode(0, 0, new DataType(DataTypeClass.Void), stringTable.insert("methodA"), Arrays.asList(
                new MethodNodeParameter(0, 0, new DataType(DataTypeClass.Int), stringTable.insert("varA"), false)
            ), Optional.empty(),
                new BlockStatementNode(0, 0, Arrays.asList(
                    (localVariable = new LocalVariableDeclarationStatementNode(0, 0, new DataType(DataTypeClass.Int), stringTable.insert("varA"), Optional.empty(), false))
                ), false),
            false)
        ), false);

        initializeNamespace(namespaceMapper, _class);

        _class.accept(visitor);

        assertTrue(errorHandler.hasError());
        assertTrue(localVariable.isHasError());
    }

    @Test
    public void testStaticMethodContainsNoReferenceToThis() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        ErrorHandler errorHandler = new ErrorHandler(Logger.nullLogger());
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable, errorHandler);

        ThisExpressionNode _this;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(
            new StaticMethodNode(0, 0, new DataType(DataTypeClass.Void), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
                new BlockStatementNode(0, 0, Arrays.asList(
                    new ExpressionStatementNode(0, 0,
                        (_this = new ThisExpressionNode(0, 0, false)),
                    false)
                ), false),
            false)
        ), Arrays.asList(), false);

        initializeNamespace(namespaceMapper, _class);

        _class.accept(visitor);

        assertTrue(errorHandler.hasError());
        assertTrue(_this.isHasError());
    }

    @Test
    public void testStaticMethodCannotReferenceFields() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        ErrorHandler errorHandler = new ErrorHandler(Logger.nullLogger());
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable, errorHandler);

        IdentifierExpressionNode identifier;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(
            new ClassNode.ClassNodeField(0, 0, new DataType(DataTypeClass.Int), stringTable.insert("fieldA"), false)
        ), Arrays.asList(
            new StaticMethodNode(0, 0, new DataType(DataTypeClass.Void), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
                new BlockStatementNode(0, 0, Arrays.asList(
                    new ExpressionStatementNode(0, 0,
                        (identifier = new IdentifierExpressionNode(0, 0, stringTable.insert("fieldA"), false)),
                    false)
                ), false),
            false)
        ), Arrays.asList(), false);

        initializeNamespace(namespaceMapper, _class);

        _class.accept(visitor);

        assertTrue(errorHandler.hasError());
        assertTrue(identifier.isHasError());
    }

    @Test
    public void testIntegerLiteralValueIsValid_SmallPositive() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        ErrorHandler errorHandler = new ErrorHandler(Logger.nullLogger());
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable, errorHandler);

        ValueExpressionNode integerValue;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(
            new StaticMethodNode(0, 0, new DataType(DataTypeClass.Void), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
                new BlockStatementNode(0, 0, Arrays.asList(
                    new ExpressionStatementNode(0, 0,
                        (integerValue = new ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, Literal.ofValue(17), false)),
                    false)
                ), false),
            false)
        ), Arrays.asList(), false);

        initializeNamespace(namespaceMapper, _class);

        _class.accept(visitor);

        assertFalse(errorHandler.hasError());
        assertFalse(integerValue.isHasError());
    }

    @Test
    public void testIntegerLiteralValueIsValid_SmallNegative() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        ErrorHandler errorHandler = new ErrorHandler(Logger.nullLogger());
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable, errorHandler);

        ValueExpressionNode integerValue;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(
            new StaticMethodNode(0, 0, new DataType(DataTypeClass.Void), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
                new BlockStatementNode(0, 0, Arrays.asList(
                    new ExpressionStatementNode(0, 0,
                        (integerValue = new ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, Literal.ofValue(-17), false)),
                    false)
                ), false),
            false)
        ), Arrays.asList(), false);

        initializeNamespace(namespaceMapper, _class);

        _class.accept(visitor);

        assertFalse(errorHandler.hasError());
        assertFalse(integerValue.isHasError());
    }

    @Test
    public void testIntegerLiteralValueIsValid_LargePositiveValid() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        ErrorHandler errorHandler = new ErrorHandler(Logger.nullLogger());
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable, errorHandler);

        ValueExpressionNode integerValue;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(
            new StaticMethodNode(0, 0, new DataType(DataTypeClass.Void), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
                new BlockStatementNode(0, 0, Arrays.asList(
                    new ExpressionStatementNode(0, 0,
                        (integerValue = new ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, new Literal("2147483647"), false)),
                    false)
                ), false),
            false)
        ), Arrays.asList(), false);

        initializeNamespace(namespaceMapper, _class);

        _class.accept(visitor);

        assertFalse(errorHandler.hasError());
        assertFalse(integerValue.isHasError());
    }

    @Test
    public void testIntegerLiteralValueIsValid_LargeNegativeValid() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        ErrorHandler errorHandler = new ErrorHandler(Logger.nullLogger());
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable, errorHandler);

        ValueExpressionNode integerValue;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(
            new StaticMethodNode(0, 0, new DataType(DataTypeClass.Void), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
                new BlockStatementNode(0, 0, Arrays.asList(
                    new ExpressionStatementNode(0, 0,
                        (integerValue = new ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, new Literal("-2147483648"), false)),
                    false)
                ), false),
            false)
        ), Arrays.asList(), false);

        initializeNamespace(namespaceMapper, _class);

        _class.accept(visitor);

        assertFalse(errorHandler.hasError());
        assertFalse(integerValue.isHasError());
    }

    @Test
    public void testIntegerLiteralValueIsValid_LargePositiveInvalid() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        ErrorHandler errorHandler = new ErrorHandler(Logger.nullLogger());
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable, errorHandler);

        ValueExpressionNode integerValue;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(
            new StaticMethodNode(0, 0, new DataType(DataTypeClass.Void), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
                new BlockStatementNode(0, 0, Arrays.asList(
                    new ExpressionStatementNode(0, 0,
                        (integerValue = new ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, new Literal("999999999999999"), false)),
                    false)
                ), false),
            false)
        ), Arrays.asList(), false);

        initializeNamespace(namespaceMapper, _class);

        _class.accept(visitor);

        assertTrue(errorHandler.hasError());
        assertTrue(integerValue.isHasError());
    }

    @Test
    public void testIntegerLiteralValueIsValid_LargeNegativeInvalid() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        ErrorHandler errorHandler = new ErrorHandler(Logger.nullLogger());
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable, errorHandler);

        ValueExpressionNode integerValue;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(
            new StaticMethodNode(0, 0, new DataType(DataTypeClass.Void), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
                new BlockStatementNode(0, 0, Arrays.asList(
                    new ExpressionStatementNode(0, 0,
                        (integerValue = new ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, new Literal("-999999999999999"), false)),
                    false)
                ), false),
            false)
        ), Arrays.asList(), false);

        initializeNamespace(namespaceMapper, _class);

        _class.accept(visitor);

        assertTrue(errorHandler.hasError());
        assertTrue(integerValue.isHasError());
    }

}
