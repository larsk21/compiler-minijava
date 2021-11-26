package edu.kit.compiler.transform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import edu.kit.compiler.data.DataType;
import edu.kit.compiler.data.DataType.DataTypeClass;
import edu.kit.compiler.data.ast_nodes.ExpressionNode.IdentifierExpressionNode;
import edu.kit.compiler.data.ast_nodes.MethodNode.DynamicMethodNode;
import edu.kit.compiler.data.ast_nodes.MethodNode.MethodNodeParameter;
import edu.kit.compiler.data.ast_nodes.MethodNode.StaticMethodNode;
import edu.kit.compiler.data.ast_nodes.StatementNode;
import edu.kit.compiler.data.ast_nodes.StatementNode.BlockStatementNode;
import edu.kit.compiler.data.ast_nodes.StatementNode.ExpressionStatementNode;
import edu.kit.compiler.data.ast_nodes.StatementNode.IfStatementNode;
import edu.kit.compiler.data.ast_nodes.StatementNode.LocalVariableDeclarationStatementNode;
import edu.kit.compiler.data.ast_nodes.StatementNode.ReturnStatementNode;
import edu.kit.compiler.data.ast_nodes.StatementNode.WhileStatementNode;

public class LocalVariableCounterTest {
    private static final DataType type = new DataType(DataTypeClass.Int);

    @Test
    public void testNoLocalVariables() {
        var method = dynamicMethod(parameters(), block());
        var mapping = LocalVariableCounter.apply(method);
        
        assertTrue(mapping.isEmpty());
    }

    @Test
    public void testBasicLocalVariables() {
        var method = dynamicMethod(parameters(), block(variable(42), variable(28)));
        var mapping = LocalVariableCounter.apply(method);
        
        assertEquals(2, mapping.size());
        assertEquals(Map.of(42, 0, 28, 1), mapping);
    }

    @Test
    public void testBasicLocalVariablesStaticMethod() {
        var method = staticMethod(parameters(), block(variable(42), variable(28)));
        var mapping = LocalVariableCounter.apply(method);
        
        assertEquals(2, mapping.size());
        assertEquals(Map.of(42, 0, 28, 1), mapping);
    }

    @Test
    public void testNestedBlocks() {
        var method = dynamicMethod(parameters(), block(
            variable(42), block(variable(28), variable(27))
        ));
        var mapping = LocalVariableCounter.apply(method);
        
        assertEquals(3, mapping.size());
        assertEquals(Map.of(42, 0, 28, 1, 27, 2), mapping);
    }

    @Test
    public void testExpressionStatement() {
        var expr = new ExpressionStatementNode(0, 0, new IdentifierExpressionNode(0, 0, 42, false), false);
        var method = dynamicMethod(parameters(), block(
            variable(42), expr, variable(28)
        ));
        var mapping = LocalVariableCounter.apply(method);
        
        assertEquals(2, mapping.size());
        assertEquals(Map.of(42, 0, 28, 1), mapping);
    }

    @Test
    public void testReturnStatement() {
        var return_ = new ReturnStatementNode(0, 0, Optional.empty(), false);
        var method = dynamicMethod(parameters(), block(
            variable(12), variable(23), return_
        ));
        var mapping = LocalVariableCounter.apply(method);

        assertEquals(2, mapping.size());
        assertEquals(Map.of(12, 0, 23, 1), mapping);
    }

    @Test
    public void testManyDeclarations() {
        var method = dynamicMethod(parameters(), block(
            variable(11), variable(22), variable(33), variable(44), variable(55)
        ));
        var mapping = LocalVariableCounter.apply(method);

        assertEquals(5, mapping.size());
        assertEquals(Map.of(11, 0, 22, 1, 33, 2, 44, 3, 55, 4), mapping);
    }

    @Test
    public void testWhileStatement() {
        var method = dynamicMethod(parameters(), block(
            variable(0), while_(block(variable(1)))
        ));
        var mapping = LocalVariableCounter.apply(method);

        assertEquals(2, mapping.size());
        assertEquals(Map.of(0, 0, 1, 1), mapping);
    }

    @Test
    public void testWhileNoBlockStatement() {
        var method = dynamicMethod(parameters(), block(
            while_(variable(0)), variable(1)
        ));
        var mapping = LocalVariableCounter.apply(method);
        
        assertEquals(2, mapping.size());
        assertEquals(Map.of(0, 0, 1, 1), mapping);
    }

    @Test
    public void testDeclarationAfterBlockStatement() {
        var method = dynamicMethod(parameters(), 
            block(variable(0), block(variable(1), variable(2)), variable(3)));
        var mapping = LocalVariableCounter.apply(method);

        assertEquals(4, mapping.size());
        assertEquals(Map.of(0, 0, 1, 1, 2, 2, 3, 3), mapping);
    }

    @Test
    public void testMultipleBlockStatement() {
        var method = dynamicMethod(parameters(), block(
            variable(0),
            block(variable(1)),
            variable(2),
            block(variable(3), variable(4)),
            variable(5)
        ));
        var mapping = LocalVariableCounter.apply(method);

        assertEquals(6, mapping.size());
        assertEquals(Map.of(0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5), mapping);
    }

    @Test
    public void testIfStatement() {
        var method = dynamicMethod(parameters(), block(if_(
            block(variable(0), variable(1)),
            block(variable(2), variable(3))
        )));
        var mapping = LocalVariableCounter.apply(method);

        assertEquals(4, mapping.size());
        assertEquals(Map.of(0, 0, 1, 1, 2, 2, 3, 3), mapping);
    }

    @Test
    public void testRedeclareVariable() {
        var method = dynamicMethod(parameters(), block(
            block(variable(0)), variable(0)
        ));
        var mapping = LocalVariableCounter.apply(method);

        assertEquals(1, mapping.size());
        assertEquals(Map.of(0, 0), mapping);
    }

    @Test
    public void testRedeclareIfElse() {
        var method = dynamicMethod(parameters(), block(if_(
            variable(0), variable(0)
        )));
        var mapping = LocalVariableCounter.apply(method);

        assertEquals(1, mapping.size());
        assertEquals(Map.of(0, 0), mapping);
    }

    @Test
    public void testRedeclareIfElseBlock() {
        var method = dynamicMethod(parameters(), block(if_(
            variable(0), block(variable(1), variable(0))
        )));
        var mapping = LocalVariableCounter.apply(method);

        assertEquals(2, mapping.size());
        assertEquals(Map.of(0, 0, 1, 1), mapping);
    }

    private static DynamicMethodNode dynamicMethod(List<MethodNodeParameter> parameters, BlockStatementNode block) {
        return new DynamicMethodNode(0, 0, type, 0, parameters, Optional.empty(), block, false);
    }

    private static StaticMethodNode staticMethod(List<MethodNodeParameter> parameters, BlockStatementNode block) {
        return new StaticMethodNode(0, 0, type, 0, parameters, Optional.empty(), block, false);
    }

    private static List<MethodNodeParameter> parameters(int... names) {
        return Arrays.stream(names)
            .mapToObj(name -> new MethodNodeParameter(0, 0, type, name, false))
            .collect(Collectors.toList());
    }

    private static BlockStatementNode block(StatementNode... statements) {
        return new BlockStatementNode(0, 0, Arrays.asList(statements), false);
    }

    private static StatementNode while_(StatementNode statement) {
        var expr = new IdentifierExpressionNode(0, 0, 0, false);
        return new WhileStatementNode(0, 0, expr, statement, false);
    }

    private static StatementNode if_(StatementNode then, StatementNode else_) {
        var expr = new IdentifierExpressionNode(0, 0, 0, false);
        return new IfStatementNode(0, 0, expr, then, Optional.ofNullable(else_), false);
    }

    private static StatementNode variable(int name) {
        return new LocalVariableDeclarationStatementNode(
            0, 0, type, name, Optional.empty(), false);
    }
}
