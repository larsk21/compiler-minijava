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
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.kit.compiler.lexer.Lexer;
import edu.kit.compiler.logger.Logger;
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
import firm.bindings.binding_irnode;
import firm.bindings.binding_irnode.ir_opcode;
import firm.nodes.Call;
import firm.nodes.Node;
import firm.nodes.NodeVisitor;
import firm.nodes.Proj;
import firm.nodes.Return;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

public class PureFunctionOptimizationTest {

    private static final String TEMPLATE = "class Main_%s { public static void main(String[] args) { } %s }";

    private PureFunctionOptimization optimization = new PureFunctionOptimization();
    private Set<Graph> oldGraphs = new HashSet<>();
    private Collection<String> members = new LinkedList<>();

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
    public void testCallConst() {
        addIntToInt("foo", 1, "return x0;");
        addIntToInt("bar", 1, "return foo(x0);");
        buildOptIR();


        var bar = getFunction("bar");
        assertEquals(1, Counter.count(bar, ir_opcode.iro_Call));

        var call = (Call) Counter.getOnly(bar, ir_opcode.iro_Call);
        assertEquals(bar.getNoMem(), call.getMem());
        assertEquals(0, binding_irnode.get_irn_pinned(call.ptr));

        var ret = (Return) Counter.getOnly(bar, ir_opcode.iro_Return);
        assertEquals(ret.getMem(), bar.getInitialMem());
    }

    @Test
    public void testCallConstUnused() {
        addIntToInt("foo", 1, "return x0;");
        addIntToInt("bar", 1, "int x = foo(x0); return 0;");
        buildOptIR();


        var bar = getFunction("bar");
        assertEquals(0, Counter.count(bar, ir_opcode.iro_Call));

        var ret = (Return) Counter.getOnly(bar, ir_opcode.iro_Return);
        assertEquals(ret.getMem(), bar.getInitialMem());
    }

    @Test
    public void testCallConstLoop() {
        addIntToInt("foo", 1, "return x0 + 1;");
        addIntToInt("bar", 1, "while (x0 < 0) x0 = foo(x0); return x0;");
        buildOptIR();


        var bar = getFunction("bar");
        assertEquals(1, Counter.count(bar, ir_opcode.iro_Call));

        var call = (Call) Counter.getOnly(bar, ir_opcode.iro_Call);
        assertEquals(bar.getNoMem(), call.getMem());
        assertEquals(0, binding_irnode.get_irn_pinned(call.ptr));
    }

    @Test
    public void testCallPure() {
        addIntField("field");
        addIntToInt("foo", 0, "return this.field;");
        addIntToInt("bar", 0, "return foo();");
        buildOptIR();

        var bar = getFunction("bar");
        assertEquals(1, Counter.count(bar, ir_opcode.iro_Call));

        var call = (Call) Counter.getOnly(bar, ir_opcode.iro_Call);
        assertEquals(bar.getInitialMem(), call.getMem());
        assertNotEquals(0, binding_irnode.get_irn_pinned(call.ptr));
    }

    private void addIntField(String name) {
        members.add(String.format("public int %s;", name));
    }

    private void addIntToInt(String name, int numParams, String body) {
        var params = IntStream.range(0, numParams).mapToObj(n -> "int x" + n).collect(Collectors.joining(", "));
        members.add(String.format("public int %s(%s) { %s }", name, params, body));
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

    private void buildOptIR() {
        var logger = Logger.nullLogger();
        var errorHandler = new ErrorHandler(logger);

        var uuid = UUID.randomUUID().toString().replace("-", "_");
        var functions = this.members.stream().collect(Collectors.joining(" "));
        var lexer = new Lexer(new StringReader(String.format(TEMPLATE, uuid, functions)));

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

        var graphs = getNewGraphs();
        var state = new OptimizationState();
        graphs.forEach(graph -> optimization.optimize(graph, state));

        graphs.forEach(graph -> {
            graph.walkPostorder(new NodeVisitor.Default() {
                public void visit(Proj node) {
                    Util.skipTuple(node);
                }
            });
        });
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

        // public static List<Node> collect(Graph graph, ir_opcode opcode)  {
        //     var visitor = new Counter(opcode);
        //     graph.walk(visitor);
        //     return visitor.buffer;
        // }

        public static Node getOnly(Graph graph, ir_opcode opcode)  {
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

