package edu.kit.compiler.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.kit.compiler.data.AstNode;
import edu.kit.compiler.data.DataType;
import edu.kit.compiler.data.Literal;
import edu.kit.compiler.data.Operator;
import edu.kit.compiler.data.DataType.DataTypeClass;
import edu.kit.compiler.data.ast_nodes.ClassNode;
import edu.kit.compiler.data.ast_nodes.ExpressionNode;
import edu.kit.compiler.data.ast_nodes.MethodNode;
import edu.kit.compiler.data.ast_nodes.ProgramNode;
import edu.kit.compiler.data.ast_nodes.StatementNode;
import edu.kit.compiler.lexer.StringTable;

public class PrettyPrintAstVisitorTest {

    @BeforeEach
    public void setup() {
        stream = new ByteArrayOutputStream();

        sysout = System.out;
        System.setOut(new PrintStream(stream));
    }

    @AfterEach
    public void tearDown() {
        System.setOut(sysout);
    }

    private PrintStream sysout;
    private ByteArrayOutputStream stream;

    @Test
    public void testEmptyProgram() {
        StringTable stringTable = new StringTable();
        PrettyPrintAstVisitor visitor = new PrettyPrintAstVisitor(stringTable);

        AstNode node = new ProgramNode(0, 0, Arrays.asList(), false);

        node.accept(visitor);
        String result = stream.toString();

        assertEquals("", result);
    }

    @Test
    public void emptyClasses() {
        StringTable stringTable = new StringTable();
        PrettyPrintAstVisitor visitor = new PrettyPrintAstVisitor(stringTable);

        int b = stringTable.insert("ClassB");
        int a = stringTable.insert("ClassA");

        AstNode node = new ProgramNode(0, 0, Arrays.asList(
            new ClassNode(0, 0, b, Arrays.asList(), Arrays.asList(), Arrays.asList(), false),
            new ClassNode(0, 0, a, Arrays.asList(), Arrays.asList(), Arrays.asList(), false)
        ), false);

        node.accept(visitor);
        String result = stream.toString();

        assertEquals(
            "class ClassA { }\n" +
            "class ClassB { }\n",
            result
        );
    }

    @Test
    public void testEmptyFieldsAndMethods() {
        StringTable stringTable = new StringTable();
        PrettyPrintAstVisitor visitor = new PrettyPrintAstVisitor(stringTable);

        int a = stringTable.insert("ClassA");
        int b = stringTable.insert("ClassB");
        int c = stringTable.insert("FieldC");
        int d = stringTable.insert("FieldD");
        int e = stringTable.insert("MethodE");
        int f = stringTable.insert("MethodF");
        int g = stringTable.insert("MethodG");
        int h = stringTable.insert("MethodH");

        AstNode node = new ClassNode(0, 0, a, Arrays.asList(
            new ClassNode.ClassNodeField(new DataType(DataTypeClass.Int), d),
            new ClassNode.ClassNodeField(new DataType(new DataType(DataTypeClass.Int)), c)
        ), Arrays.asList(
            new MethodNode.StaticMethodNode(0, 0, new DataType(DataTypeClass.Void), h, Arrays.asList(), new MethodNode.MethodNodeRest(Optional.empty()), Arrays.asList(), false),
            new MethodNode.StaticMethodNode(0, 0, new DataType(DataTypeClass.Boolean), f, Arrays.asList(), new MethodNode.MethodNodeRest(Optional.empty()), Arrays.asList(), false)
        ), Arrays.asList(
            new MethodNode.DynamicMethodNode(0, 0, new DataType(DataTypeClass.Void), e, Arrays.asList(), new MethodNode.MethodNodeRest(Optional.empty()), Arrays.asList(), false),
            new MethodNode.DynamicMethodNode(0, 0, new DataType(b), g, Arrays.asList(), new MethodNode.MethodNodeRest(Optional.empty()), Arrays.asList(), false)
        ), false);

        node.accept(visitor);
        String result = stream.toString();

        assertEquals(
            "class ClassA {\n" +
            "\tpublic void MethodE() { }\n" +
            "\tpublic static boolean MethodF() { }\n" +
            "\tpublic ClassB MethodG() { }\n" +
            "\tpublic static void MethodH() { }\n" +
            "\tpublic int[] FieldC;\n" +
            "\tpublic int FieldD;\n" +
            "}",
            result
        );
    }

    @Test
    public void testEmptyMethod() {
        StringTable stringTable = new StringTable();
        PrettyPrintAstVisitor visitor = new PrettyPrintAstVisitor(stringTable);

        int a = stringTable.insert("MethodA");
        int b = stringTable.insert("ParamB");
        int c = stringTable.insert("ParamC");
        int d = stringTable.insert("ClassD");
        int e = stringTable.insert("ExceptionE");

        AstNode node = new MethodNode.DynamicMethodNode(0, 0, new DataType(DataTypeClass.Void), a, Arrays.asList(
            new MethodNode.MethodNodeParameter(new DataType(DataTypeClass.Int), c),
            new MethodNode.MethodNodeParameter(new DataType(new DataType(d)), b)
        ), new MethodNode.MethodNodeRest(Optional.of(e)), Arrays.asList(), false);

        node.accept(visitor);
        String result = stream.toString();

        assertEquals(
            "public void MethodA(int ParamC, ClassD[] ParamB) throws ExceptionE { }",
            result
        );
    }

    @Test
    public void testMethodWithSimpleStatements() {
        StringTable stringTable = new StringTable();
        PrettyPrintAstVisitor visitor = new PrettyPrintAstVisitor(stringTable);

        int a = stringTable.insert("MethodA");

        AstNode node = new MethodNode.DynamicMethodNode(0, 0, new DataType(DataTypeClass.Void), a, Arrays.asList(), new MethodNode.MethodNodeRest(Optional.empty()), Arrays.asList(
            new StatementNode.ExpressionStatementNode(0, 0,
                new ExpressionNode.ValueExpressionNode(0, 0, ExpressionNode.ValueExpressionType.True, false),
            false),
            new StatementNode.ReturnStatementNode(0, 0,
                Optional.of(new ExpressionNode.ValueExpressionNode(0, 0, ExpressionNode.ValueExpressionType.This, false)),
            false)
        ), false);

        node.accept(visitor);
        String result = stream.toString();

        assertEquals(
            "public void MethodA() {\n" +
            "\ttrue;\n" +
            "\treturn this;\n" +
            "}",
            result
        );
    }

    @Test
    public void testEmptyBlockStatement() {
        StringTable stringTable = new StringTable();
        PrettyPrintAstVisitor visitor = new PrettyPrintAstVisitor(stringTable);

        AstNode node = new StatementNode.BlockStatementNode(0, 0, Arrays.asList(), false);

        node.accept(visitor);
        String result = stream.toString();

        assertEquals(
            "{ }",
            result
        );
    }

    @Test
    public void testBlockStatement() {
        StringTable stringTable = new StringTable();
        PrettyPrintAstVisitor visitor = new PrettyPrintAstVisitor(stringTable);

        AstNode node = new StatementNode.BlockStatementNode(0, 0, Arrays.asList(
            new StatementNode.ExpressionStatementNode(0, 0,
                new ExpressionNode.ValueExpressionNode(0, 0, ExpressionNode.ValueExpressionType.True, false),
            false),
            new StatementNode.ReturnStatementNode(0, 0, Optional.empty(), false)
        ), false);

        node.accept(visitor);
        String result = stream.toString();

        assertEquals(
            "{\n" +
            "\ttrue;\n" +
            "\treturn;\n" +
            "}",
            result
        );
    }

    @Test
    public void testEmptyLocalVariableStatement() {
        StringTable stringTable = new StringTable();
        PrettyPrintAstVisitor visitor = new PrettyPrintAstVisitor(stringTable);

        int a = stringTable.insert("a");
        int b = stringTable.insert("ClassB");

        AstNode node = new StatementNode.LocalVariableDeclarationStatementNode(0, 0, new DataType(b), a, Optional.empty(), false);

        node.accept(visitor);
        String result = stream.toString();

        assertEquals(
            "ClassB a;",
            result
        );
    }

    @Test
    public void testLocalVariableStatement() {
        StringTable stringTable = new StringTable();
        PrettyPrintAstVisitor visitor = new PrettyPrintAstVisitor(stringTable);

        int a = stringTable.insert("a");
        int b = stringTable.insert("ClassB");

        AstNode node = new StatementNode.LocalVariableDeclarationStatementNode(0, 0, new DataType(b), a,
            Optional.of(new ExpressionNode.ValueExpressionNode(0, 0, ExpressionNode.ValueExpressionType.IntegerLiteral, Literal.ofValue(17), false)),
        false);

        node.accept(visitor);
        String result = stream.toString();

        assertEquals(
            "ClassB a = 17;",
            result
        );
    }

    @Test
    public void testEmptyIfStatement() {
        StringTable stringTable = new StringTable();
        PrettyPrintAstVisitor visitor = new PrettyPrintAstVisitor(stringTable);

        AstNode node = new StatementNode.IfStatementNode(0, 0,
            new ExpressionNode.ValueExpressionNode(0, 0, ExpressionNode.ValueExpressionType.True, false),
            new StatementNode.BlockStatementNode(0, 0, Arrays.asList(), false),
        Optional.empty(), false);

        node.accept(visitor);
        String result = stream.toString();

        assertEquals(
            "if (true) { }",
            result
        );
    }

    @Test
    public void testSingleIfStatement() {
        StringTable stringTable = new StringTable();
        PrettyPrintAstVisitor visitor = new PrettyPrintAstVisitor(stringTable);

        int a = stringTable.insert("a");

        AstNode node = new StatementNode.IfStatementNode(0, 0,
            new ExpressionNode.BinaryExpressionNode(0, 0, Operator.BinaryOperator.LessThan,
                new ExpressionNode.ValueExpressionNode(0, 0, ExpressionNode.ValueExpressionType.IntegerLiteral, Literal.ofValue(17), false),
                new ExpressionNode.ValueExpressionNode(0, 0, ExpressionNode.ValueExpressionType.IntegerLiteral, Literal.ofValue(42), false),
            false),
            new StatementNode.LocalVariableDeclarationStatementNode(0, 0, new DataType(DataTypeClass.Int), a, Optional.empty(), false),
        Optional.empty(), false);

        node.accept(visitor);
        String result = stream.toString();

        assertEquals(
            "if (17 < 42)\n" +
            "\tint a;",
            result
        );
    }

    @Test
    public void testMultiIfStatement() {
        StringTable stringTable = new StringTable();
        PrettyPrintAstVisitor visitor = new PrettyPrintAstVisitor(stringTable);

        int a = stringTable.insert("a");

        AstNode node = new StatementNode.IfStatementNode(0, 0,
            new ExpressionNode.BinaryExpressionNode(0, 0, Operator.BinaryOperator.LessThan,
                new ExpressionNode.ValueExpressionNode(0, 0, ExpressionNode.ValueExpressionType.IntegerLiteral, Literal.ofValue(17), false),
                new ExpressionNode.ValueExpressionNode(0, 0, ExpressionNode.ValueExpressionType.IntegerLiteral, Literal.ofValue(42), false),
            false),
            new StatementNode.BlockStatementNode(0, 0, Arrays.asList(
                new StatementNode.LocalVariableDeclarationStatementNode(0, 0, new DataType(DataTypeClass.Int), a, Optional.empty(), false),
                new StatementNode.ReturnStatementNode(0, 0, Optional.empty(), false)
            ), false),
        Optional.empty(), false);

        node.accept(visitor);
        String result = stream.toString();

        assertEquals(
            "if (17 < 42) {\n" +
            "\tint a;\n" +
            "\treturn;\n" +
            "}",
            result
        );
    }

    @Test
    public void testEmptyEmptyIfElseStatement() {
        StringTable stringTable = new StringTable();
        PrettyPrintAstVisitor visitor = new PrettyPrintAstVisitor(stringTable);

        AstNode node = new StatementNode.IfStatementNode(0, 0,
            new ExpressionNode.ValueExpressionNode(0, 0, ExpressionNode.ValueExpressionType.False, false),
            new StatementNode.BlockStatementNode(0, 0, Arrays.asList(), false),
        Optional.of(
            new StatementNode.BlockStatementNode(0, 0, Arrays.asList(), false)
        ), false);

        node.accept(visitor);
        String result = stream.toString();

        assertEquals(
            "if (false) { }\n" +
            "else { }",
            result
        );
    }

    @Test
    public void testEmptySingleIfElseStatement() {
        StringTable stringTable = new StringTable();
        PrettyPrintAstVisitor visitor = new PrettyPrintAstVisitor(stringTable);

        AstNode node = new StatementNode.IfStatementNode(0, 0,
            new ExpressionNode.ValueExpressionNode(0, 0, ExpressionNode.ValueExpressionType.False, false),
            new StatementNode.BlockStatementNode(0, 0, Arrays.asList(), false),
        Optional.of(
            new StatementNode.ReturnStatementNode(0, 0, Optional.empty(), false)
        ), false);

        node.accept(visitor);
        String result = stream.toString();

        assertEquals(
            "if (false) { }\n" +
            "else\n" +
            "\treturn;",
            result
        );
    }

    @Test
    public void testEmptyMultiIfElseStatement() {
        StringTable stringTable = new StringTable();
        PrettyPrintAstVisitor visitor = new PrettyPrintAstVisitor(stringTable);

        int a = stringTable.insert("a");

        AstNode node = new StatementNode.IfStatementNode(0, 0,
            new ExpressionNode.ValueExpressionNode(0, 0, ExpressionNode.ValueExpressionType.False, false),
            new StatementNode.BlockStatementNode(0, 0, Arrays.asList(), false),
        Optional.of(
            new StatementNode.BlockStatementNode(0, 0, Arrays.asList(
                new StatementNode.LocalVariableDeclarationStatementNode(0, 0, new DataType(DataTypeClass.Int), a, Optional.empty(), false),
                new StatementNode.ReturnStatementNode(0, 0, Optional.empty(), false)
            ), false)
        ), false);

        node.accept(visitor);
        String result = stream.toString();

        assertEquals(
            "if (false) { }\n" +
            "else {\n" +
            "\tint a;\n" +
            "\treturn;\n" +
            "}",
            result
        );
    }

    @Test
    public void testSingleEmptyIfElseStatement() {
        StringTable stringTable = new StringTable();
        PrettyPrintAstVisitor visitor = new PrettyPrintAstVisitor(stringTable);

        int a = stringTable.insert("a");

        AstNode node = new StatementNode.IfStatementNode(0, 0,
            new ExpressionNode.ValueExpressionNode(0, 0, ExpressionNode.ValueExpressionType.False, false),
            new StatementNode.LocalVariableDeclarationStatementNode(0, 0, new DataType(DataTypeClass.Int), a, Optional.empty(), false),
        Optional.of(
            new StatementNode.BlockStatementNode(0, 0, Arrays.asList(), false)
        ), false);

        node.accept(visitor);
        String result = stream.toString();

        assertEquals(
            "if (false)\n" +
            "\tint a;\n" +
            "else { }",
            result
        );
    }

    @Test
    public void testSingleSingleIfElseStatement() {
        StringTable stringTable = new StringTable();
        PrettyPrintAstVisitor visitor = new PrettyPrintAstVisitor(stringTable);

        int a = stringTable.insert("a");

        AstNode node = new StatementNode.IfStatementNode(0, 0,
            new ExpressionNode.ValueExpressionNode(0, 0, ExpressionNode.ValueExpressionType.False, false),
            new StatementNode.LocalVariableDeclarationStatementNode(0, 0, new DataType(DataTypeClass.Int), a, Optional.empty(), false),
        Optional.of(
            new StatementNode.ReturnStatementNode(0, 0, Optional.empty(), false)
        ), false);

        node.accept(visitor);
        String result = stream.toString();

        assertEquals(
            "if (false)\n" +
            "\tint a;\n" +
            "else\n" +
            "\treturn;",
            result
        );
    }

    @Test
    public void testSingleMultiIfElseStatement() {
        StringTable stringTable = new StringTable();
        PrettyPrintAstVisitor visitor = new PrettyPrintAstVisitor(stringTable);

        int a = stringTable.insert("a");

        AstNode node = new StatementNode.IfStatementNode(0, 0,
            new ExpressionNode.ValueExpressionNode(0, 0, ExpressionNode.ValueExpressionType.False, false),
            new StatementNode.LocalVariableDeclarationStatementNode(0, 0, new DataType(DataTypeClass.Int), a, Optional.empty(), false),
        Optional.of(
            new StatementNode.BlockStatementNode(0, 0, Arrays.asList(
                new StatementNode.LocalVariableDeclarationStatementNode(0, 0, new DataType(DataTypeClass.Int), a, Optional.empty(), false),
                new StatementNode.ReturnStatementNode(0, 0, Optional.empty(), false)
            ), false)
        ), false);

        node.accept(visitor);
        String result = stream.toString();

        assertEquals(
            "if (false)\n" +
            "\tint a;\n" +
            "else {\n" +
            "\tint a;\n" +
            "\treturn;\n" +
            "}",
            result
        );
    }

    @Test
    public void testMultiEmptyIfElseStatement() {
        StringTable stringTable = new StringTable();
        PrettyPrintAstVisitor visitor = new PrettyPrintAstVisitor(stringTable);

        int a = stringTable.insert("a");

        AstNode node = new StatementNode.IfStatementNode(0, 0,
            new ExpressionNode.ValueExpressionNode(0, 0, ExpressionNode.ValueExpressionType.False, false),
            new StatementNode.BlockStatementNode(0, 0, Arrays.asList(
                new StatementNode.LocalVariableDeclarationStatementNode(0, 0, new DataType(DataTypeClass.Int), a, Optional.empty(), false),
                new StatementNode.ReturnStatementNode(0, 0, Optional.empty(), false)
            ), false),
        Optional.of(
            new StatementNode.BlockStatementNode(0, 0, Arrays.asList(), false)
        ), false);

        node.accept(visitor);
        String result = stream.toString();

        assertEquals(
            "if (false) {\n" +
            "\tint a;\n" +
            "\treturn;\n" +
            "} else { }",
            result
        );
    }

    @Test
    public void testMultiSingleIfElseStatement() {
        StringTable stringTable = new StringTable();
        PrettyPrintAstVisitor visitor = new PrettyPrintAstVisitor(stringTable);

        int a = stringTable.insert("a");

        AstNode node = new StatementNode.IfStatementNode(0, 0,
            new ExpressionNode.ValueExpressionNode(0, 0, ExpressionNode.ValueExpressionType.False, false),
            new StatementNode.BlockStatementNode(0, 0, Arrays.asList(
                new StatementNode.LocalVariableDeclarationStatementNode(0, 0, new DataType(DataTypeClass.Int), a, Optional.empty(), false),
                new StatementNode.ReturnStatementNode(0, 0, Optional.empty(), false)
            ), false),
        Optional.of(
            new StatementNode.LocalVariableDeclarationStatementNode(0, 0, new DataType(DataTypeClass.Int), a, Optional.empty(), false)
        ), false);

        node.accept(visitor);
        String result = stream.toString();

        assertEquals(
            "if (false) {\n" +
            "\tint a;\n" +
            "\treturn;\n" +
            "} else\n" +
            "\tint a;",
            result
        );
    }

    @Test
    public void testMultiMultiIfElseStatement() {
        StringTable stringTable = new StringTable();
        PrettyPrintAstVisitor visitor = new PrettyPrintAstVisitor(stringTable);

        int a = stringTable.insert("a");

        AstNode node = new StatementNode.IfStatementNode(0, 0,
            new ExpressionNode.ValueExpressionNode(0, 0, ExpressionNode.ValueExpressionType.False, false),
            new StatementNode.BlockStatementNode(0, 0, Arrays.asList(
                new StatementNode.LocalVariableDeclarationStatementNode(0, 0, new DataType(DataTypeClass.Int), a, Optional.empty(), false),
                new StatementNode.ReturnStatementNode(0, 0, Optional.empty(), false)
            ), false),
        Optional.of(
            new StatementNode.BlockStatementNode(0, 0, Arrays.asList(
                new StatementNode.LocalVariableDeclarationStatementNode(0, 0, new DataType(DataTypeClass.Int), a, Optional.empty(), false),
                new StatementNode.ReturnStatementNode(0, 0, Optional.empty(), false)
            ), false)
        ), false);

        node.accept(visitor);
        String result = stream.toString();

        assertEquals(
            "if (false) {\n" +
            "\tint a;\n" +
            "\treturn;\n" +
            "} else {\n" +
            "\tint a;\n" +
            "\treturn;\n" +
            "}",
            result
        );
    }

    @Test
    public void testSingleElseIfStatement() {
        StringTable stringTable = new StringTable();
        PrettyPrintAstVisitor visitor = new PrettyPrintAstVisitor(stringTable);

        int a = stringTable.insert("a");

        AstNode node = new StatementNode.IfStatementNode(0, 0,
            new ExpressionNode.ValueExpressionNode(0, 0, ExpressionNode.ValueExpressionType.False, false),
            new StatementNode.ReturnStatementNode(0, 0, Optional.empty(), false),
        Optional.of(
            new StatementNode.IfStatementNode(0, 0,
                new ExpressionNode.BinaryExpressionNode(0, 0, Operator.BinaryOperator.GreaterThanOrEqual,
                    new ExpressionNode.ValueExpressionNode(0, 0, ExpressionNode.ValueExpressionType.IntegerLiteral, Literal.ofValue(2), false),
                    new ExpressionNode.ValueExpressionNode(0, 0, ExpressionNode.ValueExpressionType.IntegerLiteral, Literal.ofValue(4), false),
                false),
                new StatementNode.ExpressionStatementNode(0, 0,
                    new ExpressionNode.ValueExpressionNode(0, 0, a, false),
                false),
            Optional.of(
                new StatementNode.ReturnStatementNode(0, 0, Optional.empty(), false)
            ), false)
        ), false);

        node.accept(visitor);
        String result = stream.toString();

        assertEquals(
            "if (false)\n" +
            "\treturn;\n" +
            "else if (2 >= 4)\n" +
            "\ta;\n" +
            "else\n" +
            "\treturn;",
            result
        );
    }

    @Test
    public void testMultiElseIfStatement() {
        StringTable stringTable = new StringTable();
        PrettyPrintAstVisitor visitor = new PrettyPrintAstVisitor(stringTable);

        int a = stringTable.insert("a");
        int b = stringTable.insert("b");

        AstNode node = new StatementNode.IfStatementNode(0, 0,
            new ExpressionNode.ValueExpressionNode(0, 0, ExpressionNode.ValueExpressionType.False, false),
            new StatementNode.BlockStatementNode(0, 0, Arrays.asList(
                new StatementNode.ExpressionStatementNode(0, 0,
                    new ExpressionNode.ValueExpressionNode(0, 0, a, false),
                false),
                new StatementNode.ReturnStatementNode(0, 0, Optional.empty(), false)
            ), false),
        Optional.of(
            new StatementNode.IfStatementNode(0, 0,
                new ExpressionNode.BinaryExpressionNode(0, 0, Operator.BinaryOperator.GreaterThanOrEqual,
                    new ExpressionNode.ValueExpressionNode(0, 0, ExpressionNode.ValueExpressionType.IntegerLiteral, Literal.ofValue(2), false),
                    new ExpressionNode.ValueExpressionNode(0, 0, ExpressionNode.ValueExpressionType.IntegerLiteral, Literal.ofValue(4), false),
                false),
                new StatementNode.BlockStatementNode(0, 0, Arrays.asList(
                    new StatementNode.ExpressionStatementNode(0, 0,
                        new ExpressionNode.ValueExpressionNode(0, 0, a, false),
                    false),
                    new StatementNode.ReturnStatementNode(0, 0, Optional.of(
                        new ExpressionNode.ValueExpressionNode(0, 0, b, false)
                    ), false)
                ), false),
            Optional.of(
                new StatementNode.BlockStatementNode(0, 0, Arrays.asList(
                    new StatementNode.ExpressionStatementNode(0, 0,
                        new ExpressionNode.ValueExpressionNode(0, 0, b, false),
                    false),
                    new StatementNode.ReturnStatementNode(0, 0, Optional.empty(), false)
                ), false)
            ), false)
        ), false);

        node.accept(visitor);
        String result = stream.toString();

        assertEquals(
            "if (false) {\n" +
            "\ta;\n" +
            "\treturn;\n" +
            "} else if (2 >= 4) {\n" +
            "\ta;\n" +
            "\treturn b;\n" +
            "} else {\n" +
            "\tb;\n" +
            "\treturn;\n" +
            "}",
            result
        );
    }

    @Test
    public void testEmptyWhileStatement() {
        StringTable stringTable = new StringTable();
        PrettyPrintAstVisitor visitor = new PrettyPrintAstVisitor(stringTable);

        AstNode node = new StatementNode.WhileStatementNode(0, 0,
            new ExpressionNode.ValueExpressionNode(0, 0, ExpressionNode.ValueExpressionType.False, false),
            new StatementNode.BlockStatementNode(0, 0, Arrays.asList(), false),
        false);

        node.accept(visitor);
        String result = stream.toString();

        assertEquals(
            "while (false) { }",
            result
        );
    }

    @Test
    public void testSingleWhileStatement() {
        StringTable stringTable = new StringTable();
        PrettyPrintAstVisitor visitor = new PrettyPrintAstVisitor(stringTable);

        AstNode node = new StatementNode.WhileStatementNode(0, 0,
            new ExpressionNode.ValueExpressionNode(0, 0, ExpressionNode.ValueExpressionType.False, false),
            new StatementNode.ReturnStatementNode(0, 0, Optional.empty(), false),
        false);

        node.accept(visitor);
        String result = stream.toString();

        assertEquals(
            "while (false)\n" +
            "\treturn;",
            result
        );
    }

    @Test
    public void testMultiWhileStatement() {
        StringTable stringTable = new StringTable();
        PrettyPrintAstVisitor visitor = new PrettyPrintAstVisitor(stringTable);

        int a = stringTable.insert("a");

        AstNode node = new StatementNode.WhileStatementNode(0, 0,
            new ExpressionNode.ValueExpressionNode(0, 0, ExpressionNode.ValueExpressionType.False, false),
            new StatementNode.BlockStatementNode(0, 0, Arrays.asList(
                new StatementNode.LocalVariableDeclarationStatementNode(0, 0, new DataType(DataTypeClass.Int), a, Optional.empty(), false),
                new StatementNode.ReturnStatementNode(0, 0, Optional.empty(), false)
            ), false),
        false);

        node.accept(visitor);
        String result = stream.toString();

        assertEquals(
            "while (false) {\n" +
            "\tint a;\n" +
            "\treturn;\n" +
            "}",
            result
        );
    }

    @Test
    public void testEmptyReturnStatement() {
        StringTable stringTable = new StringTable();
        PrettyPrintAstVisitor visitor = new PrettyPrintAstVisitor(stringTable);

        AstNode node = new StatementNode.ReturnStatementNode(0, 0, Optional.empty(), false);

        node.accept(visitor);
        String result = stream.toString();

        assertEquals(
            "return;",
            result
        );
    }

    @Test
    public void testValueReturnStatement() {
        StringTable stringTable = new StringTable();
        PrettyPrintAstVisitor visitor = new PrettyPrintAstVisitor(stringTable);

        AstNode node = new StatementNode.ReturnStatementNode(0, 0, Optional.of(
            new ExpressionNode.ValueExpressionNode(0, 0, ExpressionNode.ValueExpressionType.Null, false)
        ), false);

        node.accept(visitor);
        String result = stream.toString();

        assertEquals(
            "return null;",
            result
        );
    }

    @Test
    public void testExpressionReturnStatement() {
        StringTable stringTable = new StringTable();
        PrettyPrintAstVisitor visitor = new PrettyPrintAstVisitor(stringTable);

        AstNode node = new StatementNode.ReturnStatementNode(0, 0, Optional.of(
            new ExpressionNode.BinaryExpressionNode(0, 0, Operator.BinaryOperator.Addition,
                new ExpressionNode.ValueExpressionNode(0, 0, ExpressionNode.ValueExpressionType.IntegerLiteral, Literal.ofValue(17), false),
                new ExpressionNode.ValueExpressionNode(0, 0, ExpressionNode.ValueExpressionType.IntegerLiteral, Literal.ofValue(1), false),
            false)
        ), false);

        node.accept(visitor);
        String result = stream.toString();

        assertEquals(
            "return 17 + 1;",
            result
        );
    }

    @Test
    public void testExpressionStatement() {
        StringTable stringTable = new StringTable();
        PrettyPrintAstVisitor visitor = new PrettyPrintAstVisitor(stringTable);

        AstNode node = new StatementNode.ExpressionStatementNode(0, 0,
            new ExpressionNode.BinaryExpressionNode(0, 0, Operator.BinaryOperator.Addition,
                new ExpressionNode.ValueExpressionNode(0, 0, ExpressionNode.ValueExpressionType.IntegerLiteral, Literal.ofValue(17), false),
                new ExpressionNode.ValueExpressionNode(0, 0, ExpressionNode.ValueExpressionType.IntegerLiteral, Literal.ofValue(1), false),
            false),
        false);

        node.accept(visitor);
        String result = stream.toString();

        assertEquals(
            "17 + 1;",
            result
        );
    }

    @Test
    public void testBinaryExpression() {
        StringTable stringTable = new StringTable();
        PrettyPrintAstVisitor visitor = new PrettyPrintAstVisitor(stringTable);

        AstNode node = new ExpressionNode.BinaryExpressionNode(0, 0, Operator.BinaryOperator.Addition,
            new ExpressionNode.ValueExpressionNode(0, 0, ExpressionNode.ValueExpressionType.IntegerLiteral, Literal.ofValue(17), false),
            new ExpressionNode.ValueExpressionNode(0, 0, ExpressionNode.ValueExpressionType.IntegerLiteral, Literal.ofValue(1), false),
        false);

        node.accept(visitor);
        String result = stream.toString();

        assertEquals(
            "17 + 1",
            result
        );
    }

    @Test
    public void testMultiLevelBinaryExpression() {
        StringTable stringTable = new StringTable();
        PrettyPrintAstVisitor visitor = new PrettyPrintAstVisitor(stringTable);

        AstNode node = new ExpressionNode.BinaryExpressionNode(0, 0, Operator.BinaryOperator.Addition,
            new ExpressionNode.BinaryExpressionNode(0, 0, Operator.BinaryOperator.Multiplication,
                new ExpressionNode.ValueExpressionNode(0, 0, ExpressionNode.ValueExpressionType.IntegerLiteral, Literal.ofValue(17), false),
                new ExpressionNode.ValueExpressionNode(0, 0, ExpressionNode.ValueExpressionType.IntegerLiteral, Literal.ofValue(5), false),
            false),
            new ExpressionNode.ValueExpressionNode(0, 0, ExpressionNode.ValueExpressionType.IntegerLiteral, Literal.ofValue(1), false),
        false);

        node.accept(visitor);
        String result = stream.toString();

        assertEquals(
            "(17 * 5) + 1",
            result
        );
    }

    @Test
    public void testAssignmentBinaryExpression() {
        StringTable stringTable = new StringTable();
        PrettyPrintAstVisitor visitor = new PrettyPrintAstVisitor(stringTable);

        int a = stringTable.insert("a");

        AstNode node = new ExpressionNode.BinaryExpressionNode(0, 0, Operator.BinaryOperator.Assignment,
            new ExpressionNode.ValueExpressionNode(0, 0, a, false),
            new ExpressionNode.BinaryExpressionNode(0, 0, Operator.BinaryOperator.Multiplication,
                new ExpressionNode.ValueExpressionNode(0, 0, ExpressionNode.ValueExpressionType.IntegerLiteral, Literal.ofValue(17), false),
                new ExpressionNode.ValueExpressionNode(0, 0, ExpressionNode.ValueExpressionType.IntegerLiteral, Literal.ofValue(5), false),
            false),
        false);

        node.accept(visitor);
        String result = stream.toString();

        assertEquals(
            "a = (17 * 5)",
            result
        );
    }

    @Test
    public void testUnaryExpression() {
        StringTable stringTable = new StringTable();
        PrettyPrintAstVisitor visitor = new PrettyPrintAstVisitor(stringTable);

        AstNode node = new ExpressionNode.UnaryExpressionNode(0, 0, Operator.UnaryOperator.LogicalNegation,
            new ExpressionNode.ValueExpressionNode(0, 0, ExpressionNode.ValueExpressionType.False, false),
        false);

        node.accept(visitor);
        String result = stream.toString();

        assertEquals(
            "!false",
            result
        );
    }

    @Test
    public void testMultiLevelUnaryExpression() {
        StringTable stringTable = new StringTable();
        PrettyPrintAstVisitor visitor = new PrettyPrintAstVisitor(stringTable);

        AstNode node = new ExpressionNode.UnaryExpressionNode(0, 0, Operator.UnaryOperator.LogicalNegation,
            new ExpressionNode.UnaryExpressionNode(0, 0, Operator.UnaryOperator.LogicalNegation,
                new ExpressionNode.ValueExpressionNode(0, 0, ExpressionNode.ValueExpressionType.False, false),
            false),
        false);

        node.accept(visitor);
        String result = stream.toString();

        assertEquals(
            "!(!false)",
            result
        );
    }

    @Test
    public void testEmptyMethodInvocationExpression() {
        StringTable stringTable = new StringTable();
        PrettyPrintAstVisitor visitor = new PrettyPrintAstVisitor(stringTable);

        int a = stringTable.insert("a");

        AstNode node = new ExpressionNode.MethodInvocationExpressionNode(0, 0, Optional.empty(), a, Arrays.asList(), false);

        node.accept(visitor);
        String result = stream.toString();

        assertEquals(
            "a()",
            result
        );
    }

    @Test
    public void testMethodInvocationExpression() {
        StringTable stringTable = new StringTable();
        PrettyPrintAstVisitor visitor = new PrettyPrintAstVisitor(stringTable);

        int a = stringTable.insert("a");

        AstNode node = new ExpressionNode.MethodInvocationExpressionNode(0, 0, Optional.empty(), a, Arrays.asList(
            new ExpressionNode.ValueExpressionNode(0, 0, ExpressionNode.ValueExpressionType.IntegerLiteral, Literal.ofValue(42), false),
            new ExpressionNode.UnaryExpressionNode(0, 0, Operator.UnaryOperator.LogicalNegation,
                new ExpressionNode.ValueExpressionNode(0, 0, ExpressionNode.ValueExpressionType.False, false),
            false)
        ), false);

        node.accept(visitor);
        String result = stream.toString();

        assertEquals(
            "a(42, !false)",
            result
        );
    }

    @Test
    public void testObjectMethodInvocationExpression() {
        StringTable stringTable = new StringTable();
        PrettyPrintAstVisitor visitor = new PrettyPrintAstVisitor(stringTable);

        int a = stringTable.insert("a");
        int b = stringTable.insert("b");

        AstNode node = new ExpressionNode.MethodInvocationExpressionNode(0, 0, Optional.of(
            new ExpressionNode.ValueExpressionNode(0, 0, a, false)
        ), b, Arrays.asList(
            new ExpressionNode.ValueExpressionNode(0, 0, ExpressionNode.ValueExpressionType.IntegerLiteral, Literal.ofValue(42), false),
            new ExpressionNode.BinaryExpressionNode(0, 0, Operator.BinaryOperator.LogicalAnd,
                new ExpressionNode.ValueExpressionNode(0, 0, ExpressionNode.ValueExpressionType.False, false),
                new ExpressionNode.ValueExpressionNode(0, 0, ExpressionNode.ValueExpressionType.True, false),
            false)
        ), false);

        node.accept(visitor);
        String result = stream.toString();

        assertEquals(
            "a.b(42, false && true)",
            result
        );
    }

    @Test
    public void testFieldAccessExpression() {
        StringTable stringTable = new StringTable();
        PrettyPrintAstVisitor visitor = new PrettyPrintAstVisitor(stringTable);

        int a = stringTable.insert("a");
        int b = stringTable.insert("b");

        AstNode node = new ExpressionNode.FieldAccessExpressionNode(0, 0,
            new ExpressionNode.ValueExpressionNode(0, 0, a, false),
        b, false);

        node.accept(visitor);
        String result = stream.toString();

        assertEquals(
            "a.b",
            result
        );
    }

    @Test
    public void testArrayAccessExpression() {
        StringTable stringTable = new StringTable();
        PrettyPrintAstVisitor visitor = new PrettyPrintAstVisitor(stringTable);

        int a = stringTable.insert("a");

        AstNode node = new ExpressionNode.ArrayAccessExpressionNode(0, 0,
            new ExpressionNode.ValueExpressionNode(0, 0, a, false),
            new ExpressionNode.BinaryExpressionNode(0, 0, Operator.BinaryOperator.Addition,
                new ExpressionNode.ValueExpressionNode(0, 0, ExpressionNode.ValueExpressionType.IntegerLiteral, Literal.ofValue(17), false),
                new ExpressionNode.ValueExpressionNode(0, 0, ExpressionNode.ValueExpressionType.IntegerLiteral, Literal.ofValue(2), false),
            false),
        false);

        node.accept(visitor);
        String result = stream.toString();

        assertEquals(
            "a[17 + 2]",
            result
        );
    }

    @Test
    public void testComplexArrayAccessExpression() {
        StringTable stringTable = new StringTable();
        PrettyPrintAstVisitor visitor = new PrettyPrintAstVisitor(stringTable);

        int a = stringTable.insert("a");

        AstNode node = new ExpressionNode.ArrayAccessExpressionNode(0, 0,
            new ExpressionNode.ValueExpressionNode(0, 0, a, false),
            new ExpressionNode.BinaryExpressionNode(0, 0, Operator.BinaryOperator.Multiplication,
                new ExpressionNode.ValueExpressionNode(0, 0, ExpressionNode.ValueExpressionType.IntegerLiteral, Literal.ofValue(17), false),
                new ExpressionNode.BinaryExpressionNode(0, 0, Operator.BinaryOperator.Addition,
                    new ExpressionNode.ValueExpressionNode(0, 0, ExpressionNode.ValueExpressionType.IntegerLiteral, Literal.ofValue(1), false),
                    new ExpressionNode.ValueExpressionNode(0, 0, ExpressionNode.ValueExpressionType.IntegerLiteral, Literal.ofValue(1), false),
                false),
            false),
        false);

        node.accept(visitor);
        String result = stream.toString();

        assertEquals(
            "a[17 * (1 + 1)]",
            result
        );
    }

    @Test
    public void testFalseValueExpression() {
        StringTable stringTable = new StringTable();
        PrettyPrintAstVisitor visitor = new PrettyPrintAstVisitor(stringTable);

        AstNode node = new ExpressionNode.ValueExpressionNode(0, 0, ExpressionNode.ValueExpressionType.False, false);

        node.accept(visitor);
        String result = stream.toString();

        assertEquals(
            "false",
            result
        );
    }

    @Test
    public void testIdentifierValueExpression() {
        StringTable stringTable = new StringTable();
        PrettyPrintAstVisitor visitor = new PrettyPrintAstVisitor(stringTable);

        int a = stringTable.insert("a");

        AstNode node = new ExpressionNode.ValueExpressionNode(0, 0, a, false);

        node.accept(visitor);
        String result = stream.toString();

        assertEquals(
            "a",
            result
        );
    }

    @Test
    public void testIntegerLiteralValueExpression() {
        StringTable stringTable = new StringTable();
        PrettyPrintAstVisitor visitor = new PrettyPrintAstVisitor(stringTable);

        AstNode node = new ExpressionNode.ValueExpressionNode(0, 0, ExpressionNode.ValueExpressionType.IntegerLiteral, Literal.ofValue(17), false);

        node.accept(visitor);
        String result = stream.toString();

        assertEquals(
            "17",
            result
        );
    }

    @Test
    public void testNullValueExpression() {
        StringTable stringTable = new StringTable();
        PrettyPrintAstVisitor visitor = new PrettyPrintAstVisitor(stringTable);

        AstNode node = new ExpressionNode.ValueExpressionNode(0, 0, ExpressionNode.ValueExpressionType.Null, false);

        node.accept(visitor);
        String result = stream.toString();

        assertEquals(
            "null",
            result
        );
    }

    @Test
    public void testThisValueExpression() {
        StringTable stringTable = new StringTable();
        PrettyPrintAstVisitor visitor = new PrettyPrintAstVisitor(stringTable);

        AstNode node = new ExpressionNode.ValueExpressionNode(0, 0, ExpressionNode.ValueExpressionType.This, false);

        node.accept(visitor);
        String result = stream.toString();

        assertEquals(
            "this",
            result
        );
    }

    @Test
    public void testTrueValueExpression() {
        StringTable stringTable = new StringTable();
        PrettyPrintAstVisitor visitor = new PrettyPrintAstVisitor(stringTable);

        AstNode node = new ExpressionNode.ValueExpressionNode(0, 0, ExpressionNode.ValueExpressionType.True, false);

        node.accept(visitor);
        String result = stream.toString();

        assertEquals(
            "true",
            result
        );
    }

    @Test
    public void testNewObjectExpression() {
        StringTable stringTable = new StringTable();
        PrettyPrintAstVisitor visitor = new PrettyPrintAstVisitor(stringTable);

        int a = stringTable.insert("ClassA");

        AstNode node = new ExpressionNode.NewObjectExpressionNode(0, 0, a, false);

        node.accept(visitor);
        String result = stream.toString();

        assertEquals(
            "new ClassA()",
            result
        );
    }

    @Test
    public void testSingleNewArrayExpression() {
        StringTable stringTable = new StringTable();
        PrettyPrintAstVisitor visitor = new PrettyPrintAstVisitor(stringTable);

        int a = stringTable.insert("ClassA");

        AstNode node = new ExpressionNode.NewArrayExpressionNode(0, 0, new DataType(a),
            new ExpressionNode.ValueExpressionNode(0, 0, ExpressionNode.ValueExpressionType.IntegerLiteral, Literal.ofValue(5), false),
        1, false);

        node.accept(visitor);
        String result = stream.toString();

        assertEquals(
            "new ClassA[5]",
            result
        );
    }

    @Test
    public void testMultiNewArrayExpression() {
        StringTable stringTable = new StringTable();
        PrettyPrintAstVisitor visitor = new PrettyPrintAstVisitor(stringTable);

        int a = stringTable.insert("ClassA");

        AstNode node = new ExpressionNode.NewArrayExpressionNode(0, 0, new DataType(a),
            new ExpressionNode.BinaryExpressionNode(0, 0, Operator.BinaryOperator.Addition,
                new ExpressionNode.ValueExpressionNode(0, 0, ExpressionNode.ValueExpressionType.IntegerLiteral, Literal.ofValue(3), false),
                new ExpressionNode.ValueExpressionNode(0, 0, ExpressionNode.ValueExpressionType.IntegerLiteral, Literal.ofValue(2), false),
            false),
        3, false);

        node.accept(visitor);
        String result = stream.toString();

        assertEquals(
            "new ClassA[3 + 2][][]",
            result
        );
    }

    @Test
    public void testComplexExpression() {
        StringTable stringTable = new StringTable();
        PrettyPrintAstVisitor visitor = new PrettyPrintAstVisitor(stringTable);

        int a = stringTable.insert("a");
        int b = stringTable.insert("b");
        int foo = stringTable.insert("foo");

        // 17 * (a.b[2 + 2] % foo(-31, 2 < 5))

        AstNode node = new ExpressionNode.BinaryExpressionNode(0, 0, Operator.BinaryOperator.Multiplication,
            new ExpressionNode.ValueExpressionNode(0, 0, ExpressionNode.ValueExpressionType.IntegerLiteral, Literal.ofValue(17), false),
            new ExpressionNode.BinaryExpressionNode(0, 0, Operator.BinaryOperator.Modulo,
                new ExpressionNode.ArrayAccessExpressionNode(0, 0,
                    new ExpressionNode.FieldAccessExpressionNode(0, 0,
                        new ExpressionNode.ValueExpressionNode(0, 0, a, false),
                    b, false),
                    new ExpressionNode.BinaryExpressionNode(0, 0, Operator.BinaryOperator.Addition,
                        new ExpressionNode.ValueExpressionNode(0, 0, ExpressionNode.ValueExpressionType.IntegerLiteral, Literal.ofValue(2), false),
                        new ExpressionNode.ValueExpressionNode(0, 0, ExpressionNode.ValueExpressionType.IntegerLiteral, Literal.ofValue(2), false),
                    false),
                false),
                new ExpressionNode.MethodInvocationExpressionNode(0, 0, Optional.empty(), foo, Arrays.asList(
                    new ExpressionNode.UnaryExpressionNode(0, 0, Operator.UnaryOperator.ArithmeticNegation,
                        new ExpressionNode.ValueExpressionNode(0, 0, ExpressionNode.ValueExpressionType.IntegerLiteral, Literal.ofValue(31), false),
                    false),
                    new ExpressionNode.BinaryExpressionNode(0, 0, Operator.BinaryOperator.LessThan,
                        new ExpressionNode.ValueExpressionNode(0, 0, ExpressionNode.ValueExpressionType.IntegerLiteral, Literal.ofValue(2), false),
                        new ExpressionNode.ValueExpressionNode(0, 0, ExpressionNode.ValueExpressionType.IntegerLiteral, Literal.ofValue(5), false),
                    false)
                ), false),
            false),
        false);

        node.accept(visitor);
        String result = stream.toString();

        assertEquals(
            "17 * (((a.b)[2 + 2]) % (foo(-31, 2 < 5)))",
            result
        );
    }

    @Test
    public void testMultiLevels() {
        StringTable stringTable = new StringTable();
        PrettyPrintAstVisitor visitor = new PrettyPrintAstVisitor(stringTable);

        int a = stringTable.insert("ClassA");
        int b = stringTable.insert("MethodB");

        AstNode node = new ProgramNode(0, 0, Arrays.asList(
            new ClassNode(0, 0, a, Arrays.asList(), Arrays.asList(), Arrays.asList(
                new MethodNode.DynamicMethodNode(0, 0, new DataType(DataTypeClass.Int), b, Arrays.asList(), new MethodNode.MethodNodeRest(Optional.empty()), Arrays.asList(
                    new StatementNode.ReturnStatementNode(0, 0, Optional.empty(), false)
                ), false)
            ), false)
        ), false);

        node.accept(visitor);
        String result = stream.toString();

        assertEquals(
            "class ClassA {\n" +
            "\tpublic int MethodB() {\n" +
            "\t\treturn;\n" +
            "\t}\n" +
            "}\n",
            result
        );
    }

}
