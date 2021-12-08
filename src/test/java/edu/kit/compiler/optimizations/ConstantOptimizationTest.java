package edu.kit.compiler.optimizations;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.Test;

import edu.kit.compiler.data.DataType;
import edu.kit.compiler.data.Literal;
import edu.kit.compiler.data.DataType.DataTypeClass;
import edu.kit.compiler.data.Operator.BinaryOperator;
import edu.kit.compiler.data.Operator.UnaryOperator;
import edu.kit.compiler.data.ast_nodes.ClassNode;
import edu.kit.compiler.data.ast_nodes.ExpressionNode;
import edu.kit.compiler.data.ast_nodes.MethodNode;
import edu.kit.compiler.data.ast_nodes.ProgramNode;
import edu.kit.compiler.data.ast_nodes.StatementNode;
import edu.kit.compiler.data.ast_nodes.ExpressionNode.ValueExpressionType;
import edu.kit.compiler.lexer.StringTable;
import edu.kit.compiler.logger.Logger;
import edu.kit.compiler.semantic.DetailedNameTypeAstVisitor;
import edu.kit.compiler.semantic.ErrorHandler;
import edu.kit.compiler.semantic.NamespaceGatheringVisitor;
import edu.kit.compiler.semantic.NamespaceMapper;
import edu.kit.compiler.transform.IRVisitor;
import edu.kit.compiler.transform.JFirmSingleton;

import firm.Graph;
import firm.Program;
import firm.TargetValue;
import firm.bindings.binding_irnode.ir_opcode;
import firm.nodes.*;

public class ConstantOptimizationTest {

    private Graph build(StringTable stringTable, Iterable<StatementNode> statements) {
        JFirmSingleton.initializeFirmLinux();

        NamespaceMapper namespaceMapper = new NamespaceMapper();
        ErrorHandler errorHandler = new ErrorHandler(Logger.nullLogger());

        ClassNode _class = new ClassNode(0, 0, stringTable.insert("ClassA"), Arrays.asList(), Arrays.asList(
            new MethodNode.StaticMethodNode(0, 0, new DataType(DataTypeClass.Void), stringTable.insert("main"), Arrays.asList(
                new MethodNode.MethodNodeParameter(0, 0, new DataType(new DataType(stringTable.insert("String"))), stringTable.insert("args"), false)
            ), Optional.empty(),
                new StatementNode.BlockStatementNode(0, 0, statements, false),
            false)), Arrays.asList(),
        false);
        ProgramNode program = new ProgramNode(0, 0, Arrays.asList(_class), false);

        NamespaceGatheringVisitor gatheringVisitor = new NamespaceGatheringVisitor(
            namespaceMapper, stringTable, errorHandler
        );
        program.accept(gatheringVisitor);
        DetailedNameTypeAstVisitor nameTypeVisitor = new DetailedNameTypeAstVisitor(
            namespaceMapper, stringTable
        );
        program.accept(nameTypeVisitor);

        IRVisitor irv = new IRVisitor(namespaceMapper, stringTable);
        program.accept(irv);

        List<Graph> graphs = StreamSupport.stream(Program.getGraphs().spliterator(), false).collect(Collectors.toList());
        return graphs.get(graphs.size() - 1);
    }

    private ExpressionNode.MethodInvocationExpressionNode makeStdLibMethodInvocation(StringTable stringTable, String field, String method, List<ExpressionNode> arguments) {
        return new ExpressionNode.MethodInvocationExpressionNode(0, 0, Optional.of(
            new ExpressionNode.FieldAccessExpressionNode(0, 0,
                new ExpressionNode.IdentifierExpressionNode(0, 0, stringTable.insert("System"), false),
            stringTable.insert(field), false)),
        stringTable.insert(method), arguments, false);
    }

    private Iterable<StatementNode> surroundWithIO(StringTable stringTable, List<StatementNode> statements, ExpressionNode expr) {
        return Stream.<List<StatementNode>>of(
            Arrays.asList(
                new StatementNode.LocalVariableDeclarationStatementNode(0, 0, new DataType(DataTypeClass.Int), stringTable.insert("x"), Optional.of(
                    makeStdLibMethodInvocation(stringTable, "in", "read", Arrays.asList())
                ), false)
            ),
            statements,
            Arrays.asList(
                new StatementNode.ExpressionStatementNode(0, 0,
                    makeStdLibMethodInvocation(stringTable, "out", "write", Arrays.asList(
                        expr
                    )),
                false)
            )
        ).flatMap(List::stream).collect(Collectors.toList());
    }

    private Iterable<StatementNode> surroundWithIO(StringTable stringTable, ExpressionNode expr) {
        return surroundWithIO(stringTable, Arrays.asList(), expr);
    }

    private List<Node> getNodes(Graph graph) {
        List<Node> nodes = new ArrayList<>();
        NodeVisitor nodeVisitor = new NodeVisitor.Default() {

            @Override
            public void defaultVisit(Node node) {
                nodes.add(node);
            }

        };
        graph.walkPostorder(nodeVisitor);

        return nodes;
    }

    private void assertContainsOpCode(List<Node> nodes, ir_opcode opcode) {
        assertTrue(nodes.stream().anyMatch(node -> node.getOpCode() == opcode));
    }

    private void assertDoesNotContainOpCode(List<Node> nodes, ir_opcode opcode) {
        assertFalse(nodes.stream().anyMatch(node -> node.getOpCode() == opcode));
    }

    @Test
    public void testNoConsts() {
        // x + 5 -> x + 5

        StringTable stringTable = new StringTable();
        Graph graph = build(stringTable, surroundWithIO(stringTable,
            new ExpressionNode.BinaryExpressionNode(0, 0, BinaryOperator.Addition,
                new ExpressionNode.IdentifierExpressionNode(0, 0, stringTable.insert("x"), false),
                new ExpressionNode.ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, Literal.ofValue(5), false),
            false)
        ));

        ConstantOptimization optimization = new ConstantOptimization();
        optimization.optimize(graph);

        assertContainsOpCode(getNodes(graph), ir_opcode.iro_Add);
    }

    @Test
    public void testConst() {
        // 5 -> 5

        StringTable stringTable = new StringTable();
        Graph graph = build(stringTable, surroundWithIO(stringTable,
            new ExpressionNode.ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, Literal.ofValue(5), false)
        ));

        ConstantOptimization optimization = new ConstantOptimization();
        optimization.optimize(graph);

        assertContainsOpCode(getNodes(graph), ir_opcode.iro_Const);
    }

    @Test
    public void testUnary() {
        // -(7) -> -7

        StringTable stringTable = new StringTable();
        Graph graph = build(stringTable, surroundWithIO(stringTable,
            new ExpressionNode.UnaryExpressionNode(0, 0, UnaryOperator.ArithmeticNegation,
                new ExpressionNode.ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, Literal.ofValue(7), false),
            false)
        ));

        ConstantOptimization optimization = new ConstantOptimization();
        optimization.optimize(graph);

        assertDoesNotContainOpCode(getNodes(graph), ir_opcode.iro_Minus);
    }

    @Test
    public void testBinary() {
        // 7 + 5 -> 12

        StringTable stringTable = new StringTable();
        Graph graph = build(stringTable, surroundWithIO(stringTable,
            new ExpressionNode.BinaryExpressionNode(0, 0, BinaryOperator.Addition,
                new ExpressionNode.ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, Literal.ofValue(7), false),
                new ExpressionNode.ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, Literal.ofValue(5), false),
            false)
        ));

        ConstantOptimization optimization = new ConstantOptimization();
        optimization.optimize(graph);

        assertDoesNotContainOpCode(getNodes(graph), ir_opcode.iro_Add);
    }

    @Test
    public void testBinaryWithConst() {
        // y + 5 -> 12 with y = 7

        StringTable stringTable = new StringTable();
        Graph graph = build(stringTable, surroundWithIO(stringTable, Arrays.asList(
            new StatementNode.LocalVariableDeclarationStatementNode(0, 0, new DataType(DataTypeClass.Int), stringTable.insert("y"), Optional.of(
                new ExpressionNode.ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, Literal.ofValue(7), false)
            ), false)
        ),
            new ExpressionNode.BinaryExpressionNode(0, 0, BinaryOperator.Addition,
                new ExpressionNode.IdentifierExpressionNode(0, 0, stringTable.insert("y"), false),
                new ExpressionNode.ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, Literal.ofValue(5), false),
            false)
        ));

        ConstantOptimization optimization = new ConstantOptimization();
        optimization.optimize(graph);

        assertDoesNotContainOpCode(getNodes(graph), ir_opcode.iro_Add);
    }

    @Test
    public void testBinaryWithBinary() {
        // y + 5 -> 15 with y = 7 + 3

        StringTable stringTable = new StringTable();
        Graph graph = build(stringTable, surroundWithIO(stringTable, Arrays.asList(
            new StatementNode.LocalVariableDeclarationStatementNode(0, 0, new DataType(DataTypeClass.Int), stringTable.insert("y"), Optional.of(
                new ExpressionNode.BinaryExpressionNode(0, 0, BinaryOperator.Addition,
                    new ExpressionNode.ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, Literal.ofValue(7), false),
                    new ExpressionNode.ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, Literal.ofValue(3), false),
                false)
            ), false)
        ),
            new ExpressionNode.BinaryExpressionNode(0, 0, BinaryOperator.Addition,
                new ExpressionNode.IdentifierExpressionNode(0, 0, stringTable.insert("y"), false),
                new ExpressionNode.ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, Literal.ofValue(5), false),
            false)
        ));

        ConstantOptimization optimization = new ConstantOptimization();
        optimization.optimize(graph);

        assertDoesNotContainOpCode(getNodes(graph), ir_opcode.iro_Add);
    }

    @Test
    public void testDiv() {
        // 10 / 2 -> 5

        StringTable stringTable = new StringTable();
        Graph graph = build(stringTable, surroundWithIO(stringTable, Arrays.asList(),
            new ExpressionNode.BinaryExpressionNode(0, 0, BinaryOperator.Division,
                new ExpressionNode.ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, Literal.ofValue(10), false),
                new ExpressionNode.ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, Literal.ofValue(2), false),
            false)
        ));

        ConstantOptimization optimization = new ConstantOptimization();
        optimization.optimize(graph);

        assertDoesNotContainOpCode(getNodes(graph), ir_opcode.iro_Div);
    }

    @Test
    public void testIf() {
        // x < 5 ? 3 : 3 -> 3

        StringTable stringTable = new StringTable();
        Graph graph = build(stringTable, surroundWithIO(stringTable, Arrays.asList(
            new StatementNode.LocalVariableDeclarationStatementNode(0, 0, new DataType(DataTypeClass.Int), stringTable.insert("y"), Optional.empty(), false),
            new StatementNode.IfStatementNode(0, 0,
                new ExpressionNode.BinaryExpressionNode(0, 0, BinaryOperator.LessThan,
                    new ExpressionNode.IdentifierExpressionNode(0, 0, stringTable.insert("x"), false),
                    new ExpressionNode.ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, Literal.ofValue(5), false),
                false),
                new StatementNode.ExpressionStatementNode(0, 0,
                    new ExpressionNode.BinaryExpressionNode(0, 0, BinaryOperator.Assignment,
                        new ExpressionNode.IdentifierExpressionNode(0, 0, stringTable.insert("y"), false),
                        new ExpressionNode.ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, Literal.ofValue(3), false),
                    false),
                false),
                Optional.of(new StatementNode.ExpressionStatementNode(0, 0,
                    new ExpressionNode.BinaryExpressionNode(0, 0, BinaryOperator.Assignment,
                        new ExpressionNode.IdentifierExpressionNode(0, 0, stringTable.insert("y"), false),
                        new ExpressionNode.ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, Literal.ofValue(3), false),
                    false),
                false)),
            false)
        ),
            new ExpressionNode.IdentifierExpressionNode(0, 0, stringTable.insert("y"), false)
        ));

        ConstantOptimization optimization = new ConstantOptimization();
        optimization.optimize(graph);

        assertDoesNotContainOpCode(getNodes(graph), ir_opcode.iro_Phi);
    }

    @Test
    public void testWhile() {
        // y <- 3 ; while (x < 5) { y <- 3 } -> 3

        StringTable stringTable = new StringTable();
        Graph graph = build(stringTable, surroundWithIO(stringTable, Arrays.asList(
            new StatementNode.LocalVariableDeclarationStatementNode(0, 0, new DataType(DataTypeClass.Int), stringTable.insert("y"), Optional.of(
                new ExpressionNode.ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, Literal.ofValue(3), false)
            ), false),
            new StatementNode.WhileStatementNode(0, 0,
                new ExpressionNode.BinaryExpressionNode(0, 0, BinaryOperator.LessThan,
                    new ExpressionNode.IdentifierExpressionNode(0, 0, stringTable.insert("x"), false),
                    new ExpressionNode.ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, Literal.ofValue(5), false),
                false),
                new StatementNode.ExpressionStatementNode(0, 0,
                    new ExpressionNode.BinaryExpressionNode(0, 0, BinaryOperator.Assignment,
                        new ExpressionNode.IdentifierExpressionNode(0, 0, stringTable.insert("y"), false),
                        new ExpressionNode.ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, Literal.ofValue(3), false),
                    false),
                false),
            false)
        ),
            new ExpressionNode.IdentifierExpressionNode(0, 0, stringTable.insert("y"), false)
        ));

        ConstantOptimization optimization = new ConstantOptimization();
        optimization.optimize(graph);

        boolean containsPhi = getNodes(graph).stream().anyMatch(node ->
            node instanceof Phi &&
            StreamSupport.stream(node.getPreds().spliterator(), false).noneMatch(pred ->
                pred.equals(node)
            )
        );

        assertFalse(containsPhi);
    }

    @Test
    public void testCmpExclFalse() {
        // 7 < 5 -> false

        StringTable stringTable = new StringTable();
        Graph graph = build(stringTable, surroundWithIO(stringTable, Arrays.asList(
            new StatementNode.LocalVariableDeclarationStatementNode(0, 0, new DataType(DataTypeClass.Int), stringTable.insert("y"), Optional.of(
                new ExpressionNode.ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, Literal.ofValue(2), false)
            ), false),
            new StatementNode.IfStatementNode(0, 0,
                new ExpressionNode.BinaryExpressionNode(0, 0, BinaryOperator.LessThan,
                    new ExpressionNode.ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, Literal.ofValue(7), false),
                    new ExpressionNode.ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, Literal.ofValue(5), false),
                false),
                new StatementNode.ExpressionStatementNode(0, 0,
                    new ExpressionNode.BinaryExpressionNode(0, 0, BinaryOperator.Assignment,
                        new ExpressionNode.IdentifierExpressionNode(0, 0, stringTable.insert("y"), false),
                        new ExpressionNode.ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, Literal.ofValue(3), false),
                    false),
                false),
                Optional.empty(),
            false)
        ),
            new ExpressionNode.IdentifierExpressionNode(0, 0, stringTable.insert("y"), false)
        ));

        ConstantOptimization optimization = new ConstantOptimization();
        optimization.optimize(graph);

        Cond cond = (Cond)getNodes(graph).stream().filter(node -> node instanceof Cond).findAny().get();
        assertTrue(cond.getSelector() instanceof Const);

        Const selector = (Const)cond.getSelector();
        assertTrue(selector.getTarval().equals(TargetValue.getBFalse()));
    }

    @Test
    public void testCmpExclTrue() {
        // 5 < 7 -> true

        StringTable stringTable = new StringTable();
        Graph graph = build(stringTable, surroundWithIO(stringTable, Arrays.asList(
            new StatementNode.LocalVariableDeclarationStatementNode(0, 0, new DataType(DataTypeClass.Int), stringTable.insert("y"), Optional.of(
                new ExpressionNode.ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, Literal.ofValue(2), false)
            ), false),
            new StatementNode.IfStatementNode(0, 0,
                new ExpressionNode.BinaryExpressionNode(0, 0, BinaryOperator.LessThan,
                    new ExpressionNode.ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, Literal.ofValue(5), false),
                    new ExpressionNode.ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, Literal.ofValue(7), false),
                false),
                new StatementNode.ExpressionStatementNode(0, 0,
                    new ExpressionNode.BinaryExpressionNode(0, 0, BinaryOperator.Assignment,
                        new ExpressionNode.IdentifierExpressionNode(0, 0, stringTable.insert("y"), false),
                        new ExpressionNode.ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, Literal.ofValue(3), false),
                    false),
                false),
                Optional.empty(),
            false)
        ),
            new ExpressionNode.IdentifierExpressionNode(0, 0, stringTable.insert("y"), false)
        ));

        ConstantOptimization optimization = new ConstantOptimization();
        optimization.optimize(graph);

        Cond cond = (Cond)getNodes(graph).stream().filter(node -> node instanceof Cond).findAny().get();
        assertTrue(cond.getSelector() instanceof Const);

        Const selector = (Const)cond.getSelector();
        assertTrue(selector.getTarval().equals(TargetValue.getBTrue()));
    }

    @Test
    public void testCmpInclFalse() {
        // 7 <= 5 -> true

        StringTable stringTable = new StringTable();
        Graph graph = build(stringTable, surroundWithIO(stringTable, Arrays.asList(
            new StatementNode.LocalVariableDeclarationStatementNode(0, 0, new DataType(DataTypeClass.Int), stringTable.insert("y"), Optional.of(
                new ExpressionNode.ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, Literal.ofValue(2), false)
            ), false),
            new StatementNode.IfStatementNode(0, 0,
                new ExpressionNode.BinaryExpressionNode(0, 0, BinaryOperator.LessThanOrEqual,
                new ExpressionNode.ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, Literal.ofValue(7), false),
                    new ExpressionNode.ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, Literal.ofValue(5), false),
                false),
                new StatementNode.ExpressionStatementNode(0, 0,
                    new ExpressionNode.BinaryExpressionNode(0, 0, BinaryOperator.Assignment,
                        new ExpressionNode.IdentifierExpressionNode(0, 0, stringTable.insert("y"), false),
                        new ExpressionNode.ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, Literal.ofValue(3), false),
                    false),
                false),
                Optional.empty(),
            false)
        ),
            new ExpressionNode.IdentifierExpressionNode(0, 0, stringTable.insert("y"), false)
        ));

        ConstantOptimization optimization = new ConstantOptimization();
        optimization.optimize(graph);

        Cond cond = (Cond)getNodes(graph).stream().filter(node -> node instanceof Cond).findAny().get();
        assertTrue(cond.getSelector() instanceof Const);

        Const selector = (Const)cond.getSelector();
        assertTrue(selector.getTarval().equals(TargetValue.getBFalse()));
    }

    @Test
    public void testCmpInclTrue() {
        // 5 <= 7 -> true

        StringTable stringTable = new StringTable();
        Graph graph = build(stringTable, surroundWithIO(stringTable, Arrays.asList(
            new StatementNode.LocalVariableDeclarationStatementNode(0, 0, new DataType(DataTypeClass.Int), stringTable.insert("y"), Optional.of(
                new ExpressionNode.ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, Literal.ofValue(2), false)
            ), false),
            new StatementNode.IfStatementNode(0, 0,
                new ExpressionNode.BinaryExpressionNode(0, 0, BinaryOperator.LessThanOrEqual,
                    new ExpressionNode.ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, Literal.ofValue(5), false),
                    new ExpressionNode.ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, Literal.ofValue(7), false),
                false),
                new StatementNode.ExpressionStatementNode(0, 0,
                    new ExpressionNode.BinaryExpressionNode(0, 0, BinaryOperator.Assignment,
                        new ExpressionNode.IdentifierExpressionNode(0, 0, stringTable.insert("y"), false),
                        new ExpressionNode.ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, Literal.ofValue(3), false),
                    false),
                false),
                Optional.empty(),
            false)
        ),
            new ExpressionNode.IdentifierExpressionNode(0, 0, stringTable.insert("y"), false)
        ));

        ConstantOptimization optimization = new ConstantOptimization();
        optimization.optimize(graph);

        Cond cond = (Cond)getNodes(graph).stream().filter(node -> node instanceof Cond).findAny().get();
        assertTrue(cond.getSelector() instanceof Const);

        Const selector = (Const)cond.getSelector();
        assertTrue(selector.getTarval().equals(TargetValue.getBTrue()));
    }

    @Test
    public void testCmpInclTrueEq() {
        // 5 <= 5 -> true

        StringTable stringTable = new StringTable();
        Graph graph = build(stringTable, surroundWithIO(stringTable, Arrays.asList(
            new StatementNode.LocalVariableDeclarationStatementNode(0, 0, new DataType(DataTypeClass.Int), stringTable.insert("y"), Optional.of(
                new ExpressionNode.ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, Literal.ofValue(2), false)
            ), false),
            new StatementNode.IfStatementNode(0, 0,
                new ExpressionNode.BinaryExpressionNode(0, 0, BinaryOperator.LessThanOrEqual,
                    new ExpressionNode.ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, Literal.ofValue(5), false),
                    new ExpressionNode.ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, Literal.ofValue(5), false),
                false),
                new StatementNode.ExpressionStatementNode(0, 0,
                    new ExpressionNode.BinaryExpressionNode(0, 0, BinaryOperator.Assignment,
                        new ExpressionNode.IdentifierExpressionNode(0, 0, stringTable.insert("y"), false),
                        new ExpressionNode.ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, Literal.ofValue(3), false),
                    false),
                false),
                Optional.empty(),
            false)
        ),
            new ExpressionNode.IdentifierExpressionNode(0, 0, stringTable.insert("y"), false)
        ));

        ConstantOptimization optimization = new ConstantOptimization();
        optimization.optimize(graph);

        Cond cond = (Cond)getNodes(graph).stream().filter(node -> node instanceof Cond).findAny().get();
        assertTrue(cond.getSelector() instanceof Const);

        Const selector = (Const)cond.getSelector();
        assertTrue(selector.getTarval().equals(TargetValue.getBTrue()));
    }

    @Test
    public void testCmpEqFalse() {
        // 5 == 7 -> false

        StringTable stringTable = new StringTable();
        Graph graph = build(stringTable, surroundWithIO(stringTable, Arrays.asList(
            new StatementNode.LocalVariableDeclarationStatementNode(0, 0, new DataType(DataTypeClass.Int), stringTable.insert("y"), Optional.of(
                new ExpressionNode.ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, Literal.ofValue(2), false)
            ), false),
            new StatementNode.IfStatementNode(0, 0,
                new ExpressionNode.BinaryExpressionNode(0, 0, BinaryOperator.Equal,
                    new ExpressionNode.ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, Literal.ofValue(5), false),
                    new ExpressionNode.ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, Literal.ofValue(7), false),
                false),
                new StatementNode.ExpressionStatementNode(0, 0,
                    new ExpressionNode.BinaryExpressionNode(0, 0, BinaryOperator.Assignment,
                        new ExpressionNode.IdentifierExpressionNode(0, 0, stringTable.insert("y"), false),
                        new ExpressionNode.ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, Literal.ofValue(3), false),
                    false),
                false),
                Optional.empty(),
            false)
        ),
            new ExpressionNode.IdentifierExpressionNode(0, 0, stringTable.insert("y"), false)
        ));

        ConstantOptimization optimization = new ConstantOptimization();
        optimization.optimize(graph);

        Cond cond = (Cond)getNodes(graph).stream().filter(node -> node instanceof Cond).findAny().get();
        assertTrue(cond.getSelector() instanceof Const);

        Const selector = (Const)cond.getSelector();
        assertTrue(selector.getTarval().equals(TargetValue.getBFalse()));
    }

    @Test
    public void testCmpEqTrue() {
        // 5 == 5 -> false

        StringTable stringTable = new StringTable();
        Graph graph = build(stringTable, surroundWithIO(stringTable, Arrays.asList(
            new StatementNode.LocalVariableDeclarationStatementNode(0, 0, new DataType(DataTypeClass.Int), stringTable.insert("y"), Optional.of(
                new ExpressionNode.ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, Literal.ofValue(2), false)
            ), false),
            new StatementNode.IfStatementNode(0, 0,
                new ExpressionNode.BinaryExpressionNode(0, 0, BinaryOperator.Equal,
                    new ExpressionNode.ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, Literal.ofValue(5), false),
                    new ExpressionNode.ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, Literal.ofValue(5), false),
                false),
                new StatementNode.ExpressionStatementNode(0, 0,
                    new ExpressionNode.BinaryExpressionNode(0, 0, BinaryOperator.Assignment,
                        new ExpressionNode.IdentifierExpressionNode(0, 0, stringTable.insert("y"), false),
                        new ExpressionNode.ValueExpressionNode(0, 0, ValueExpressionType.IntegerLiteral, Literal.ofValue(3), false),
                    false),
                false),
                Optional.empty(),
            false)
        ),
            new ExpressionNode.IdentifierExpressionNode(0, 0, stringTable.insert("y"), false)
        ));

        ConstantOptimization optimization = new ConstantOptimization();
        optimization.optimize(graph);

        Cond cond = (Cond)getNodes(graph).stream().filter(node -> node instanceof Cond).findAny().get();
        assertTrue(cond.getSelector() instanceof Const);

        Const selector = (Const)cond.getSelector();
        assertTrue(selector.getTarval().equals(TargetValue.getBTrue()));
    }

}
