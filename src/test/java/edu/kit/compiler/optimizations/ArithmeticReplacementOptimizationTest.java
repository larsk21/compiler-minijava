package edu.kit.compiler.optimizations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Random;
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

public class ArithmeticReplacementOptimizationTest {

    private static final int[] POWERS = new int[] {
            2, 4, 8, 16, 32, 64, 128, 256, 512, 1024, 2048, 4096, 8192, 16384, 32768,
            65536, 131072, 262144, 524288, 1048576, 2097152, 4194304, 8388608, 16777216,
            33554432, 67108864, 134217728, 268435456, 536870912, 1073741824,
    };

    private static Random random;
    private Deque<Graph> graphs;
    private ArithmeticReplacementOptimization optimization;

    @BeforeAll
    public static void setupAll() {
        JFirmSingleton.initializeFirmLinux();
        random = new Random();
    }

    @BeforeEach
    public void setup() {
        graphs = new ArrayDeque<>();
        optimization = new ArithmeticReplacementOptimization();
    }

    @AfterEach
    public void teardown() {
        graphs.forEach(Graph::free);
        graphs.clear();
    }

    @Test
    public void testTrivialMul() {
        createMul(initGraph(), 1);
        optimization.optimize(graph(), null);
        assertEquals(1, count(ir_opcode.iro_Mul));

        createMul(initGraph(), 0);
        optimization.optimize(graph(), null);
        assertEquals(1, count(ir_opcode.iro_Mul));

        createMul(initGraph(), -1);
        optimization.optimize(graph(), null);
        assertEquals(1, count(ir_opcode.iro_Mul));
    }

    @Test
    public void testMulPowerOfTwo() {
        for (int i = 0; i < 2; ++i) {
            createMul(initGraph(), randomPower(3));
            optimization.optimize(graph(), null);
            assertEquals(0, count(ir_opcode.iro_Mul));
        }

        for (int i = 0; i < 2; ++i) {
            createMul(initGraph(), -randomPower(3));
            optimization.optimize(graph(), null);
            assertEquals(0, count(ir_opcode.iro_Mul));
        }
    }

    @Test
    public void testMulIntBounds() {
        createMul(initGraph(), Integer.MAX_VALUE);
        optimization.optimize(graph(), null);
        assertNotEquals(0, count(ir_opcode.iro_Mul));

        createMul(initGraph(), Integer.MIN_VALUE);
        optimization.optimize(graph(), null);
        assertEquals(0, count(ir_opcode.iro_Mul));
    }

    @Test
    public void testMulRandom() {
        for (int i = 0; i < 2; ++i) {
            createMul(initGraph(), randomDivisor());
            optimization.optimize(graph(), null);
            assertNotEquals(0, count(ir_opcode.iro_Mul));
        }

        for (int i = 0; i < 2; ++i) {
            createMul(initGraph(), -randomDivisor());
            optimization.optimize(graph(), null);
            assertNotEquals(0, count(ir_opcode.iro_Mul));
        }
    }

    @Test
    public void testTrivialDiv() {
        createDiv(initGraph(), 1);
        optimization.optimize(graph(), null);
        assertEquals(1, count(ir_opcode.iro_Div));

        createDiv(initGraph(), 0);
        optimization.optimize(graph(), null);
        assertEquals(1, count(ir_opcode.iro_Div));

        createDiv(initGraph(), -1);
        optimization.optimize(graph(), null);
        assertEquals(1, count(ir_opcode.iro_Div));
    }

    @Test
    public void testDivPowerOfTwo() {
        for (int i = 0; i < 2; ++i) {
            createDiv(initGraph(), randomPower());
            optimization.optimize(graph(), null);
            assertEquals(0, count(ir_opcode.iro_Div));
            assertEquals(0, count(ir_opcode.iro_Mul));
        }

        for (int i = 0; i < 2; ++i) {
            createDiv(initGraph(), -randomPower());
            optimization.optimize(graph(), null);
            assertEquals(0, count(ir_opcode.iro_Div));
            assertEquals(0, count(ir_opcode.iro_Mul));
        }
    }

    @Test
    public void testDivIntBounds() {
        createDiv(initGraph(), Integer.MAX_VALUE);
        optimization.optimize(graph(), null);
        assertEquals(0, count(ir_opcode.iro_Div));

        createDiv(initGraph(), Integer.MIN_VALUE);
        optimization.optimize(graph(), null);
        assertEquals(0, count(ir_opcode.iro_Div));
        assertEquals(0, count(ir_opcode.iro_Mul));
    }

    @Test
    public void testDivRandom() {
        for (int i = 0; i < 4; ++i) {
            createDiv(initGraph(), randomDivisor());
            optimization.optimize(graph(), null);
            assertEquals(0, count(ir_opcode.iro_Div));
        }

        for (int i = 0; i < 4; ++i) {
            createDiv(initGraph(), -randomDivisor());
            optimization.optimize(graph(), null);
            assertEquals(0, count(ir_opcode.iro_Div));
        }
    }

    private Graph graph() {
        return graphs.peek();
    }

    private int count(ir_opcode opcode) {
        return Counter.count(opcode, graph());
    }

    private int randomPower() {
        return randomPower(0);
    }

    private int randomPower(int origin) {
        return POWERS[random.ints(origin, POWERS.length).findFirst().getAsInt()];
    }

    private int randomDivisor() {
        return random.ints(3, Integer.MAX_VALUE)
                .filter(x -> Arrays.binarySearch(POWERS, x) < 0)
                .findFirst().getAsInt();
    }

    private static void createDiv(Graph graph, int value) {
        var con = new Construction(graph);
        var args = graph.getArgs();
        var lhs = con.newProj(args, Mode.getIs(), 0);
        var rhs = con.newConst(new TargetValue(value, Mode.getIs()));

        var div = con.newDiv(con.getCurrentMem(),
                lhs, rhs, op_pin_state.op_pin_state_pinned);
        var projRes = con.newProj(div, Mode.getIs(), Div.pnRes);
        var projMem = con.newProj(div, Mode.getM(), Div.pnM);
        con.setCurrentMem(projMem);
        var ret = con.newReturn(con.getCurrentMem(), new Node[] { projRes });
        graph.getEndBlock().addPred(ret);
        con.finish();
    }

    private static void createMul(Graph graph, int value) {
        var con = new Construction(graph);
        var args = graph.getArgs();
        var lhs = con.newProj(args, Mode.getIs(), 0);
        var rhs = con.newConst(new TargetValue(value, Mode.getIs()));

        var mul = con.newMul(lhs, rhs);
        var ret = con.newReturn(con.getCurrentMem(), new Node[] { mul });
        graph.getEndBlock().addPred(ret);
        con.finish();
    }

    private Graph initGraph() {
        var uuid = UUID.randomUUID();
        var intType = new PrimitiveType(Mode.getIs());
        var methodType = new MethodType(new Type[] { intType }, new Type[] { intType });
        var entity = new Entity(Program.getGlobalType(), "test_" + uuid, methodType);
        var graph = new Graph(entity, 1);
        graphs.push(graph);
        return graphs.peek();
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
