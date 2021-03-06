package edu.kit.compiler.semantic;

import edu.kit.compiler.data.DataType;
import edu.kit.compiler.data.DataType.DataTypeClass;
import edu.kit.compiler.data.Literal;
import edu.kit.compiler.data.Operator;
import edu.kit.compiler.data.ast_nodes.ClassNode;
import edu.kit.compiler.data.ast_nodes.ClassNode.*;
import edu.kit.compiler.data.ast_nodes.ExpressionNode.*;
import edu.kit.compiler.data.ast_nodes.MethodNode.*;
import edu.kit.compiler.data.ast_nodes.ProgramNode;
import edu.kit.compiler.data.ast_nodes.StatementNode.*;
import edu.kit.compiler.lexer.StringTable;
import edu.kit.compiler.logger.Logger;
import edu.kit.compiler.semantic.NamespaceMapper.ClassNamespace;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class DetailedNameTypeAstVisitorSystemTest {

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
    public void testSystemCall_Correct() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        ErrorHandler errorHandler = new ErrorHandler(Logger.nullLogger());

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
                                new ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, Literal.ofValue(17), false)
                            ), false)),
                        false)
                    ), false),
                false)
            ), false))
        ), false);

        initializeNamespace(namespaceMapper, _class);

        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable, errorHandler);
        program.accept(visitor);

        assertFalse(errorHandler.hasError());
        assertTrue(methodInvocation.getDefinition() instanceof StandardLibraryMethodNode);
    }

    @Test
    public void testSystemCall_CorrectStaticMethod() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        ErrorHandler errorHandler = new ErrorHandler(Logger.nullLogger());

        ClassNode _class;
        MethodInvocationExpressionNode methodInvocation;
        ProgramNode program = new ProgramNode(0, 0, Arrays.asList(
            (_class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(
                new StaticMethodNode(0, 0, new DataType(DataTypeClass.Void), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
                    new BlockStatementNode(0, 0, Arrays.asList(
                        new ExpressionStatementNode(0, 0,
                            (methodInvocation = new MethodInvocationExpressionNode(0, 0, Optional.of(
                                new FieldAccessExpressionNode(0, 0,
                                    new IdentifierExpressionNode(0, 0, stringTable.insert("System"), false),
                                stringTable.insert("out"), false)
                            ), stringTable.insert("println"), Arrays.asList(
                                new ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, Literal.ofValue(17), false)
                            ), false)),
                        false)
                    ), false),
                false)
            ), Arrays.asList(), false))
        ), false);

        initializeNamespace(namespaceMapper, _class);

        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable, errorHandler);
        program.accept(visitor);

        assertFalse(errorHandler.hasError());
        assertTrue(methodInvocation.getDefinition() instanceof StandardLibraryMethodNode);
    }

    @Test
    public void testSystemCall_WrongNumberOfArguments() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        ErrorHandler errorHandler = new ErrorHandler(Logger.nullLogger());

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
                                new ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, Literal.ofValue(17), false),
                                new ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, Literal.ofValue(3), false)
                            ), false)),
                        false)
                    ), false),
                false)
            ), false))
        ), false);

        initializeNamespace(namespaceMapper, _class);

        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable, errorHandler);
        program.accept(visitor);

        assertTrue(errorHandler.hasError());
        assertTrue(methodInvocation.isHasError());
    }

    @Test
    public void testSystemCall_WrongArgumentType() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        ErrorHandler errorHandler = new ErrorHandler(Logger.nullLogger());

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
                                new ValueExpressionNode(0, 0, ValueExpressionType.False, false)
                            ), false)),
                        false)
                    ), false),
                false)
            ), false))
        ), false);

        initializeNamespace(namespaceMapper, _class);

        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable, errorHandler);
        program.accept(visitor);

        assertTrue(errorHandler.hasError());
        assertTrue(methodInvocation.isHasError());
    }

    @Test
    public void testSystemCall_ArgumentTypeVoid() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        ErrorHandler errorHandler = new ErrorHandler(Logger.nullLogger());

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

        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable, errorHandler);
        program.accept(visitor);

        assertTrue(errorHandler.hasError());
        assertTrue(methodInvocation.isHasError());
    }

    @Test
    public void testSystemCall_ShadowedByType() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        ErrorHandler errorHandler = new ErrorHandler(Logger.nullLogger());

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

        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable, errorHandler);
        program.accept(visitor);

        assertTrue(errorHandler.hasError());
    }

    @Test
    public void testSystemCall_ShadowedByField() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        ErrorHandler errorHandler = new ErrorHandler(Logger.nullLogger());

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

        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable, errorHandler);
        program.accept(visitor);

        assertTrue(errorHandler.hasError());
    }

    @Test
    public void testSystemCall_ShadowedByFieldStaticMethod() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        ErrorHandler errorHandler = new ErrorHandler(Logger.nullLogger());

        ClassNode _class;
        ProgramNode program = new ProgramNode(0, 0, Arrays.asList(
            (_class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(
                new ClassNode.ClassNodeField(0, 0, new DataType(stringTable.insert("ClassA")), stringTable.insert("System"), false)
            ), Arrays.asList(
                new StaticMethodNode(0, 0, new DataType(DataTypeClass.Void), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
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
            ), Arrays.asList(), false))
        ), false);

        initializeNamespace(namespaceMapper, _class);

        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable, errorHandler);
        program.accept(visitor);

        assertTrue(errorHandler.hasError());
    }

    @Test
    public void testSystemCall_ShadowedByParameter() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        ErrorHandler errorHandler = new ErrorHandler(Logger.nullLogger());

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

        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable, errorHandler);
        program.accept(visitor);

        assertTrue(errorHandler.hasError());
    }

    @Test
    public void testSystemCall_ShadowedByLocalVariable() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        ErrorHandler errorHandler = new ErrorHandler(Logger.nullLogger());

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

        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable, errorHandler);
        program.accept(visitor);

        assertTrue(errorHandler.hasError());
    }

    @Test
    public void testSystemCall_ShadowedButCorrect() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        ErrorHandler errorHandler = new ErrorHandler(Logger.nullLogger());

        ClassNode _class;
        MethodInvocationExpressionNode methodInvocation;
        ProgramNode program = new ProgramNode(0, 0, Arrays.asList(
            (_class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(
                new ClassNodeField(0, 0, new DataType(stringTable.insert("ClassA")), stringTable.insert("System"), false),
                new ClassNodeField(0, 0, new DataType(stringTable.insert("ClassA")), stringTable.insert("out"), false)
            ), Arrays.asList(), Arrays.asList(
                new DynamicMethodNode(0, 0, new DataType(DataTypeClass.Void), stringTable.insert("println"), Arrays.asList(
                    new MethodNodeParameter(0, 0, new DataType(DataTypeClass.Int), stringTable.insert("value"), false)
                ), Optional.empty(),
                    new BlockStatementNode(0, 0, Arrays.asList(), false),
                false),
                new DynamicMethodNode(0, 0, new DataType(DataTypeClass.Void), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
                    new BlockStatementNode(0, 0, Arrays.asList(
                        new ExpressionStatementNode(0, 0,
                            (methodInvocation = new MethodInvocationExpressionNode(0, 0, Optional.of(
                                new FieldAccessExpressionNode(0, 0,
                                    new IdentifierExpressionNode(0, 0, stringTable.insert("System"), false),
                                stringTable.insert("out"), false)
                            ), stringTable.insert("println"), Arrays.asList(
                                new ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, Literal.ofValue(17), false)
                            ), false)),
                        false)
                    ), false),
                false)
            ), false))
        ), false);

        initializeNamespace(namespaceMapper, _class);

        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable, errorHandler);
        program.accept(visitor);

        assertFalse(errorHandler.hasError());
        assertFalse(methodInvocation.getDefinition() instanceof StandardLibraryMethodNode);
    }

    @Test
    public void testSystemIsNotAnObject() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        ErrorHandler errorHandler = new ErrorHandler(Logger.nullLogger());

        ClassNode _class;
        ProgramNode program = new ProgramNode(0, 0, Arrays.asList(
            (_class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(), Arrays.asList(
                new DynamicMethodNode(0, 0, new DataType(DataTypeClass.Void), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
                    new BlockStatementNode(0, 0, Arrays.asList(
                        new ExpressionStatementNode(0, 0,
                            new BinaryExpressionNode(0, 0, Operator.BinaryOperator.Equal,
                                new IdentifierExpressionNode(0, 0, stringTable.insert("System"), false),
                                new IdentifierExpressionNode(0, 0, stringTable.insert("System"), false),
                            false),
                        false)
                    ), false),
                false)
            ), false))
        ), false);

        initializeNamespace(namespaceMapper, _class);

        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable, errorHandler);
        program.accept(visitor);

        assertTrue(errorHandler.hasError());
    }

    @Test
    public void testSystemOutIsNotAnObject() {
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        StringTable stringTable = new StringTable();
        ErrorHandler errorHandler = new ErrorHandler(Logger.nullLogger());

        ClassNode _class;
        ProgramNode program = new ProgramNode(0, 0, Arrays.asList(
            (_class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(), Arrays.asList(
                new DynamicMethodNode(0, 0, new DataType(DataTypeClass.Void), stringTable.insert("methodA"), Arrays.asList(), Optional.empty(),
                    new BlockStatementNode(0, 0, Arrays.asList(
                        new ExpressionStatementNode(0, 0,
                            new BinaryExpressionNode(0, 0, Operator.BinaryOperator.Equal,
                                new FieldAccessExpressionNode(0, 0,
                                    new IdentifierExpressionNode(0, 0, stringTable.insert("System"), false),
                                stringTable.insert("out"), false),
                                new FieldAccessExpressionNode(0, 0,
                                    new IdentifierExpressionNode(0, 0, stringTable.insert("System"), false),
                                stringTable.insert("out"), false),
                            false),
                        false)
                    ), false),
                false)
            ), false))
        ), false);

        initializeNamespace(namespaceMapper, _class);

        DetailedNameTypeAstVisitor visitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable, errorHandler);
        program.accept(visitor);

        assertTrue(errorHandler.hasError());
    }

}
