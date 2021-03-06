package edu.kit.compiler.optimizations;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
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
import edu.kit.compiler.optimizations.attributes.AttributeAnalysis;
import edu.kit.compiler.optimizations.attributes.Attributes;
import edu.kit.compiler.optimizations.attributes.Attributes.Purity;
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

public class AttributeAnalysisTest {

    private static final String TEMPLATE = "class %s { public static void main(String[] args) { } %s }";

    private Set<Graph> oldGraphs = new HashSet<>();
    private Collection<String> members = new LinkedList<>();
    private AttributeAnalysis analysis;
    private String className;

    @BeforeAll
    public static void setupAll() {
        JFirmSingleton.initializeFirmLinux();
    }

    @BeforeEach
    public void setup() {
        collectGraphs(oldGraphs);

        analysis = new AttributeAnalysis();
        className = "Main_" + UUID.randomUUID().toString().replace("-", "_");
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
    public void testSimpleConstFunction() {
        addIntToInt("func", 3, "return x0 + x1 * x2;");
        buildIR();

        var func = getAttributes("func");
        assertTrue(func.isConst());
        assertTrue(func.isPure());
        assertTrue(func.isTerminates());
        assertSame(Purity.CONST, func.getPurity());
        assertFalse(func.isMalloc());
    }

    @Test
    public void testBranchConstFunction() {
        addIntToInt("func", 3, "if (x0 < 0) return x1; else return x2;");
        buildIR();

        var func = getAttributes("func");
        assertTrue(func.isConst());
        assertTrue(func.isPure());
        assertTrue(func.isTerminates());
        assertSame(Purity.CONST, func.getPurity());
        assertFalse(func.isMalloc());
    }

    @Test
    public void testFunctionWithLoad() {
        addIntField("field");
        addIntToInt("func", 0, "return this.field;");
        buildIR();

        var func = getAttributes("func");
        assertFalse(func.isConst());
        assertTrue(func.isPure());
        assertTrue(func.isTerminates());
        assertSame(Purity.PURE, func.getPurity());
        assertFalse(func.isMalloc());
    }

    @Test
    public void testFunctionWithStore() {
        addIntField("field");
        addIntToVoid("func", 1, "this.field = x0;");
        buildIR();

        var func = getAttributes("func");
        assertFalse(func.isConst());
        assertFalse(func.isPure());
        assertTrue(func.isTerminates());
        assertSame(Purity.IMPURE, func.getPurity());
        assertFalse(func.isMalloc());
    }

    @Test
    public void testFunctionWithLoadStore() {
        addIntField("field1");
        addIntField("field2");
        addIntToVoid("func", 1, "this.field1 = this.field2;");
        buildIR();

        var func = getAttributes("func");
        assertFalse(func.isConst());
        assertFalse(func.isPure());
        assertTrue(func.isTerminates());
        assertSame(Purity.IMPURE, func.getPurity());
        assertFalse(func.isMalloc());
    }

    @Test
    public void testDivFunction() {
        addIntToInt("func", 2, "return x0 / x1;");
        buildIR();

        var func = getAttributes("func");
        assertFalse(func.isConst());
        assertFalse(func.isPure());
        assertFalse(func.isTerminates());
        assertFalse(func.isMalloc());
    }

    @Test
    public void testModFunction() {
        addIntToInt("func", 2, "return x0 % x1;");
        buildIR();

        var func = getAttributes("func");
        assertFalse(func.isConst());
        assertFalse(func.isPure());
        assertFalse(func.isTerminates());
        assertFalse(func.isMalloc());
    }

    @Test
    public void testFunctionWithInfiniteLoop() {
        addIntToInt("func", 2, "while (x0 == x0); return x1;");
        buildIR();

        var func = getAttributes("func");
        assertTrue(func.isConst());
        assertTrue(func.isPure());
        assertFalse(func.isTerminates());
        assertFalse(func.isMalloc());
    }

    @Test
    public void testWhileTrue() {
        addIntToVoid("func", 0, "while (true);");
        buildIR();

        var func = getAttributes("func");
        assertTrue(func.isConst());
        assertTrue(func.isPure());
        assertFalse(func.isTerminates());
        assertFalse(func.isMalloc());
    }

    @Test
    public void testFunctionWithFiniteLoop() {
        addIntToInt("func", 1, "int i = 0; while (i < 10) i = i + 1; return x0;");
        buildIR();

        var func = getAttributes("func");
        assertTrue(func.isConst());
        assertTrue(func.isPure());
        assertFalse(func.isTerminates());
        assertFalse(func.isMalloc());
    }

    @Test
    public void testBranchLoad() {
        addIntField("field");
        addIntToInt("func", 1, "int res; if (x0 < 0) res = this.field; else res = this.field; return res;");
        buildIR();

        var func = getAttributes("func");
        assertFalse(func.isConst());
        assertTrue(func.isPure());
        assertTrue(func.isTerminates());
        assertFalse(func.isMalloc());
    }

    @Test
    public void testBranchStore() {
        addIntField("field");
        addIntToInt("func", 3, "if (x0 < 0) this.field = x1; else this.field = x2; return this.field;");
        buildIR();

        var func = getAttributes("func");
        assertFalse(func.isConst());
        assertFalse(func.isPure());
        assertTrue(func.isTerminates());
        assertFalse(func.isMalloc());
    }

    @Test
    public void testLoopLoad() {
        addIntField("field");
        addIntToVoid("func", 0, "while (this.field < 0);");
        buildIR();

        var func = getAttributes("func");
        assertFalse(func.isConst());
        assertTrue(func.isPure());
        assertFalse(func.isTerminates());
        assertFalse(func.isMalloc());
    }

    @Test
    public void testLoopStore() {
        addIntField("field");
        addIntToVoid("func", 0, "while (this.field < 0) this.field = this.field + 1;");
        buildIR();

        var func = getAttributes("func");
        assertFalse(func.isConst());
        assertFalse(func.isPure());
        assertFalse(func.isTerminates());
        assertFalse(func.isMalloc());
    }

    @Test
    public void testRecursionConst() {
        addIntToInt("func", 0, "return func();");
        buildIR();

        var func = getAttributes("func");
        assertTrue(func.isConst());
        assertTrue(func.isPure());
        assertFalse(func.isTerminates());
        assertFalse(func.isMalloc());
    }

    @Test
    public void testRecursionPure() {
        addIntField("field");
        addIntToInt("func", 0, "func(); return this.field;");
        buildIR();

        var func = getAttributes("func");
        assertFalse(func.isConst());
        assertTrue(func.isPure());
        assertFalse(func.isTerminates());
        assertFalse(func.isMalloc());
    }

    @Test
    public void testRecursionImpure() {
        addIntField("field");
        addIntToInt("func", 1, "this.field = x0; return func(x0);");
        buildIR();

        var func = getAttributes("func");
        assertFalse(func.isConst());
        assertFalse(func.isPure());
        assertFalse(func.isTerminates());
        assertFalse(func.isMalloc());
    }

    @Test
    public void testComplexLoop() {
        addIntToVoid("func", 2,
                "while (x0 < 0) { if (x0 + 5 > 0) { if (x0 + 5 < 42) x1 = x0 * x1; } else { if (x0 > 42 && x1 -42 > 1234) x1 = x0 + 1234; else if (x1 * 1234 <= x0 - x1 - 1234512) return; else while (x0 < 0) x0 = x1; } }");
        buildIR();

        var func = getAttributes("func");
        assertTrue(func.isConst());
        assertTrue(func.isPure());
        assertFalse(func.isTerminates());
        assertFalse(func.isMalloc());
    }

    @Test
    public void testCallConst() {
        addIntToInt("foo", 1, "return x0;");
        addIntToInt("bar", 1, "return foo(x0);");
        buildIR();

        var foo = getAttributes("foo");
        assertTrue(foo.isConst());
        assertTrue(foo.isPure());
        assertTrue(foo.isTerminates());
        assertFalse(foo.isMalloc());

        var bar = getAttributes("bar");
        assertTrue(bar.isConst());
        assertTrue(bar.isPure());
        assertTrue(bar.isTerminates());
        assertFalse(bar.isMalloc());
    }

    @Test
    public void testCallPure() {
        addIntField("field1");
        addIntToInt("foo", 0, "return this.field1;");
        addIntToInt("bar", 0, "return foo();");
        buildIR();

        var foo = getAttributes("foo");
        assertFalse(foo.isConst());
        assertTrue(foo.isPure());
        assertTrue(foo.isTerminates());
        assertFalse(foo.isMalloc());

        var bar = getAttributes("bar");
        assertFalse(bar.isConst());
        assertTrue(bar.isPure());
        assertTrue(bar.isTerminates());
        assertFalse(bar.isMalloc());
    }

    @Test
    public void testCallImpure() {
        addIntField("field1");
        addIntToInt("foo", 1, "return (this.field1 = x0);");
        addIntToInt("bar", 1, "return foo(x0);");
        buildIR();

        var foo = getAttributes("foo");
        assertFalse(foo.isConst());
        assertFalse(foo.isPure());
        assertTrue(foo.isTerminates());
        assertFalse(foo.isMalloc());

        var bar = getAttributes("bar");
        assertFalse(bar.isConst());
        assertFalse(bar.isPure());
        assertTrue(bar.isTerminates());
        assertFalse(bar.isMalloc());
    }

    @Test
    public void testCallNotTerminates() {
        addIntField("field1");
        addIntToInt("foo", 1, "while (true); return x0;");
        addIntToInt("bar", 1, "return foo(x0);");
        buildIR();

        var foo = getAttributes("foo");
        assertTrue(foo.isConst());
        assertTrue(foo.isPure());
        assertFalse(foo.isTerminates());
        assertFalse(foo.isMalloc());

        var bar = getAttributes("bar");
        assertTrue(bar.isConst());
        assertTrue(bar.isPure());
        assertFalse(bar.isTerminates());
        assertFalse(bar.isMalloc());
    }

    @Test
    public void testMallocSimple() {
        addIntToArr("foo", 0, "return new int[4];");
        buildIR();

        var foo = getAttributes("foo");
        assertFalse(foo.isConst());
        assertTrue(foo.isPure());
        assertTrue(foo.isTerminates());
        assertTrue(foo.isMalloc());
    }

    @Test
    public void testMallocBranch() {
        addIntToArr("foo", 1, "if (x0 < 0) return new int[8]; else return new int[4];");
        buildIR();

        var foo = getAttributes("foo");
        assertFalse(foo.isConst());
        assertTrue(foo.isPure());
        assertTrue(foo.isTerminates());
        assertTrue(foo.isMalloc());
    }

    @Test
    public void testMallocPhi() {
        addIntToArr("foo", 1, "int[] x; if (x0 < 0) x = new int[1]; else x = new int[2]; return x;");
        buildIR();

        var foo = getAttributes("foo");
        assertFalse(foo.isConst());
        assertTrue(foo.isPure());
        assertTrue(foo.isTerminates());
        assertTrue(foo.isMalloc());
    }

    @Test
    public void testMallocLoop() {
        addIntToArr("foo", 1, "int[] x; while (x0 < 0) x = new int[1]; x0 = x0 + 1; return x;");
        buildIR();

        var foo = getAttributes("foo");
        assertFalse(foo.isConst());
        assertTrue(foo.isPure());
        assertFalse(foo.isTerminates());
        assertTrue(foo.isMalloc());
    }

    @Test
    public void testMallocMaybe() {
        addIntToArr("foo", 1, "if (x0 < 0) return new int[4]; else return null;");
        buildIR();

        var foo = getAttributes("foo");
        assertFalse(foo.isConst());
        assertTrue(foo.isPure());
        assertTrue(foo.isTerminates());
        assertTrue(foo.isMalloc());
    }

    @Test
    public void testMallocStore() {
        addArrField("field");
        addIntToArr("foo", 1, "int[] x = new int[4]; this.field = x; return x;");
        buildIR();

        var foo = getAttributes("foo");
        assertFalse(foo.isConst());
        assertFalse(foo.isPure());
        assertTrue(foo.isTerminates());
        assertTrue(foo.isMalloc());
    }

    @Test
    public void testNestedMalloc() {
        addIntToArr("foo", 3,
                "int[] x; if (x0 < 0) { if (x1 < 0) { if (x2 < 0) { x = new int[1]; } else { x = new int[2]; } } else { x = new int[3]; } } else { x = new int[4]; } return x;");
        buildIR();

        var foo = getAttributes("foo");
        assertFalse(foo.isConst());
        assertTrue(foo.isPure());
        assertTrue(foo.isTerminates());
        assertTrue(foo.isMalloc());
    }

    private void addIntToInt(String name, int numParams, String body) {
        var params = IntStream.range(0, numParams).mapToObj(n -> "int x" + n).collect(Collectors.joining(", "));
        members.add(String.format("public int %s(%s) { %s }", name, params, body));
    }

    private void addIntToVoid(String name, int numParams, String body) {
        var params = IntStream.range(0, numParams).mapToObj(n -> "int x" + n).collect(Collectors.joining(", "));
        members.add(String.format("public void %s(%s) { %s }", name, params, body));
    }

    private void addIntToArr(String name, int numParams, String body) {
        var params = IntStream.range(0, numParams).mapToObj(n -> "int x" + n).collect(Collectors.joining(", "));
        members.add(String.format("public int[] %s(%s) { %s }", name, params, body));
    }

    private void addIntField(String name) {
        members.add(String.format("public int %s;", name));
    }

    private void addArrField(String name) {
        members.add(String.format("public int[] %s;", name));
    }

    private Attributes getAttributes(String name) {
        return analysis.getAttributes(getFunction(name).getEntity());
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

    private void buildIR() {
        var logger = Logger.nullLogger();
        var errorHandler = new ErrorHandler(logger);

        var members = this.members.stream().collect(Collectors.joining(" "));
        var lexer = new Lexer(new StringReader(String.format(TEMPLATE, className, members)));

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
    }
}
