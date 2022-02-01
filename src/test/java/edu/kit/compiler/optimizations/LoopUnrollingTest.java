package edu.kit.compiler.optimizations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.kit.compiler.lexer.Lexer;
import edu.kit.compiler.logger.Logger;
import edu.kit.compiler.optimizations.unrolling.LoopUnrollingOptimization;
import edu.kit.compiler.optimizations.unrolling.LoopVariableAnalysis.FixedIterationLoop;
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
import firm.Mode;
import firm.Program;
import firm.Relation;
import firm.TargetValue;
import firm.bindings.binding_irnode.ir_opcode;
import firm.nodes.Const;
import firm.nodes.Node;
import firm.nodes.NodeVisitor;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

public class LoopUnrollingTest {

    private static final long ABS_INT_MIN = Math.abs((long) Integer.MIN_VALUE);
    private static final long INT_MAX = Math.abs((long) Integer.MAX_VALUE);

    private static final String PROGRAM = "class Main_%s { public static void main(String[] args) { } %s }";
    private static final String FUNCTION = "public int func() { %s }";

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
    public void testIterationsLt() {
        assertEquals(Optional.of(10), intIterations(0, 10, 1, Relation.Less));
        assertEquals(Optional.of(5), intIterations(0, 10, 2, Relation.Less));
        assertEquals(Optional.of(0), intIterations(100, 100, 1, Relation.Less));
        assertEquals(Optional.of(0), intIterations(100, 0, 1, Relation.Less));
        assertEquals(Optional.of(10), intIterations(10, 60, 5, Relation.Less));
        assertEquals(Optional.of(5), intIterations(-10, 10, 4, Relation.Less));

        assertEquals(Optional.empty(), intIterations(0, 10, 0, Relation.Less));
        assertEquals(Optional.empty(), intIterations(0, 10, -1, Relation.Less));

        assertEquals(Optional.of(ABS_INT_MIN), longIterations(Integer.MIN_VALUE, 0, 1, Relation.Less));
        assertEquals(Optional.of(INT_MAX), longIterations(0, Integer.MAX_VALUE, 1, Relation.Less));
        assertEquals(Optional.of(ABS_INT_MIN + INT_MAX),
                longIterations(Integer.MIN_VALUE, Integer.MAX_VALUE, 1, Relation.Less));
    }

    @Test
    public void testIterationsLe() {
        assertEquals(Optional.of(11), intIterations(0, 10, 1, Relation.LessEqual));
        assertEquals(Optional.of(6), intIterations(0, 10, 2, Relation.LessEqual));
        assertEquals(Optional.of(1), intIterations(100, 100, 1, Relation.LessEqual));
        assertEquals(Optional.of(0), intIterations(100, 0, 1, Relation.LessEqual));
        assertEquals(Optional.of(11), intIterations(10, 60, 5, Relation.LessEqual));
        assertEquals(Optional.of(6), intIterations(-10, 10, 4, Relation.LessEqual));
        assertEquals(Optional.of(3), intIterations(0, 10, 4, Relation.LessEqual));

        assertEquals(Optional.empty(), intIterations(0, 10, 0, Relation.LessEqual));
        assertEquals(Optional.empty(), intIterations(0, 10, -1, Relation.LessEqual));

        var ABS_INT_MIN = Math.abs((long) Integer.MIN_VALUE);
        assertEquals(Optional.of(ABS_INT_MIN + 1), longIterations(Integer.MIN_VALUE, 0, 1, Relation.LessEqual));
        assertEquals(Optional.empty(), longIterations(0, Integer.MAX_VALUE, 1, Relation.LessEqual));
        assertEquals(Optional.empty(), intIterations(Integer.MIN_VALUE, Integer.MAX_VALUE, 1, Relation.LessEqual));
    }

    @Test
    public void testIterationsGt() {
        assertEquals(Optional.of(10), intIterations(10, 0, -1, Relation.Greater));
        assertEquals(Optional.of(5), intIterations(10, 0, -2, Relation.Greater));
        assertEquals(Optional.of(0), intIterations(100, 100, -1, Relation.Greater));
        assertEquals(Optional.of(0), intIterations(0, 100, -1, Relation.Greater));
        assertEquals(Optional.of(10), intIterations(60, 10, -5, Relation.Greater));
        assertEquals(Optional.of(5), intIterations(10, -10, -4, Relation.Greater));

        assertEquals(Optional.empty(), intIterations(10, 0, 0, Relation.Greater));
        assertEquals(Optional.empty(), intIterations(10, 0, 1, Relation.Greater));

        var ABS_INT_MIN = Math.abs((long) Integer.MIN_VALUE);
        var INT_MAX = Math.abs((long) Integer.MAX_VALUE);
        assertEquals(Optional.of(ABS_INT_MIN), longIterations(0, Integer.MIN_VALUE, -1, Relation.Greater));
        assertEquals(Optional.of(INT_MAX), longIterations(Integer.MAX_VALUE, 0, -1, Relation.Greater));
        assertEquals(Optional.of(ABS_INT_MIN + INT_MAX),
                longIterations(Integer.MAX_VALUE, Integer.MIN_VALUE, -1, Relation.Greater));
        assertEquals(Optional.of(0), intIterations(Integer.MIN_VALUE, Integer.MAX_VALUE, -1, Relation.Greater));
    }

    @Test
    public void testIterationsGe() {
        assertEquals(Optional.of(11), intIterations(10, 0, -1, Relation.GreaterEqual));
        assertEquals(Optional.of(6), intIterations(10, 0, -2, Relation.GreaterEqual));
        assertEquals(Optional.of(1), intIterations(100, 100, -1, Relation.GreaterEqual));
        assertEquals(Optional.of(0), intIterations(0, 100, -1, Relation.GreaterEqual));
        assertEquals(Optional.of(11), intIterations(60, 10, -5, Relation.GreaterEqual));
        assertEquals(Optional.of(6), intIterations(10, -10, -4, Relation.GreaterEqual));
        assertEquals(Optional.of(3), intIterations(10, 0, -4, Relation.GreaterEqual));

        assertEquals(Optional.empty(), intIterations(10, 0, 0, Relation.GreaterEqual));
        assertEquals(Optional.empty(), intIterations(10, 0, 1, Relation.GreaterEqual));

        var ABS_INT_MIN = Math.abs((long) Integer.MIN_VALUE);
        assertEquals(Optional.empty(), longIterations(0, Integer.MIN_VALUE, -1, Relation.GreaterEqual));
        assertEquals(Optional.of(ABS_INT_MIN), longIterations(Integer.MAX_VALUE, 0, -1, Relation.GreaterEqual));
        assertEquals(Optional.empty(), longIterations(Integer.MAX_VALUE, Integer.MIN_VALUE, -1, Relation.GreaterEqual));
        assertEquals(Optional.of(0), intIterations(Integer.MIN_VALUE, Integer.MAX_VALUE, -1, Relation.GreaterEqual));
    }

    @Test
    public void testIterationsEq() {
        assertEquals(Optional.of(1), intIterations(-128, -128, 10, Relation.Equal));
        assertEquals(Optional.of(0), intIterations(42, 41, 1, Relation.Equal));
        assertEquals(Optional.of(0), intIterations(41, 42, -1, Relation.Equal));
        assertEquals(Optional.empty(), intIterations(28, 28, 0, Relation.Equal));
        assertEquals(Optional.of(0), intIterations(42, 41, 0, Relation.Equal));
    }

    @Test
    public void testIterationsNeq() {
        assertEquals(Optional.of(0), intIterations(42, 42, 1, Relation.LessGreater));
        assertEquals(Optional.of(0), intIterations(42, 42, 0, Relation.LessGreater));
        assertEquals(Optional.of(1), intIterations(41, 42, 1, Relation.LessGreater));
        assertEquals(Optional.empty(), intIterations(41, 42, 0, Relation.LessGreater));
        assertEquals(Optional.of(256), intIterations(-128, 128, 1, Relation.LessGreater));
        assertEquals(Optional.of(ABS_INT_MIN + INT_MAX),
                longIterations(Integer.MIN_VALUE, Integer.MAX_VALUE, 1, Relation.LessGreater));
        assertEquals(Optional.empty(), longIterations(Integer.MIN_VALUE, Integer.MAX_VALUE, -1, Relation.LessGreater));
        assertEquals(Optional.empty(), longIterations(Integer.MAX_VALUE, Integer.MIN_VALUE, 1, Relation.LessGreater));
        assertEquals(Optional.of(ABS_INT_MIN + INT_MAX),
                longIterations(Integer.MAX_VALUE, Integer.MIN_VALUE, -1, Relation.LessGreater));
    }

    @Test
    public void testUnroll() {
        assertFullyUnrolled(createFunction("int i = 0; while (i < 10) i = i + 1; return i;"), 10);
    }

    @Test
    public void testUnrollMedium() {
        assertFullyUnrolled(createFunction("int i = 0; while (i < 128) i = i + 1; return i;"), 128);
    }

    @Test
    public void testUnrollLarge() {
        assertFullyUnrolled(createFunction("int i = 0; while (i < 4096) i = i + 1; return i;"), 4096);
    }

    @Test
    public void testUnrollPrime() {
        assertNotUnrolled(createFunction("int i = 0; while (i < 73) i = i + 1; return i;"));
    }

    @Test
    public void testUnrollCountTo100() {
        assertFullyUnrolled(
                createFunction("int i = 1; int sum = 0; while (i <= 100) { sum = sum + i; i = i + 1; } return sum;"),
                5050);
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

    private void assertNotUnrolled(Graph graph) {
        assertEquals(1, Counter.count(graph, ir_opcode.iro_Return));
        assertEquals(1, Counter.count(graph, ir_opcode.iro_Add));
        assertNotEquals(0, Counter.count(graph, ir_opcode.iro_Jmp));
        assertNotEquals(0, Counter.count(graph, ir_opcode.iro_Cond));
    }

    private static Optional<Integer> intIterations(int initial, int bound, int step, Relation relation) {
        var desc = new FixedIterationLoop(
                new TargetValue(initial, Mode.getIs()),
                new TargetValue(bound, Mode.getIs()),
                new TargetValue(step, Mode.getIs()),
                relation);
        return desc.getIterationCount().map(Long::intValue);
    }

    private static Optional<Long> longIterations(int initial, int bound, int step, Relation relation) {
        var desc = new FixedIterationLoop(
                new TargetValue(initial, Mode.getIs()),
                new TargetValue(bound, Mode.getIs()),
                new TargetValue(step, Mode.getIs()),
                relation);
        return desc.getIterationCount();
    }

    private Graph createFunction(String body) {
        buildOptIR(String.format(FUNCTION, body));
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
