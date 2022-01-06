package edu.kit.compiler.optimizations;

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

public class ArithmeticReplacementOptimizationTest {

    private static final int[] POWERS = new int[] {
            2, 4, 8, 16, 32, 64, 128, 256, 512, 1024, 2048, 4096, 8192, 16384, 32768,
            65536, 131072, 262144, 524288, 1048576, 2097152, 4194304, 8388608, 16777216,
            33554432, 67108864, 134217728, 268435456, 536870912, 1073741824,
    };

    private static Random random;
    private Deque<Graph> graphs;
    private Optimization optimization;

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
        optimization.optimize(graph());
        assertHas(graph(), ir_opcode.iro_Mul, "expected x*1 not to be replaced");

        createMul(initGraph(), 0);
        optimization.optimize(graph());
        assertHas(graph(), ir_opcode.iro_Mul, "expected x*0 not to be replaced");

        createMul(initGraph(), -1);
        optimization.optimize(graph());
        assertHas(graph(), ir_opcode.iro_Mul, "expected x*-1 not to be replaced");
    }

    @Test
    public void testMulPowerOfTwo() {
        for (int i = 0; i < 2; ++i) {
            createMul(initGraph(), randomPower(3));
            optimization.optimize(graph());
            assertNo(graph(), ir_opcode.iro_Mul);
        }

        for (int i = 0; i < 2; ++i) {
            createMul(initGraph(), -randomPower(3));
            optimization.optimize(graph());
            assertNo(graph(), ir_opcode.iro_Mul);
        }
    }

    @Test
    public void testMulIntBounds() {
        createMul(initGraph(), Integer.MAX_VALUE);
        optimization.optimize(graph());
        assertHas(graph(), ir_opcode.iro_Mul);

        createMul(initGraph(), Integer.MIN_VALUE);
        optimization.optimize(graph());
        assertNo(graph(), ir_opcode.iro_Mul);
    }

    @Test
    public void testMulRandom() {
        for (int i = 0; i < 2; ++i) {
            createMul(initGraph(), randomDivisor());
            optimization.optimize(graph());
            assertHas(graph(), ir_opcode.iro_Mul);
        }

        for (int i = 0; i < 2; ++i) {
            createMul(initGraph(), -randomDivisor());
            optimization.optimize(graph());
            assertHas(graph(), ir_opcode.iro_Mul);
        }
    }


    @Test
    public void testTrivialDiv() {
        createDiv(initGraph(), 1);
        optimization.optimize(graph());
        assertHas(graph(), ir_opcode.iro_Div, "expected x/1 not to be replaced");

        createDiv(initGraph(), 0);
        optimization.optimize(graph());
        assertHas(graph(), ir_opcode.iro_Div, "expected x/0 not to be replaced");

        createDiv(initGraph(), -1);
        optimization.optimize(graph());
        assertHas(graph(), ir_opcode.iro_Div, "expected x/-1 not to be replaced");
    }

    @Test
    public void testDivPowerOfTwo() {
        for (int i = 0; i < 2; ++i) {
            createDiv(initGraph(), randomPower());
            optimization.optimize(graph());
            assertNo(graph(), ir_opcode.iro_Div);
            assertNo(graph(), ir_opcode.iro_Mul);
        }

        for (int i = 0; i < 2; ++i) {
            createDiv(initGraph(), -randomPower());
            optimization.optimize(graph());
            assertNo(graph(), ir_opcode.iro_Div);
            assertNo(graph(), ir_opcode.iro_Mul);
        }
    }

    @Test
    public void testDivIntBounds() {
        createDiv(initGraph(), Integer.MAX_VALUE);
        optimization.optimize(graph());
        assertNo(graph(), ir_opcode.iro_Div);

        createDiv(initGraph(), Integer.MIN_VALUE);
        optimization.optimize(graph());
        assertNo(graph(), ir_opcode.iro_Div);
        assertNo(graph(), ir_opcode.iro_Mul);
    }

    @Test
    public void testDivRandom() {
        for (int i = 0; i < 4; ++i) {
            createDiv(initGraph(), randomDivisor());
            optimization.optimize(graph());
            assertNo(graph(), ir_opcode.iro_Div);
        }

        for (int i = 0; i < 4; ++i) {
            createDiv(initGraph(), -randomDivisor());
            optimization.optimize(graph());
            assertNo(graph(), ir_opcode.iro_Div);
        }
    }

    private Graph graph() {
        return graphs.peek();
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

    private static void assertNo(Graph graph, ir_opcode opcode) {
        assertNo(graph, opcode, String.format("expected no %s node in graph", opcode));
    }

    private static void assertNo(Graph graph, ir_opcode opcode, String message) {
        graph.walk(new NodeVisitor.Default() {
            @Override
            public void defaultVisit(Node node) {
                if (node.getOpCode() == opcode) {
                    throw new AssertionError(message);
                }
            }
        });
    }

    private static void assertHas(Graph graph, ir_opcode opcode) {
        assertHas(graph, opcode, String.format("expected %s node in graph", opcode));
    }

    private static void assertHas(Graph graph, ir_opcode opcode, String message) {
        try {
            graph.walk(new NodeVisitor.Default() {
                @Override
                public void defaultVisit(Node node) {
                    if (node.getOpCode() == opcode) {
                        throw new IllegalArgumentException();
                    }
                }
            });
        } catch (IllegalArgumentException e) {
            return;
        }
        throw new AssertionError(message);
    }

    private static void createDiv(Graph graph, int value) {
        var con = new Construction(graph);
        var args = graph.getArgs();
        var param = con.newProj(args, Mode.getIs(), 0);
        var lhs = con.newConv(param, Mode.getLs());
        var rhs = con.newConst(new TargetValue(value, Mode.getLs()));

        var div = con.newDiv(con.getCurrentMem(),
                lhs, rhs, op_pin_state.op_pin_state_pinned);
        var projRes = con.newProj(div, Mode.getLs(), Div.pnRes);
        var projMem = con.newProj(div, Mode.getM(), Div.pnM);
        con.setCurrentMem(projMem);
        var result = con.newConv(projRes, Mode.getIs());
        var ret = con.newReturn(con.getCurrentMem(), new Node[] { result });
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
        var entity = new Entity(Program.getGlobalType(), "div_test_" + uuid, methodType);
        var graph = new Graph(entity, 1);
        graphs.push(graph);
        return graphs.peek();
    }
}
