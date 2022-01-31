package edu.kit.compiler.optimizations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.kit.compiler.lexer.Lexer;
import edu.kit.compiler.logger.Logger;
import edu.kit.compiler.optimizations.unrolling.LoopUnrollingOptimization;
import edu.kit.compiler.parser.Parser;
import edu.kit.compiler.semantic.DetailedNameTypeAstVisitor;
import edu.kit.compiler.semantic.ErrorHandler;
import edu.kit.compiler.semantic.NamespaceGatheringVisitor;
import edu.kit.compiler.semantic.NamespaceMapper;
import edu.kit.compiler.semantic.SemanticChecks;
import edu.kit.compiler.transform.IRVisitor;
import edu.kit.compiler.transform.JFirmSingleton;
import edu.kit.compiler.transform.Lower;
import firm.Graph;
import firm.Program;
import firm.bindings.binding_irnode.ir_opcode;
import firm.nodes.Const;
import firm.nodes.Node;
import firm.nodes.NodeVisitor;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

public class LoopUnrollingTest {

    private static final String PROGRAM = "class Main_%s { public static void main(String[] args) { } %s }";
    private static final String FUNCTION = "public int func() { int i = %s; while (i %s %s) { i = i + %s; } return i; }";

    private Optimization.Local unroll = new LoopUnrollingOptimization();
    private Optimization.Local linearBlocks = new LinearBlocksOptimization();
    private Optimization.Local constants = new ConstantOptimization();
    private Optimization.Local identities = new ArithmeticIdentitiesOptimization();
    private Set<Graph> oldGraphs = new HashSet<>();

    @BeforeAll
    public static void setupAll() {
        JFirmSingleton.initializeFirmLinux();
    }

    @BeforeEach
    public void setup() {
        collectGraphs(oldGraphs);
    }

    @AfterEach
    public void teardown() {
        getNewGraphs().forEach(graph -> {
            var entity = graph.getEntity();
            graph.free();
            entity.free();
        });
    }

    @Test
    public void testRangeLt1() {
        assertFullyUnrolled(createWhile(0, 10, 1, "<"), 10);
    }

    @Test
    public void testRangeLt2() {
        assertFullyUnrolled(createWhile(-10, 10, 2, "<"), 10);
    }

    @Test
    public void testRangeLt3() {
        assertFullyUnrolled(createWhile(28, 10, 2, "<"), 28);
    }

    @Test
    public void testRangeLe1() {
        assertFullyUnrolled(createWhile(0, 10, 5, "<="), 15);
    }

    @Test
    public void testRangeLe2() {
        assertFullyUnrolled(createWhile(-15, 1, 3, "<="), 3);
    }

    @Test
    public void testRangeLe3() {
        assertFullyUnrolled(createWhile(10, 9, 3, "<="), 10);
    }

    @Test
    public void testRangeGt1() {
        assertFullyUnrolled(createWhile(10, 0, -1, ">"), 0);
    }

    @Test
    public void testRangeGt2() {
        assertFullyUnrolled(createWhile(96, 0, -3, ">"), 0);
    }

    @Test
    public void testRangeGt3() {
        assertFullyUnrolled(createWhile(1000, 0, -5, ">"), 0);
    }

    @Test
    public void testRangeGt4() {
        assertFullyUnrolled(createWhile(1337, 1337, -5, ">"), 1337);
    }

    @Test
    public void testRangeGe1() {
        assertFullyUnrolled(createWhile(10, 0, -5, ">="), -5);
    }

    @Test
    public void testRangeGe2() {
        assertFullyUnrolled(createWhile(999, 0, -1, ">="), -1);
    }

    @Test
    public void testRangeGe3() {
        assertFullyUnrolled(createWhile(-5, -4, -1, ">="), -5);
    }
 
    @Test
    public void testRangeEq1() {
        assertFullyUnrolled(createWhile(42, 42, 1, "=="), 43);
    }
 
    @Test
    public void testRangeEq2() {
        assertFullyUnrolled(createWhile(28, 42, 1, "=="), 28);
    }
 
    @Test
    public void testRangeNeq1() {
        assertFullyUnrolled(createWhile(0, 42, 1, "!="), 42);
    }
 
    @Test
    public void testRangeNeq2() {
        assertNotUnrolled(createWhile(0, 41, 2, "!="), 42);
    }
 
    @Test
    public void testRangeNeq3() {
        assertFullyUnrolled(createWhile(10, 0, -1, "!="), 0);
    }
 
    @Test
    public void testRangeNeq4() {
        assertFullyUnrolled(createWhile(42, 42, -1, "!="), 42);
    }

    private void assertFullyUnrolled(Graph graph, int result) {
        assertEquals(1, Counter.count(graph, ir_opcode.iro_Return));
        assertEquals(1, Counter.count(graph, ir_opcode.iro_Const));
        assertEquals(0, Counter.count(graph, ir_opcode.iro_Add));
        assertEquals(0, Counter.count(graph, ir_opcode.iro_Jmp));
        assertEquals(0, Counter.count(graph, ir_opcode.iro_Cond));

        var constNode = Counter.getOnly(graph, ir_opcode.iro_Const);
        assertEquals(result, ((Const) constNode).getTarval().asInt());
    }

    private void assertNotUnrolled(Graph graph, int result) {
        assertEquals(1, Counter.count(graph, ir_opcode.iro_Return));
        assertEquals(1, Counter.count(graph, ir_opcode.iro_Add));
        assertNotEquals(0, Counter.count(graph, ir_opcode.iro_Jmp));
        assertNotEquals(0, Counter.count(graph, ir_opcode.iro_Cond));
    }

    private Graph createWhile(int begin, int end, int step, String relation) {
        buildOptIR(String.format(FUNCTION, begin, relation, end, step));
        return getFunction("func");
    }

    private Graph getFunction(String name) {
        for (var graph : getNewGraphs()) {
            if (graph.getEntity().getLdName().contains(name)) {
                return graph;
            }
        }
        throw new IllegalStateException();
    }

    private Collection<Graph> getNewGraphs() {
        var newGraphs = new HashSet<Graph>();
        collectGraphs(newGraphs);
        newGraphs.removeAll(oldGraphs);
        return newGraphs;
    }

    private void collectGraphs(Collection<Graph> collection) {
        Program.getGraphs().forEach(collection::add);
    }

    private void buildOptIR(String function) {
        var logger = Logger.nullLogger();
        var errorHandler = new ErrorHandler(logger);

        var uuid = UUID.randomUUID().toString().replace("-", "_");
        var lexer = new Lexer(new StringReader(String.format(PROGRAM, uuid, function)));

        var stringTable = lexer.getStringTable();
        var parser = new Parser(lexer);
        var ast = parser.parse();
        var namespaceMapper = new NamespaceMapper();
        var gatheringVisitor = new NamespaceGatheringVisitor(namespaceMapper, stringTable, errorHandler);
        ast.accept(gatheringVisitor);
        var nameTypeVisitor = new DetailedNameTypeAstVisitor(namespaceMapper, stringTable, errorHandler);
        ast.accept(nameTypeVisitor);
        SemanticChecks.applyChecks(ast, errorHandler, gatheringVisitor.getStringClass());
        errorHandler.checkForErrors();
        var irVisitor = new IRVisitor(namespaceMapper, stringTable);
        ast.accept(irVisitor);
        Lower.lower(irVisitor.getTypeMapper());

        var state = new OptimizationState();

        for (var graph : getNewGraphs()) {
            boolean change;
            do {
                change = unroll.optimize(graph, state);
                linearBlocks.optimize(graph, state);
                constants.optimize(graph, state);
                identities.optimize(graph, state);
            } while (change);
        }
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private static final class Counter extends NodeVisitor.Default {

        private final ir_opcode opcode;

        private List<Node> buffer = new LinkedList<>();

        public static int count(Graph graph, ir_opcode opcode) {
            var visitor = new Counter(opcode);
            graph.walk(visitor);
            return visitor.buffer.size();
        }

        public static Node getOnly(Graph graph, ir_opcode opcode) {
            var visitor = new Counter(opcode);
            graph.walk(visitor);

            assertTrue(visitor.buffer.size() == 1);
            return visitor.buffer.get(0);
        }

        @Override
        public void defaultVisit(Node node) {
            if (node.getOpCode() == opcode) {
                buffer.add(node);
            }
        }
    }
    
}
