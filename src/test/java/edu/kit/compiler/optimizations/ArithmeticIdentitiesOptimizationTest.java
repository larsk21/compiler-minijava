package edu.kit.compiler.optimizations;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

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
import firm.PrimitiveType;
import firm.Program;
import firm.TargetValue;
import firm.Type;
import firm.bindings.binding_ircons.op_pin_state;
import firm.bindings.binding_irnode.ir_opcode;
import firm.nodes.Div;
import firm.nodes.Node;
import firm.nodes.NodeVisitor;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

public class ArithmeticIdentitiesOptimizationTest {

    private Deque<Graph> graphs;
    private Optimization optimization;

    @BeforeAll
    public static void setupAll() {
        JFirmSingleton.initializeFirmLinux();
    }

    @BeforeEach
    public void setup() {
        graphs = new ArrayDeque<>();
        optimization = new ArithmeticIdentitiesOptimization();
    }

    @AfterEach
    public void teardown() {
        graphs.forEach(Graph::free);
        graphs.clear();
    }

    @Test
    public void testAddZero() {
        createConstBOp(initGraph(), 0, false, Construction::newAdd);
        optimization.optimize(graph());
        assertEquals(0, count(ir_opcode.iro_Add));

        createConstBOp(initGraph(), 0, true, Construction::newAdd);
        optimization.optimize(graph());
        assertEquals(0, count(ir_opcode.iro_Add));
    }

    @Test
    public void testSubZero() {
        createConstBOp(initGraph(), 0, false, Construction::newSub);
        optimization.optimize(graph());
        assertEquals(0, count(ir_opcode.iro_Sub));

        createConstBOp(initGraph(), 0, true, Construction::newSub);
        optimization.optimize(graph());
        assertEquals(0, count(ir_opcode.iro_Sub));
        assertEquals(1, count(ir_opcode.iro_Minus));
    }

    @Test
    public void testDoubleMinus() {
        createUOp(initGraph(), (con, op) -> con.newMinus(con.newMinus(op)));
        optimization.optimize(graph());
        assertEquals(0, count(ir_opcode.iro_Minus));

        createUOp(initGraph(), (con, op) -> con.newMinus(con.newMinus(con.newMinus(op))));
        optimization.optimize(graph());
        assertEquals(1, count(ir_opcode.iro_Minus));
    }

    @Test
    public void testAddMinus() {
        createBOp(initGraph(), (con, l, r) -> con.newAdd(l, con.newMinus(r)));
        optimization.optimize(graph());
        assertEquals(0, count(ir_opcode.iro_Minus));
        assertEquals(0, count(ir_opcode.iro_Add));
        assertEquals(1, count(ir_opcode.iro_Sub));

        createBOp(initGraph(), (con, l, r) -> con.newAdd(con.newMinus(l), r));
        optimization.optimize(graph());
        assertEquals(0, count(ir_opcode.iro_Minus));
        assertEquals(0, count(ir_opcode.iro_Add));
        assertEquals(1, count(ir_opcode.iro_Sub));
    }

    @Test
    public void testSubMinus() {
        createBOp(initGraph(), (con, l, r) -> con.newSub(l, con.newMinus(r)));
        optimization.optimize(graph());
        assertEquals(0, count(ir_opcode.iro_Minus));
        assertEquals(0, count(ir_opcode.iro_Sub));
        assertEquals(1, count(ir_opcode.iro_Add));
    }

    @Test
    public void testSubConst() {
        createConstBOp(initGraph(), 42, false, Construction::newSub);
        optimization.optimize(graph());
        assertEquals(0, count(ir_opcode.iro_Sub));
        assertEquals(1, count(ir_opcode.iro_Add));
    }

    @Test
    public void testMulOne() {
        createConstBOp(initGraph(), 1, false, Construction::newMul);
        optimization.optimize(graph());
        assertEquals(0, count(ir_opcode.iro_Mul));
        assertEquals(0, count(ir_opcode.iro_Const));

        createConstBOp(initGraph(), 1, true, Construction::newMul);
        optimization.optimize(graph());
        assertEquals(0, count(ir_opcode.iro_Mul));
        assertEquals(0, count(ir_opcode.iro_Const));
    }

    @Test
    public void testMulZero() {
        createConstBOp(initGraph(), 0, false, Construction::newMul);
        optimization.optimize(graph());
        assertEquals(0, count(ir_opcode.iro_Mul));
        assertEquals(1, count(ir_opcode.iro_Const));

        createConstBOp(initGraph(), 0, true, Construction::newMul);
        optimization.optimize(graph());
        assertEquals(0, count(ir_opcode.iro_Mul));
        assertEquals(1, count(ir_opcode.iro_Const));
    }

    @Test
    public void testMulNegOne() {
        createConstBOp(initGraph(), -1, false, Construction::newMul);
        optimization.optimize(graph());
        assertEquals(0, count(ir_opcode.iro_Mul));
        assertEquals(1, count(ir_opcode.iro_Minus));

        createConstBOp(initGraph(), -1, true, Construction::newMul);
        optimization.optimize(graph());
        assertEquals(0, count(ir_opcode.iro_Mul));
        assertEquals(1, count(ir_opcode.iro_Minus));
    }

    @Test
    public void testDivOne() {
        createConstDiv(initGraph(), 1, false, Construction::newDiv);
        optimization.optimize(graph());
        assertEquals(0, count(ir_opcode.iro_Div));
        assertEquals(0, count(ir_opcode.iro_Const));
    }

    @Test
    public void testZeroDiv() {
        createConstDiv(initGraph(), 0, true, Construction::newDiv);
        optimization.optimize(graph());
        assertEquals(0, count(ir_opcode.iro_Div));
        assertEquals(1, count(ir_opcode.iro_Const));
    }

    @Test
    public void testDivNegOne() {
        createConstDiv(initGraph(), -1, false, Construction::newDiv);
        optimization.optimize(graph());
        assertEquals(0, count(ir_opcode.iro_Div));
        assertEquals(0, count(ir_opcode.iro_Const));
        assertEquals(1, count(ir_opcode.iro_Minus));
    }

    @Test
    public void testModOne() {
        createConstDiv(initGraph(), 1, false, Construction::newMod);
        optimization.optimize(graph());
        assertEquals(0, count(ir_opcode.iro_Mod));
        assertEquals(1, count(ir_opcode.iro_Const));
    }

    @Test
    public void testModNegOne() {
        createConstDiv(initGraph(), -1, false, Construction::newMod);
        optimization.optimize(graph());
        assertEquals(0, count(ir_opcode.iro_Mod));
        assertEquals(1, count(ir_opcode.iro_Const));
    }

    @Test
    public void testNestedAdd() {
        createConstBOp(initGraph(), 28, false, (con, lhs, rhs) -> {
            return con.newAdd(con.newAdd(lhs, con.newConst(42, Mode.getIs())), rhs);
        });
        optimization.optimize(graph());
        assertEquals(1, count(ir_opcode.iro_Add));
        assertEquals(1, count(ir_opcode.iro_Const));
    }

    @Test
    public void testNestedSub() {
        createConstBOp(initGraph(), 28, false, (con, lhs, rhs) -> {
            return con.newAdd(con.newSub(con.newConst(42, Mode.getIs()), lhs), rhs);
        });
        optimization.optimize(graph());
        assertEquals(1, count(ir_opcode.iro_Sub));
        assertEquals(0, count(ir_opcode.iro_Add));
        assertEquals(1, count(ir_opcode.iro_Const));
    }

    @Test
    public void testNestedUnnormalized() {
        createConstBOp(initGraph(), 5, false, (con, lhs, rhs) -> {
            return con.newAdd(rhs, con.newAdd(
                    con.newSub(con.newSub(lhs, con.newConst(2, Mode.getIs())), con.newConst(8, Mode.getIs())),
                    con.newConst(4, Mode.getIs())));
        });
        optimization.optimize(graph());
        assertEquals(0, count(ir_opcode.iro_Sub));
        assertEquals(1, count(ir_opcode.iro_Add));
        assertEquals(1, count(ir_opcode.iro_Const));
    }

    @Test
    public void testNestedMul() {
        createConstBOp(initGraph(), 28, false, (con, lhs, rhs) -> {
            return con.newMul(con.newMul(con.newConst(42, Mode.getIs()), lhs), rhs);
        });
        optimization.optimize(graph());
        assertEquals(1, count(ir_opcode.iro_Mul));
        assertEquals(1, count(ir_opcode.iro_Const));
    }

    @Test
    public void testNegAdd() {
        createConstBOp(initGraph(), 28, false, (con, lhs, rhs) -> {
            return con.newMinus(con.newAdd(lhs, rhs));
        });
        optimization.optimize(graph());
        assertEquals(0, count(ir_opcode.iro_Add));
        assertEquals(1, count(ir_opcode.iro_Sub));
        assertEquals(0, count(ir_opcode.iro_Minus));
        assertEquals(1, count(ir_opcode.iro_Const));
    }

    @Test
    public void testNegSub() {
        createBOp(initGraph(), (con, lhs, rhs) -> {
            return con.newMinus(con.newSub(lhs, rhs));
        });
        optimization.optimize(graph());
        assertEquals(1, count(ir_opcode.iro_Sub));
        assertEquals(0, count(ir_opcode.iro_Minus));
    }

    @Test
    public void testNegMul() {
        createConstBOp(initGraph(), 42, false, (con, lhs, rhs) -> {
            return con.newMinus(con.newMul(lhs, rhs));
        });
        optimization.optimize(graph());
        assertEquals(1, count(ir_opcode.iro_Mul));
        assertEquals(0, count(ir_opcode.iro_Minus));
        assertEquals(1, count(ir_opcode.iro_Const));
    }

    @Test
    public void testConvAdd() {
        createConstBOp(initGraph(Mode.getLs()), 42, false, (con, lhs, rhs) -> {
            return con.newAdd(con.newConv(con.newAdd(lhs, rhs), Mode.getLs()), con.newConst(28, Mode.getLs()));
        });
        optimization.optimize(graph());
        assertEquals(1, count(ir_opcode.iro_Conv));
        assertEquals(1, count(ir_opcode.iro_Add));
        assertEquals(1, count(ir_opcode.iro_Const));
    }

    @Test
    public void testMulDist() {
        createConstBOp(initGraph(), 42, false, (con, lhs, rhs) -> {
            return con.newAdd(
                    con.newMul(con.newAdd(lhs, rhs), con.newConst(28, Mode.getIs())),
                    con.newConst(12, Mode.getIs()));
        });
        optimization.optimize(graph());
        assertEquals(1, count(ir_opcode.iro_Add));
        assertEquals(1, count(ir_opcode.iro_Mul));
        assertEquals(2, count(ir_opcode.iro_Const));
    }

    private Graph graph() {
        return graphs.peek();
    }

    private int count(ir_opcode opcode) {
        return Counter.count(opcode, graph());
    }

    private static void createConstDiv(Graph graph, int value, boolean swap, DivNodeFun fun) {
        var con = new Construction(graph);
        var args = graph.getArgs();
        var param = con.newProj(args, Mode.getIs(), 0);
        var lhs = con.newConv(param, Mode.getLs());
        var rhs = con.newConst(new TargetValue(value, Mode.getLs()));

        var mem = con.getCurrentMem();
        var div = swap
                ? fun.apply(con, mem, rhs, lhs, op_pin_state.op_pin_state_pinned)
                : fun.apply(con, mem, lhs, rhs, op_pin_state.op_pin_state_pinned);
        var projRes = con.newProj(div, Mode.getLs(), Div.pnRes);
        var projMem = con.newProj(div, Mode.getM(), Div.pnM);
        con.setCurrentMem(projMem);
        var result = con.newConv(projRes, Mode.getIs());
        var ret = con.newReturn(con.getCurrentMem(), new Node[] { result });
        graph.getEndBlock().addPred(ret);
        con.finish();
    }

    private static void createConstBOp(Graph graph, int value, boolean swap, BNodeFun fun) {
        var con = new Construction(graph);
        var args = graph.getArgs();
        var lhs = con.newProj(args, Mode.getIs(), 0);
        var rhs = con.newConst(new TargetValue(value, Mode.getIs()));

        var op = swap ? fun.apply(con, rhs, lhs) : fun.apply(con, lhs, rhs);
        var ret = con.newReturn(con.getCurrentMem(), new Node[] { op });
        graph.getEndBlock().addPred(ret);
        con.finish();
    }

    private static void createBOp(Graph graph, BNodeFun fun) {
        var con = new Construction(graph);
        var args = graph.getArgs();
        var lhs = con.newProj(args, Mode.getIs(), 0);
        var rhs = con.newProj(args, Mode.getIs(), 1);

        var op = fun.apply(con, lhs, rhs);
        var ret = con.newReturn(con.getCurrentMem(), new Node[] { op });
        graph.getEndBlock().addPred(ret);
        con.finish();
    }

    private static void createUOp(Graph graph, UNodeFun fun) {
        var con = new Construction(graph);
        var args = graph.getArgs();
        var lhs = con.newProj(args, Mode.getIs(), 0);

        var op = fun.apply(con, lhs);
        var ret = con.newReturn(con.getCurrentMem(), new Node[] { op });
        graph.getEndBlock().addPred(ret);
        con.finish();
    }

    private Graph initGraph() {
        return initGraph(Mode.getIs());
    }

    private Graph initGraph(Mode result) {
        var uuid = UUID.randomUUID();
        var intType = new PrimitiveType(Mode.getIs());
        var resultType = new PrimitiveType(result);
        var methodType = new MethodType(new Type[] { intType, intType }, new Type[] { resultType });
        var entity = new Entity(Program.getGlobalType(), "test_" + uuid, methodType);
        var graph = new Graph(entity, 2);
        graphs.push(graph);
        return graphs.peek();
    }

    private interface UNodeFun {
        Node apply(Construction con, Node op);
    }

    private interface BNodeFun {
        Node apply(Construction con, Node lhs, Node rhs);
    }

    private interface DivNodeFun {
        Node apply(Construction con, Node mem, Node lhs, Node rhs, op_pin_state pin_state);
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
