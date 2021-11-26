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
import edu.kit.compiler.data.ast_nodes.MethodNode.DynamicMethodNode;
import edu.kit.compiler.data.ast_nodes.MethodNode.MethodNodeParameter;
import edu.kit.compiler.data.ast_nodes.MethodNode.StaticMethodNode;
import edu.kit.compiler.data.ast_nodes.StatementNode;
import edu.kit.compiler.data.ast_nodes.StatementNode.BlockStatementNode;

public class ParameterCounterTest {
    private static final DataType type = new DataType(DataTypeClass.Int);

    @Test
    public void testStaticNoParameters() {
        var method = staticMethod(parameters(), block());
        var mapping = ParameterCounter.apply(method);

        assertTrue(mapping.isEmpty());
    }

    @Test
    public void testDynamicNoParameters() {
        var method = dynamicMethod(parameters(), block());
        var mapping = ParameterCounter.apply(method);

        assertTrue(mapping.isEmpty());
    }

    @Test
    public void testStaticParameters() {
        var method = staticMethod(parameters(28, 42), block());
        var mapping = ParameterCounter.apply(method);

        assertEquals(Map.of(28, 0, 42, 1), mapping);
    }

    @Test
    public void testDynamicParameters() {
        var method = dynamicMethod(parameters(32, 64), block());
        var mapping = ParameterCounter.apply(method);

        assertEquals(Map.of(32, 1, 64, 2), mapping);
    }

    private static List<MethodNodeParameter> parameters(int... names) {
        return Arrays.stream(names)
            .mapToObj(name -> new MethodNodeParameter(0, 0, type, name, false))
            .collect(Collectors.toList());
    }

    private static DynamicMethodNode dynamicMethod(List<MethodNodeParameter> parameters, BlockStatementNode block) {
        return new DynamicMethodNode(0, 0, type, 0, parameters, Optional.empty(), block, false);
    }

    private static StaticMethodNode staticMethod(List<MethodNodeParameter> parameters, BlockStatementNode block) {
        return new StaticMethodNode(0, 0, type, 0, parameters, Optional.empty(), block, false);
    }

    private static BlockStatementNode block(StatementNode... statements) {
        return new BlockStatementNode(0, 0, Arrays.asList(statements), false);
    }
}
