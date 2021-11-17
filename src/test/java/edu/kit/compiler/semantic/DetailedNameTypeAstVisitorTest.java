package edu.kit.compiler.semantic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import edu.kit.compiler.data.DataType;
import edu.kit.compiler.data.Literal;
import edu.kit.compiler.data.DataType.DataTypeClass;
import edu.kit.compiler.data.ast_nodes.ClassNode;
import edu.kit.compiler.data.ast_nodes.ClassNode.*;
import edu.kit.compiler.data.ast_nodes.ExpressionNode.*;
import edu.kit.compiler.data.ast_nodes.MethodNode.*;
import edu.kit.compiler.data.ast_nodes.StatementNode.*;
import edu.kit.compiler.lexer.StringTable;
import edu.kit.compiler.logger.Logger;
import edu.kit.compiler.semantic.NamespaceMapper.ClassNamespace;

public class DetailedNameTypeAstVisitorTest {

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

    private Logger logger = new Logger();

    @Test
    public void testAllUsedVariablesReferenceTheirDeclaration_Field() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        ErrorHandler errorHandler = new ErrorHandler(logger);
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
        ErrorHandler errorHandler = new ErrorHandler(logger);
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
        ErrorHandler errorHandler = new ErrorHandler(logger);
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
        ErrorHandler errorHandler = new ErrorHandler(logger);
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
    public void testAllUsedVariablesReferenceTheirDeclaration_LocalVariableDifferentScopes() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        ErrorHandler errorHandler = new ErrorHandler(logger);
        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable, errorHandler);

        LocalVariableDeclarationStatementNode definition;
        IdentifierExpressionNode usage;
        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(), Arrays.asList(
            new DynamicMethodNode(0, 0, new DataType(DataTypeClass.Void), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
                new BlockStatementNode(0, 0, Arrays.asList(
                    new LocalVariableDeclarationStatementNode(0, 0, new DataType(DataTypeClass.Int), stringTable.insert("varA"), Optional.empty(), false),
                    new BlockStatementNode(0, 0, Arrays.asList(
                        (definition = new LocalVariableDeclarationStatementNode(0, 0, new DataType(DataTypeClass.Int), stringTable.insert("varA"), Optional.empty(), false)),
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
    public void testAllUsedVariablesReferenceTheirDeclaration_NoDefinition() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        ErrorHandler errorHandler = new ErrorHandler(logger);
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

        assertTrue(errorHandler.isHasError());
        assertTrue(usage.isHasError());
    }

    @Test
    public void testNoVariableIsDeclaredTwiceInsideTheSameScope_SameScope() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        ErrorHandler errorHandler = new ErrorHandler(logger);
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

        assertTrue(errorHandler.isHasError());
        assertTrue(localVariable.isHasError());
    }

    @Test
    public void testNoVariableIsDeclaredTwiceInsideTheSameScope_DifferentScopes() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        ErrorHandler errorHandler = new ErrorHandler(logger);
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

        assertFalse(errorHandler.isHasError());
        assertFalse(localVariable.isHasError());
    }

    @Test
    public void testStaticMethodContainsNoReferenceToThis() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        ErrorHandler errorHandler = new ErrorHandler(logger);
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

        assertTrue(errorHandler.isHasError());
        assertTrue(_this.isHasError());
    }

    @Test
    public void testIntegerLiteralValueIsValid_SmallPositive() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        ErrorHandler errorHandler = new ErrorHandler(logger);
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

        assertFalse(errorHandler.isHasError());
        assertFalse(integerValue.isHasError());
    }

    @Test
    public void testIntegerLiteralValueIsValid_SmallNegative() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        ErrorHandler errorHandler = new ErrorHandler(logger);
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

        assertFalse(errorHandler.isHasError());
        assertFalse(integerValue.isHasError());
    }

    @Test
    public void testIntegerLiteralValueIsValid_LargePositive() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        ErrorHandler errorHandler = new ErrorHandler(logger);
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

        assertTrue(errorHandler.isHasError());
        assertTrue(integerValue.isHasError());
    }

    @Test
    public void testIntegerLiteralValueIsValid_LargeNegative() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        ErrorHandler errorHandler = new ErrorHandler(logger);
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

        assertTrue(errorHandler.isHasError());
        assertTrue(integerValue.isHasError());
    }

}
