package edu.kit.compiler.optimizations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.kit.compiler.transform.JFirmSingleton;
import firm.Construction;
import firm.Entity;
import firm.Graph;
import firm.MethodType;
import firm.Mode;
import firm.Program;
import firm.Type;
import firm.nodes.Call;
import firm.nodes.Node;

public class CallGraphTest {

    private List<Graph> graphs;
    
    @BeforeAll
    public static void setupAll() {
        JFirmSingleton.initializeFirmLinux();
    }

    @BeforeEach
    public void setup() {
        graphs = new ArrayList<>();
    }

    @AfterEach
    public void teardown() {
        graphs.forEach(Graph::free);
        graphs.clear();
    }

    @Test
    public void testSimpleCall() {
        var caller = initGraph();
        var callee = initGraph();

        createCalls(caller, callee);
        createCalls(callee);

        var cg = createCallGraph();
        assertTrue(cg.existsCall(caller, callee));
        assertFalse(cg.existsCall(callee, caller));
        assertFalse(cg.existsCall(callee, caller));
        assertFalse(cg.existsCall(callee, callee));

        assertEqualsStream(Stream.of(), cg.getCallers(caller));
        assertEqualsStream(Stream.of(caller), cg.getCallers(callee));
        assertEqualsStream(Stream.of(callee), cg.getCallees(caller));
        assertEqualsStream(Stream.of(), cg.getCallees(callee));
    }

    @Test
    public void testMultipleCallees() {
        var caller = initGraph();
        var callee1 = initGraph();
        var callee2 = initGraph();

        createCalls(caller, callee1, callee2);
        createCalls(callee1);
        createCalls(callee2);

        var cg = createCallGraph();
        assertTrue(cg.existsCall(caller, callee1));
        assertTrue(cg.existsCall(caller, callee2));
        assertFalse(cg.existsCall(callee1, callee2));

        assertEqualsStream(Stream.of(), cg.getCallers(caller));
        assertEqualsStream(Stream.of(caller), cg.getCallers(callee1));
        assertEqualsStream(Stream.of(caller), cg.getCallers(callee2));
    }

    @Test
    public void testMultipleCallers() {
        var caller1 = initGraph();
        var caller2 = initGraph();
        var callee = initGraph();

        createCalls(caller1, callee);
        createCalls(caller2, callee);
        createCalls(callee);

        var cg = createCallGraph();
        assertTrue(cg.existsCall(caller1, callee));
        assertTrue(cg.existsCall(caller2, callee));

        assertEqualsStream(Stream.of(caller1, caller2), cg.getCallers(callee));
    }

    @Test
    public void testCallFrequency() {
        var caller1 = initGraph();
        var caller2 = initGraph();
        var caller3 = initGraph();
        var callee = initGraph();

        createCalls(caller1, caller1, caller2);
        createCalls(caller2, callee);
        createCalls(caller3, callee, callee, callee, callee, callee);
        createCalls(callee);

        var cg = createCallGraph();
        assertEquals(0.0, cg.getCallFrequency(caller1, callee));
        assertEquals(1.0, cg.getCallFrequency(caller2, callee));
        assertEquals(5.0, cg.getCallFrequency(caller3, callee));
    }

    @Test
    public void testCallSelf() {
        var function = initGraph();

        createCalls(function, function);

        var cg = createCallGraph();
        assertTrue(cg.existsCall(function, function));

        assertEqualsStream(Stream.of(function), cg.getCallers(function));
        assertEqualsStream(Stream.of(function), cg.getCallees(function));
    }

    @Test
    public void testNoRecursion() {
        var caller = initGraph();
        var callee = initGraph();

        createCalls(caller, callee);
        createCalls(callee);

        var cg = createCallGraph();
        assertFalse(cg.existsRecursion(caller, callee));
        assertFalse(cg.existsRecursion(callee, caller));
    }

    @Test
    public void testSimpleRecursion() {
        var function = initGraph();

        createCalls(function, function);

        var cg = createCallGraph();
        assertTrue(cg.existsRecursion(function, function));
    }

    @Test
    public void testIndirectRecursion() {
        var fun1 = initGraph();
        var fun2 = initGraph();
        var fun3 = initGraph();

        createCalls(fun1, fun2, fun3);
        createCalls(fun2, fun1, fun3);
        createCalls(fun3);

        var cg = createCallGraph();
        assertTrue(cg.existsRecursion(fun1, fun2));
        assertTrue(cg.existsRecursion(fun2, fun1));
        assertFalse(cg.existsRecursion(fun1, fun3));
        assertFalse(cg.existsRecursion(fun2, fun3));
    }

    @Test
    public void testUpdateAddCall() {
        var caller = initGraph();
        var callee = initGraph();

        var cg = createCallGraph();
        assertFalse(cg.existsCall(caller, callee));

        createCalls(caller, callee);
        cg.update(caller.getGraph());
        assertTrue(cg.existsCall(caller, callee));
    }

    @Test
    public void testUpdateRemoveCall() {
        var caller = initGraph();
        var callee = initGraph();

        createCalls(caller, callee);
        createCalls(callee);
        var cg = createCallGraph();
        assertTrue(cg.existsCall(caller, callee));

        reinitGraph(caller);
        cg.update(caller.getGraph());
        assertFalse(cg.existsCall(caller, callee));
    }

    @Test
    public void testUpdateCreateRecursion() {
        var function = initGraph();

        createCalls(function);
        var cg = createCallGraph();
        assertFalse(cg.existsCall(function, function));

        reinitGraph(function);
        createCalls(function, function);
        cg.update(function.getGraph());
        assertTrue(cg.existsCall(function, function));
        assertTrue(cg.existsRecursion(function, function));
    }

    @Test
    public void testUpdateCreateIndirectRecursion() {
        var fun1 = initGraph();
        var fun2 = initGraph();

        createCalls(fun1, fun2);
        createCalls(fun2);
        var cg = createCallGraph();
        assertTrue(cg.existsCall(fun1, fun2));
        assertFalse(cg.existsCall(fun2, fun1));

        reinitGraph(fun2);
        createCalls(fun2, fun1);
        cg.update(fun2.getGraph());
        assertTrue(cg.existsCall(fun1, fun2));
        assertTrue(cg.existsCall(fun2, fun1));
        assertTrue(cg.existsRecursion(fun1, fun2));
        assertTrue(cg.existsRecursion(fun2, fun1));
    }

    @Test
    public void testUpdateCallFrequency() {
        var caller = initGraph();
        var callee = initGraph();

        createCalls(caller, callee, callee, callee, callee, callee, callee);
        var cg = createCallGraph();
        assertEquals(6.0, cg.getCallFrequency(caller, callee));

        reinitGraph(caller);
        createCalls(caller, callee, callee);
        cg.update(caller.getGraph());
        assertEquals(2.0, cg.getCallFrequency(caller, callee));
    }


    private CallGraph createCallGraph() {
        return CallGraph.create(graphs);
    }

    private void createCalls(Entity caller, Entity... callees) {
        var con = new Construction(caller.getGraph());

        for (var callee : callees) {
            var mem = con.getCurrentMem();
            var addr = con.newAddress(callee);

            var methodType = new MethodType(new Type[0], new Type[0]);
            var call = con.newCall(mem, addr, new Node[0], methodType);
            var projMem = con.newProj(call, Mode.getM(), Call.pnM);
            con.setCurrentMem(projMem);
        }


        var ret = con.newReturn(con.getCurrentMem(), new Node[0]);
        con.getGraph().getEndBlock().addPred(ret);
        con.finish();
    }

    private static void assertEqualsStream(Stream<Entity> expected, Stream<Entity> actual) {
        var expectedSet = expected.collect(Collectors.toSet());
        var actualSet = actual.collect(Collectors.toSet());
        assertEquals(expectedSet, actualSet);
    }

    private Entity initGraph() {
        var uuid = UUID.randomUUID();
        var methodType = new MethodType(new Type[0], new Type[0]);
        var entity = new Entity(Program.getGlobalType(), "test_" + uuid, methodType);
        var graph = new Graph(entity, 0);
        graphs.add(graph);
        return graph.getEntity();
    }

    private void reinitGraph(Entity function) {
        var oldGraph = function.getGraph();
        graphs.remove(oldGraph);
        oldGraph.free();
        var newGraph = new Graph(function, 0);
        graphs.add(newGraph);
    }
}
