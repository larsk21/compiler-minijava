package edu.kit.compiler.optimizations;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import edu.kit.compiler.transform.JFirmSingleton;

import firm.Construction;
import firm.Entity;
import firm.Graph;
import firm.MethodType;
import firm.Mode;
import firm.PrimitiveType;
import firm.Program;
import firm.Relation;
import firm.TargetValue;
import firm.Type;
import firm.nodes.Block;
import firm.nodes.Cond;
import firm.nodes.Node;

public class LoopInvariantOptimizationTest {

    private static PrimitiveType INT_TYPE;

    @BeforeAll
    public static void setupAll() {
        JFirmSingleton.initializeFirmLinux();

        INT_TYPE = new PrimitiveType(Mode.getIs());
    }

    private Graph createGraph(Type[] returnTypes) {
        UUID uuid = UUID.randomUUID();
        MethodType methodType = new MethodType(new Type[] { }, returnTypes);
        Entity entity = new Entity(Program.getGlobalType(), "test_" + uuid, methodType);
        Graph graph = new Graph(entity, 0);

        return graph;
    }

    @Test
    public void testSimple() {
        //            <-> B
        // Start -> A
        //             -> C -> End

        // construct graph
        Graph graph = createGraph(new Type[] { INT_TYPE, INT_TYPE });
        Construction construction = new Construction(graph);

        Block block1 = construction.getCurrentBlock();
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
        Node const31 = construction.newConst(new TargetValue(42, Mode.getIs()));
        Node const32 = construction.newConst(new TargetValue(73, Mode.getIs()));
        Node add3 = construction.newAdd(const31, const32);
        Node const33 = construction.newConst(new TargetValue(1, Mode.getIs()));
        Node sub3 = construction.newSub(construction.newBad(Mode.getIs()), const33);
        Node jmp3 = construction.newJmp();
        block3.mature();

        block2.addPred(jmp3);
        construction.setCurrentBlock(block2);
        Node const22 = construction.newConst(new TargetValue(17, Mode.getIs()));
        Node phi21 = construction.newPhi(new Node[] { const22, sub3 }, Mode.getIs());
        sub3.setPred(0, phi21);
        Node const23 = construction.newConst(new TargetValue(1, Mode.getIs()));
        Node phi22 = construction.newPhi(new Node[] { const23, add3 }, Mode.getIs());
        block2.mature();

        Block block4 = construction.newBlock();
        block4.addPred(proj21);
        construction.setCurrentBlock(block4);
        Node return4 = construction.newReturn(construction.getCurrentMem(), new Node[] { phi21, phi22 });
        block4.mature();

        Block block5 = graph.getEndBlock();
        block5.addPred(return4);

        construction.finish();

        // run optimization
        LoopInvariantOptimization optimization = new LoopInvariantOptimization();
        optimization.optimize(graph, null);

        // make assertions
        assertEquals(block2, cond2.getBlock());
        assertEquals(block2, phi21.getBlock());
        assertEquals(block2, phi22.getBlock());

        assertEquals(block1, add3.getBlock());
        assertEquals(block3, sub3.getBlock());
    }

    @Test
    public void testChain() {
        //            <-> B
        // Start -> A
        //             -> C -> End

        // construct graph
        Graph graph = createGraph(new Type[] { INT_TYPE });
        Construction construction = new Construction(graph);

        Block block1 = construction.getCurrentBlock();
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
        Node const31 = construction.newConst(new TargetValue(42, Mode.getIs()));
        Node const32 = construction.newConst(new TargetValue(73, Mode.getIs()));
        Node add3 = construction.newAdd(const31, const32);
        Node const33 = construction.newConst(new TargetValue(1, Mode.getIs()));
        Node sub3 = construction.newSub(add3, const33);
        Node jmp3 = construction.newJmp();
        block3.mature();

        block2.addPred(jmp3);
        construction.setCurrentBlock(block2);
        Node const22 = construction.newConst(new TargetValue(0, Mode.getIs()));
        Node phi2 = construction.newPhi(new Node[] { const22, sub3 }, Mode.getIs());
        block2.mature();

        Block block4 = construction.newBlock();
        block4.addPred(proj21);
        construction.setCurrentBlock(block4);
        Node return4 = construction.newReturn(construction.getCurrentMem(), new Node[] { phi2 });
        block4.mature();

        Block block5 = graph.getEndBlock();
        block5.addPred(return4);

        construction.finish();

        // run optimization
        LoopInvariantOptimization optimization = new LoopInvariantOptimization();
        optimization.optimize(graph, null);

        // make assertions
        assertEquals(block2, cond2.getBlock());
        assertEquals(block2, phi2.getBlock());

        assertEquals(block1, add3.getBlock());
        assertEquals(block1, sub3.getBlock());
    }

    @Test
    public void testHeader() {
        //            <-> B
        // Start -> A
        //             -> C -> End

        // construct graph
        Graph graph = createGraph(new Type[] { INT_TYPE, INT_TYPE });
        Construction construction = new Construction(graph);

        Block block1 = construction.getCurrentBlock();
        Node jmp1 = construction.newJmp();
        block1.mature();

        Block block2 = construction.newBlock();
        block2.addPred(jmp1);
        construction.setCurrentBlock(block2);
        Node const21 = construction.newConst(new TargetValue(42, Mode.getIs()));
        Node const22 = construction.newConst(new TargetValue(73, Mode.getIs()));
        Node add2 = construction.newAdd(const21, const22);
        Node const23 = construction.newConst(new TargetValue(17, Mode.getIs()));
        Node const24 = construction.newConst(new TargetValue(1, Mode.getIs()));
        Node sub2 = construction.newSub(construction.newBad(Mode.getIs()), const24);
        Node const25 = construction.newConst(TargetValue.getBTrue());
        Node cond2 = construction.newCond(const25);
        Node proj21 = construction.newProj(cond2, Mode.getX(), Cond.pnFalse);
        Node proj22 = construction.newProj(cond2, Mode.getX(), Cond.pnTrue);

        Block block3 = construction.newBlock();
        block3.addPred(proj22);
        construction.setCurrentBlock(block3);
        Node jmp3 = construction.newJmp();
        block3.mature();

        block2.addPred(jmp3);
        construction.setCurrentBlock(block2);
        Node phi2 = construction.newPhi(new Node[] { const23, sub2 }, Mode.getIs());
        sub2.setPred(0, phi2);
        block2.mature();

        Block block4 = construction.newBlock();
        block4.addPred(proj21);
        construction.setCurrentBlock(block4);
        Node return4 = construction.newReturn(construction.getCurrentMem(), new Node[] { add2, phi2 });
        block4.mature();

        Block block5 = graph.getEndBlock();
        block5.addPred(return4);

        construction.finish();

        // run optimization
        LoopInvariantOptimization optimization = new LoopInvariantOptimization();
        optimization.optimize(graph, null);

        // make assertions
        assertEquals(block2, cond2.getBlock());
        assertEquals(block2, phi2.getBlock());

        assertEquals(block1, add2.getBlock());
        assertEquals(block2, sub2.getBlock());
    }

    @Test
    public void testCond() {
        //            <--------
        //                 -> C
        //            -> B
        //                 -> D
        //            <--------
        // Start -> A
        //            -> E -> End

        // construct graph
        Graph graph = createGraph(new Type[] { INT_TYPE, INT_TYPE });
        Construction construction = new Construction(graph);

        Block block1 = construction.getCurrentBlock();
        Node jmp1 = construction.newJmp();
        block1.mature();

        Block block2 = construction.newBlock();
        block2.addPred(jmp1);
        construction.setCurrentBlock(block2);
        Node const21 = construction.newConst(new TargetValue(10, Mode.getIs()));
        Node cmp2 = construction.newCmp(construction.newBad(Mode.getIs()), const21, Relation.Less);
        Node cond2 = construction.newCond(cmp2);
        Node proj21 = construction.newProj(cond2, Mode.getX(), Cond.pnFalse);
        Node proj22 = construction.newProj(cond2, Mode.getX(), Cond.pnTrue);

        Block block3 = construction.newBlock();
        block3.addPred(proj22);
        construction.setCurrentBlock(block3);
        Node const31 = construction.newConst(new TargetValue(111, Mode.getIs()));
        Node const32 = construction.newConst(new TargetValue(113, Mode.getIs()));
        Node cmp3 = construction.newCmp(const31, const32, Relation.Equal);
        Node cond3 = construction.newCond(cmp3);
        Node proj31 = construction.newProj(cond3, Mode.getX(), Cond.pnFalse);
        Node proj32 = construction.newProj(cond3, Mode.getX(), Cond.pnTrue);
        block3.mature();

        Block block4 = construction.newBlock();
        block4.addPred(proj31);
        construction.setCurrentBlock(block4);
        Node const41 = construction.newConst(new TargetValue(42, Mode.getIs()));
        Node const42 = construction.newConst(new TargetValue(73, Mode.getIs()));
        Node add4 = construction.newAdd(const41, const42);
        Node jmp4 = construction.newJmp();
        block4.mature();

        Block block5 = construction.newBlock();
        block5.addPred(proj32);
        construction.setCurrentBlock(block5);
        Node const51 = construction.newConst(new TargetValue(1, Mode.getIs()));
        Node sub5 = construction.newSub(construction.newBad(Mode.getIs()), const51);
        Node jmp5 = construction.newJmp();
        block5.mature();

        block2.addPred(jmp4);
        block2.addPred(jmp5);
        construction.setCurrentBlock(block2);
        Node const22 = construction.newConst(new TargetValue(17, Mode.getIs()));
        Node phi21 = construction.newPhi(new Node[] { const22, construction.newBad(Mode.getIs()), sub5 }, Mode.getIs());
        phi21.setPred(1, phi21);
        sub5.setPred(0, phi21);
        cmp2.setPred(0, phi21);
        Node const23 = construction.newConst(new TargetValue(1, Mode.getIs()));
        Node phi22 = construction.newPhi(new Node[] { const23, add4, construction.newBad(Mode.getIs()) }, Mode.getIs());
        phi22.setPred(2, phi22);
        block2.mature();

        Block block6 = construction.newBlock();
        block6.addPred(proj21);
        construction.setCurrentBlock(block6);
        Node return6 = construction.newReturn(construction.getCurrentMem(), new Node[] { phi21, phi22 });
        block6.mature();

        Block block7 = graph.getEndBlock();
        block7.addPred(return6);

        construction.finish();

        // run optimization
        LoopInvariantOptimization optimization = new LoopInvariantOptimization();
        optimization.optimize(graph, null);

        // make assertions
        assertEquals(block2, cond2.getBlock());
        assertEquals(block2, phi21.getBlock());
        assertEquals(block2, phi22.getBlock());
        assertEquals(block3, cond3.getBlock());

        assertEquals(block2, cmp2.getBlock());
        assertEquals(block1, cmp3.getBlock());
        assertEquals(block1, add4.getBlock());
        assertEquals(block5, sub5.getBlock());
    }

    @Test
    public void testMultiplePredecessors() {
        //            -> B ->   <-> E
        // Start -> A         D
        //            -> C ->    -> F -> End

        // construct graph
        Graph graph = createGraph(new Type[] { INT_TYPE, INT_TYPE });
        Construction construction = new Construction(graph);

        Block block1 = construction.getCurrentBlock();
        Node jmp1 = construction.newJmp();
        block1.mature();

        Block block2 = construction.newBlock();
        block2.addPred(jmp1);
        construction.setCurrentBlock(block2);
        Node const2 = construction.newConst(TargetValue.getBTrue());
        Node cond2 = construction.newCond(const2);
        Node proj21 = construction.newProj(cond2, Mode.getX(), Cond.pnFalse);
        Node proj22 = construction.newProj(cond2, Mode.getX(), Cond.pnTrue);
        block2.mature();

        Block block3 = construction.newBlock();
        block3.addPred(proj21);
        construction.setCurrentBlock(block3);
        Node jmp3 = construction.newJmp();
        block3.mature();

        Block block4 = construction.newBlock();
        block4.addPred(proj22);
        construction.setCurrentBlock(block4);
        Node jmp4 = construction.newJmp();
        block4.mature();

        Block block5 = construction.newBlock();
        block5.addPred(jmp3);
        block5.addPred(jmp4);
        construction.setCurrentBlock(block5);
        Node const51 = construction.newConst(TargetValue.getBFalse());
        Node cond5 = construction.newCond(const51);
        Node proj51 = construction.newProj(cond5, Mode.getX(), Cond.pnFalse);
        Node proj52 = construction.newProj(cond5, Mode.getX(), Cond.pnTrue);

        Block block6 = construction.newBlock();
        block6.addPred(proj52);
        construction.setCurrentBlock(block6);
        Node const61 = construction.newConst(new TargetValue(42, Mode.getIs()));
        Node const62 = construction.newConst(new TargetValue(73, Mode.getIs()));
        Node add6 = construction.newAdd(const61, const62);
        Node const63 = construction.newConst(new TargetValue(1, Mode.getIs()));
        Node sub6 = construction.newSub(construction.newBad(Mode.getIs()), const63);
        Node jmp6 = construction.newJmp();
        block6.mature();

        block5.addPred(jmp6);
        construction.setCurrentBlock(block5);
        Node const52 = construction.newConst(new TargetValue(17, Mode.getIs()));
        Node phi51 = construction.newPhi(new Node[] { const52, const52, sub6 }, Mode.getIs());
        sub6.setPred(0, phi51);
        Node const53 = construction.newConst(new TargetValue(1, Mode.getIs()));
        Node phi52 = construction.newPhi(new Node[] { const53, const53, add6 }, Mode.getIs());
        block5.mature();

        Block block7 = construction.newBlock();
        block7.addPred(proj51);
        construction.setCurrentBlock(block7);
        Node return7 = construction.newReturn(construction.getCurrentMem(), new Node[] { phi51, phi52 });
        block7.mature();

        Block block8 = graph.getEndBlock();
        block8.addPred(return7);

        construction.finish();

        // run optimization
        LoopInvariantOptimization optimization = new LoopInvariantOptimization();
        optimization.optimize(graph, null);

        // make assertions
        assertEquals(block5, cond5.getBlock());
        assertEquals(block5, phi51.getBlock());
        assertEquals(block5, phi52.getBlock());

        assertEquals(block2, add6.getBlock());
        assertEquals(block6, sub6.getBlock());
    }

    @Test
    public void testInnerLoop() {
        //                 <-> C
        //            <-> B
        // Start -> A
        //             -> D -> End

        // construct graph
        Graph graph = createGraph(new Type[] { INT_TYPE, INT_TYPE, INT_TYPE });
        Construction construction = new Construction(graph);

        Block block1 = construction.getCurrentBlock();
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
        Node const3 = construction.newConst(TargetValue.getBFalse());
        Node cond3 = construction.newCond(const3);
        Node proj31 = construction.newProj(cond3, Mode.getX(), Cond.pnFalse);
        Node proj32 = construction.newProj(cond3, Mode.getX(), Cond.pnTrue);

        Block block4 = construction.newBlock();
        block4.addPred(proj32);
        construction.setCurrentBlock(block4);
        Node const41 = construction.newConst(new TargetValue(42, Mode.getIs()));
        Node const42 = construction.newConst(new TargetValue(73, Mode.getIs()));
        Node add4 = construction.newAdd(const41, const42);
        Node const43 = construction.newConst(new TargetValue(1, Mode.getIs()));
        Node sub4 = construction.newSub(construction.newBad(Mode.getIs()), const43);
        Node const44 = construction.newConst(new TargetValue(2, Mode.getIs()));
        Node mul4 = construction.newMul(construction.newBad(Mode.getIs()), const44);
        Node jmp4 = construction.newJmp();
        block4.mature();

        block3.addPred(jmp4);
        construction.setCurrentBlock(block3);
        Node phi31 = construction.newPhi(new Node[] { construction.newBad(Mode.getIs()), sub4 }, Mode.getIs());
        Node phi32 = construction.newPhi(new Node[] { construction.newBad(Mode.getIs()), add4 }, Mode.getIs());
        Node phi33 = construction.newPhi(new Node[] { construction.newBad(Mode.getIs()), mul4 }, Mode.getIs());
        mul4.setPred(0, phi33);
        block3.mature();

        block2.addPred(proj31);
        construction.setCurrentBlock(block2);
        Node const22 = construction.newConst(new TargetValue(17, Mode.getIs()));
        Node phi21 = construction.newPhi(new Node[] { const22, phi31 }, Mode.getIs());
        sub4.setPred(0, phi21);
        phi31.setPred(0, phi21);
        Node const23 = construction.newConst(new TargetValue(1, Mode.getIs()));
        Node phi22 = construction.newPhi(new Node[] { const23, phi32 }, Mode.getIs());
        phi32.setPred(0, phi22);
        Node const24 = construction.newConst(new TargetValue(2, Mode.getIs()));
        Node phi23 = construction.newPhi(new Node[] { const24, phi33 }, Mode.getIs());
        phi33.setPred(0, phi23);
        block2.mature();

        Block block5 = construction.newBlock();
        block5.addPred(proj21);
        construction.setCurrentBlock(block5);
        Node return5 = construction.newReturn(construction.getCurrentMem(), new Node[] { phi21, phi22, phi23 });
        block5.mature();

        Block block6 = graph.getEndBlock();
        block6.addPred(return5);

        construction.finish();

        // run optimization
        LoopInvariantOptimization optimization = new LoopInvariantOptimization();
        optimization.optimize(graph, null);

        // make assertions
        assertEquals(block2, cond2.getBlock());
        assertEquals(block2, phi21.getBlock());
        assertEquals(block2, phi22.getBlock());
        assertEquals(block2, phi23.getBlock());

        assertEquals(block3, cond3.getBlock());
        assertEquals(block3, phi31.getBlock());
        assertEquals(block3, phi32.getBlock());
        assertEquals(block3, phi33.getBlock());

        assertEquals(block1, add4.getBlock());
        assertEquals(block2, sub4.getBlock());
        assertEquals(block4, mul4.getBlock());
    }

}
