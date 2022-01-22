package edu.kit.compiler.optimizations;

import edu.kit.compiler.optimizations.inlining.InliningOptimization;
import edu.kit.compiler.transform.JFirmSingleton;
import firm.*;
import firm.bindings.binding_irgopt;
import firm.bindings.binding_irnode.ir_opcode;
import firm.nodes.*;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class InliningOptimizationTest {
    private Set<Graph> graphs;
    private OptimizationState state;
    private InliningOptimization optimization;

    @BeforeAll
    public static void setupAll() {
        JFirmSingleton.initializeFirmLinux();
    }

    @BeforeEach
    public void setup() {
        graphs = new HashSet<>();
        state = new OptimizationState();
        optimization = new InliningOptimization();
    }

    @AfterEach
    public void teardown() {
        graphs.forEach(Graph::free);
        graphs.clear();
    }

    @Test
    public void testInlinesEmptyFn() {
        var callee = initCallee(false, con -> {
            var ret = con.newReturn(con.getCurrentMem(), new Node[0]);
            con.getGraph().getEndBlock().addPred(ret);
        });
        var main = initMainGraph(callee, false);
        optimize(main);
        assertEquals(0, count(main, ir_opcode.iro_Call));
    }

    @Test
    public void testInlinesWithRet() {
        var callee = initCallee(true, con -> {
            var value = con.newConst(0, Mode.getIs());
            var ret = con.newReturn(con.getCurrentMem(), new Node[] {value});
            con.getGraph().getEndBlock().addPred(ret);
        });
        var main = initMainGraph(callee, true);
        optimize(main);
        assertEquals(0, count(main, ir_opcode.iro_Call));
    }

    @Test
    public void testInlinesFnWithSideEffect() {
        var callee = initCallee(false, con -> {
            var args = con.getGraph().getArgs();
            var param = con.newProj(args, Mode.getIs(), 0);
            var pointer = con.newConst(0, Mode.getP());
            var store = con.newStore(con.getCurrentMem(), pointer, param);
            var projMem = con.newProj(store, Mode.getM(), Div.pnM);
            var ret = con.newReturn(projMem, new Node[0]);
            con.getGraph().getEndBlock().addPred(ret);
        });
        var main = initMainGraph(callee, false);
        optimize(main);
        assertEquals(0, count(main, ir_opcode.iro_Call));
    }

    @Test
    public void testDoesNotInlineExternFn() {
        var callee = initCallee(false, con -> {
            var methodType = new MethodType(new Type[0], new Type[0]);
            var entity = new Entity(Program.getGlobalType(), Ident.mangleGlobal("extern"), methodType);
            var addr = con.newAddress(entity);
            var call = con.newCall(con.getCurrentMem(), addr, new Node[0], methodType);
            var projMem = con.newProj(call, Mode.getM(), Div.pnM);
            var ret = con.newReturn(projMem, new Node[0]);
            con.getGraph().getEndBlock().addPred(ret);
        });
        var main = initMainGraph(callee, false);
        optimize(main);
        assertEquals(1, count(main, ir_opcode.iro_Call));
        assertEquals(0, count(main, ir_opcode.iro_Const));
    }

    @Test
    public void testDoesNotInlineRecursion() {
        var intType = new PrimitiveType(Mode.getIs());
        var methodType = new MethodType(new Type[] {intType}, new Type[] {});
        var entity = new Entity(Program.getGlobalType(), "test_callee_recursive", methodType);
        var callee = initCallee(entity, con -> {
            var args = con.getGraph().getArgs();
            var param = con.newProj(args, Mode.getIs(), 0);
            var addr = con.newAddress(entity);
            var call = con.newCall(con.getCurrentMem(), addr, new Node[] {param}, methodType);
            var projMem = con.newProj(call, Mode.getM(), Div.pnM);
            con.setCurrentMem(projMem);
            var call2 = con.newCall(con.getCurrentMem(), addr, new Node[] {param}, methodType);
            var projMem2 = con.newProj(call2, Mode.getM(), Div.pnM);
            con.setCurrentMem(projMem2);
            var ret = con.newReturn(con.getCurrentMem(), new Node[0]);
            con.getGraph().getEndBlock().addPred(ret);
        });
        var main = initMainGraph(callee, false);
        optimize(main);
        assert count(main, ir_opcode.iro_Call) <= 2;
    }

    @Test
    public void testDoesNotInlineEndlessLoop() {
        var callee = initCallee(false, this::createEndlessLoop);
        binding_irgopt.remove_bads(callee.ptr);
        var main = initMainGraph(callee, false);
        optimize(main);
        assertEquals(1, count(main, ir_opcode.iro_Call));
    }

    @Test
    public void testDoesNotInlineEndlessLoopWithResult() {
        var callee = initCallee(true, this::createEndlessLoop);
        binding_irgopt.remove_bads(callee.ptr);
        var main = initMainGraph(callee, true);
        optimize(main);
        assertEquals(1, count(main, ir_opcode.iro_Call));
    }

    private void optimize(Graph main) {
        var cg = CallGraph.create(graphs);
        for (var graph: graphs) {
            state.update(cg, graph);
        }
        optimization.optimize(main, state);
    }

    private int count(Graph graph, ir_opcode opcode) {
        return Counter.count(opcode, graph);
    }

    private Graph initCallee(Entity entity, Consumer<Construction> constrFn) {
        var graph = new Graph(entity, 1);

        var con = new Construction(graph);
        constrFn.accept(con);
        con.finish();

        graphs.add(graph);
        return graph;
    }

    private Graph initCallee(boolean withResult, Consumer<Construction> constrFn) {
        var uuid = UUID.randomUUID();
        var intType = new PrimitiveType(Mode.getIs());
        var resultType = withResult ? new Type[] {intType} : new Type[0];
        var methodType = new MethodType(new Type[] {intType}, resultType);
        var entity = new Entity(Program.getGlobalType(), "test_callee_" + uuid, methodType);
        return initCallee(entity, constrFn);
    }

    private Graph initMainGraph(Graph callee, boolean withResult) {
        var uuid = UUID.randomUUID();
        var intType = new PrimitiveType(Mode.getIs());
        var resultType = withResult ? new Type[] {intType} : new Type[0];
        var mainType = new MethodType(new Type[0], resultType);
        var entity = new Entity(Program.getGlobalType(), "test_main_" + uuid, mainType);
        var graph = new Graph(entity, 0);

        var con = new Construction(graph);
        var mem = con.getCurrentMem();
        var addr = con.newAddress(callee.getEntity());
        var methodType = new MethodType(new Type[] {intType}, resultType);
        var input = con.newConst(0, Mode.getIs());
        var call = con.newCall(mem, addr, new Node[] {input}, methodType);
        var projMem = con.newProj(call, Mode.getM(), Call.pnM);
        con.setCurrentMem(projMem);

        if (withResult) {
            var projRes1 = con.newProj(call, Mode.getT(), Call.pnTResult);
            var projRes2 = con.newProj(projRes1, Mode.getIs(), 0);
            var ret = con.newReturn(con.getCurrentMem(), new Node[] {projRes2});
            con.getGraph().getEndBlock().addPred(ret);
        } else {
            var ret = con.newReturn(con.getCurrentMem(), new Node[0]);
            con.getGraph().getEndBlock().addPred(ret);
        }
        con.finish();

        graphs.add(graph);
        return graph;
    }

    private void createEndlessLoop(Construction con) {
        var entryJmp = con.newJmp();
        Block loop = con.newBlock();
        loop.addPred(entryJmp);
        con.setCurrentBlock(loop);
        con.getCurrentMem();
        con.getGraph().keepAlive(loop);
        var jmp = con.newJmp();
        loop.addPred(jmp);
        loop.mature();
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private static final class Counter extends NodeVisitor.Default {

        private final ir_opcode opcode;

        private int counter = 0;

        public static int count(ir_opcode opcode, Graph graph) {
            var visitor = new Counter(opcode);
            graph.walk(visitor);
            return visitor.counter;
        }

        @Override
        public void defaultVisit(Node node) {
            if (node.getOpCode() == opcode) {
                counter += 1;
            }
        }
    }
}
