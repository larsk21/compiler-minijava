package edu.kit.compiler.optimizations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.UUID;
import java.util.function.BiFunction;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import edu.kit.compiler.transform.JFirmSingleton;

import firm.bindings.binding_irdom;
import firm.Construction;
import firm.Entity;
import firm.Graph;
import firm.Ident;
import firm.MethodType;
import firm.Mode;
import firm.PrimitiveType;
import firm.Program;
import firm.TargetValue;
import firm.Type;
import firm.nodes.Block;
import firm.nodes.Call;
import firm.nodes.Cond;
import firm.nodes.Node;

public class LoopInvariantOptimizationCallTest {

    private static PrimitiveType INT_TYPE;
    private static MethodType METHOD_TYPE;
    private static Entity METHOD_ENTITY;

    @BeforeAll
    public static void setupAll() {
        JFirmSingleton.initializeFirmLinux();

        INT_TYPE = new PrimitiveType(Mode.getIs());

        UUID uuid = UUID.randomUUID();
        METHOD_TYPE = new MethodType(new Type[0], new Type[] { INT_TYPE });
        METHOD_ENTITY = new Entity(Program.getGlobalType(), Ident.mangleGlobal("extern_" + uuid), METHOD_TYPE);
    }

    private Graph createGraph(Type[] returnTypes) {
        UUID uuid = UUID.randomUUID();
        MethodType methodType = new MethodType(new Type[] { }, returnTypes);
        Entity entity = new Entity(Program.getGlobalType(), "test_" + uuid, methodType);
        Graph graph = new Graph(entity, 0);

        return graph;
    }

    /**
     * @param createCall Function that receives the construction object and the
     * initial memory as argument and returns a call node.
     * @param makeAssertions Function that receives the block in which the call
     * node was placed originally, as well as the call node itself. The return
     * value is ignored.
     */
    private void genericTest(BiFunction<Construction, Node, Node> createCall, BiFunction<Block, Node, Object> makeAssertions) {
        //            <-> B
        // Start -> A
        //             -> C -> End

        // construct graph
        Graph graph = createGraph(new Type[] { INT_TYPE });
        Construction construction = new Construction(graph);

        Block block1 = construction.getCurrentBlock();
        Node mem1 = construction.getCurrentMem();
        Node jmp1 = construction.newJmp();
        block1.mature();

        Block block2 = construction.newBlock();
        block2.addPred(jmp1);
        construction.setCurrentBlock(block2);
        Node const21 = construction.newConst(TargetValue.getBTrue());
        Node cond2 = construction.newCond(const21);
        Node proj21 = construction.newProj(cond2, Mode.getX(), Cond.pnFalse);
        Node proj22 = construction.newProj(cond2, Mode.getX(), Cond.pnTrue);

        Block block3 = construction.newBlock();
        block3.addPred(proj22);
        construction.setCurrentBlock(block3);
        Node call3 = createCall.apply(construction, mem1);
        Node proj31 = construction.newProj(call3, Mode.getT(), Call.pnTResult);
        Node proj32 = construction.newProj(proj31, Mode.getIs(), 0);
        Node jmp3 = construction.newJmp();
        block3.mature();

        block2.addPred(jmp3);
        construction.setCurrentBlock(block2);
        Node const22 = construction.newConst(new TargetValue(1, Mode.getIs()));
        Node phi2 = construction.newPhi(new Node[] { const22, proj32 }, Mode.getIs());
        block2.mature();

        Block block4 = construction.newBlock();
        block4.addPred(proj21);
        construction.setCurrentBlock(block4);
        Node return4 = construction.newReturn(construction.getCurrentMem(), new Node[] { phi2 });
        block4.mature();

        Block block5 = graph.getEndBlock();
        block5.addPred(return4);

        construction.finish();

        binding_irdom.compute_doms(graph.ptr);

        // run optimization
        LoopInvariantOptimization optimization = new LoopInvariantOptimization();
        optimization.optimize(call3.getGraph(), null);

        // make assertions
        makeAssertions.apply(block3, call3);
    }

    @Test
    public void testPinnedMemoryFunction() {
        genericTest((construction, initialMem) -> {
            Node address = construction.newAddress(METHOD_ENTITY);
            return construction.newCall(construction.getCurrentMem(), address, new Node[0], METHOD_TYPE);
        }, (loopBlock, call) -> {
            assertEquals(loopBlock, call.getBlock());
            return null;
        });
    }

    @Test
    public void testPinnedNoMemoryFunction() {
        genericTest((construction, initialMem) -> {
            Node address = construction.newAddress(METHOD_ENTITY);
            return construction.newCall(construction.newNoMem(), address, new Node[0], METHOD_TYPE);
        }, (loopBlock, call) -> {
            assertEquals(loopBlock, call.getBlock());
            return null;
        });
    }

    @Test
    public void testUnpinnedMemoryFunction() {
        genericTest((construction, initialMem) -> {
            Node address = construction.newAddress(METHOD_ENTITY);
            Node call = construction.newCall(construction.getCurrentMem(), address, new Node[0], METHOD_TYPE);
            Util.setPinned(call, false);
            return call;
        }, (loopBlock, call) -> {
            assertEquals(loopBlock, call.getBlock());
            return null;
        });
    }

    @Test
    public void testUnpinnedNoMemoryFunction() {
        genericTest((construction, initialMem) -> {
            Node address = construction.newAddress(METHOD_ENTITY);
            Node call = construction.newCall(construction.newNoMem(), address, new Node[0], METHOD_TYPE);
            Util.setPinned(call, false);
            return call;
        }, (loopBlock, call) -> {
            assertNotEquals(loopBlock, call.getBlock());
            return null;
        });
    }

    @Test
    public void testUnpinnedOutsideLoopMemoryFunction() {
        genericTest((construction, initialMem) -> {
            Node address = construction.newAddress(METHOD_ENTITY);
            Node call = construction.newCall(initialMem, address, new Node[0], METHOD_TYPE);
            Util.setPinned(call, false);
            return call;
        }, (loopBlock, call) -> {
            assertNotEquals(loopBlock, call.getBlock());
            return null;
        });
    }

}
