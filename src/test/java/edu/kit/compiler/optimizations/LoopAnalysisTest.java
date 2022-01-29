package edu.kit.compiler.optimizations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import edu.kit.compiler.optimizations.analysis.LoopAnalysis;
import edu.kit.compiler.transform.JFirmSingleton;

import firm.Construction;
import firm.Entity;
import firm.Graph;
import firm.MethodType;
import firm.Mode;
import firm.Program;
import firm.TargetValue;
import firm.Type;
import firm.nodes.Block;
import firm.nodes.Cond;
import firm.nodes.Node;

public class LoopAnalysisTest {

    @BeforeAll
    public static void setupAll() {
        JFirmSingleton.initializeFirmLinux();
    }

    private Graph createGraph(Type[] returnTypes) {
        UUID uuid = UUID.randomUUID();
        MethodType methodType = new MethodType(new Type[] { }, returnTypes);
        Entity entity = new Entity(Program.getGlobalType(), "test_" + uuid, methodType);
        Graph graph = new Graph(entity, 0);

        return graph;
    }

    @Test
    public void testLinear() {
        // Start -> A -> End
        // =>
        // no loop

        // construct graph
        Graph graph = createGraph(new Type[] { });
        Construction construction = new Construction(graph);

        Block block1 = construction.getCurrentBlock();
        Node jmp1 = construction.newJmp();
        block1.mature();

        Block block2 = construction.newBlock();
        block2.addPred(jmp1);
        construction.setCurrentBlock(block2);
        Node return2 = construction.newReturn(construction.getCurrentMem(), new Node[] { });
        block2.mature();

        Block block3 = graph.getEndBlock();
        block3.addPred(return2);

        construction.finish();

        // run analysis
        LoopAnalysis analysis = new LoopAnalysis(graph);
        analysis.analyze();

        // make assertions
        assertTrue(analysis.getLoops().isEmpty());

        assertEquals(Set.of(), analysis.getBlockLoops().get(block2));
    }

    @Test
    public void testCond() {
        //       -> A ->
        // Start         C -> End
        //       -> B ->
        // =>
        // no loop

        // construct graph
        Graph graph = createGraph(new Type[] { });
        Construction construction = new Construction(graph);

        Block block1 = construction.getCurrentBlock();
        Node const1 = construction.newConst(TargetValue.getBTrue());
        Node cond1 = construction.newCond(const1);
        Node proj11 = construction.newProj(cond1, Mode.getX(), Cond.pnFalse);
        Node proj12 = construction.newProj(cond1, Mode.getX(), Cond.pnTrue);
        block1.mature();

        Block block2 = construction.newBlock();
        block2.addPred(proj11);
        construction.setCurrentBlock(block2);
        Node jmp2 = construction.newJmp();
        block2.mature();

        Block block3 = construction.newBlock();
        block3.addPred(proj12);
        construction.setCurrentBlock(block3);
        Node jmp3 = construction.newJmp();
        block3.mature();

        Block block4 = construction.newBlock();
        block4.addPred(jmp2);
        block4.addPred(jmp3);
        construction.setCurrentBlock(block4);
        Node return4 = construction.newReturn(construction.getCurrentMem(), new Node[] { });
        block4.mature();

        Block block5 = graph.getEndBlock();
        block5.addPred(return4);

        construction.finish();

        // run analysis
        LoopAnalysis analysis = new LoopAnalysis(graph);
        analysis.analyze();

        // make assertions
        assertTrue(analysis.getLoops().isEmpty());

        Map<Block, Set<Block>> blockLoops = analysis.getBlockLoops();
        assertEquals(Set.of(), blockLoops.get(block3));
        assertEquals(Set.of(), blockLoops.get(block4));
    }

    @Test
    public void testLoopEmpty() {
        //          <->
        // Start -> A    B -> End
        //            ->
        // =>
        // loop header: A
        // loop body: <empty>

        // construct graph
        Graph graph = createGraph(new Type[] { });
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
        block2.addPred(proj22);
        block2.mature();

        Block block3 = construction.newBlock();
        block3.addPred(proj21);
        construction.setCurrentBlock(block3);
        Node return3 = construction.newReturn(construction.getCurrentMem(), new Node[] { });
        block3.mature();

        Block block4 = graph.getEndBlock();
        block4.addPred(return3);

        construction.finish();

        // run analysis
        LoopAnalysis analysis = new LoopAnalysis(graph);
        analysis.analyze();

        Map<Block, Set<Block>> loops = analysis.getLoops();

        // make assertions
        assertEquals(1, loops.size());

        assertTrue(loops.containsKey(block2));
        assertEquals(Set.of(block2), loops.get(block2));

        Map<Block, Set<Block>> blockLoops = analysis.getBlockLoops();
        assertEquals(Set.of(block2), blockLoops.get(block2));
        assertEquals(Set.of(), blockLoops.get(block3));
    }

    @Test
    public void testLoopSingle() {
        //            <-> B
        // Start -> A
        //             -> C -> End
        // =>
        // loop header: A
        // loop body: B

        // construct graph
        Graph graph = createGraph(new Type[] { });
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

        Block block3 = construction.newBlock();
        block3.addPred(proj22);
        construction.setCurrentBlock(block3);
        Node jmp3 = construction.newJmp();
        block3.mature();

        block2.addPred(jmp3);
        block2.mature();

        Block block4 = construction.newBlock();
        block4.addPred(proj21);
        construction.setCurrentBlock(block4);
        Node return4 = construction.newReturn(construction.getCurrentMem(), new Node[] { });
        block4.mature();

        Block block5 = graph.getEndBlock();
        block5.addPred(return4);

        construction.finish();

        // run analysis
        LoopAnalysis analysis = new LoopAnalysis(graph);
        analysis.analyze();

        Map<Block, Set<Block>> loops = analysis.getLoops();

        // make assertions
        assertEquals(1, loops.size());

        assertTrue(loops.containsKey(block2));
        assertEquals(Set.of(block2, block3), loops.get(block2));

        Map<Block, Set<Block>> blockLoops = analysis.getBlockLoops();
        assertEquals(Set.of(block2), blockLoops.get(block2));
        assertEquals(Set.of(), blockLoops.get(block4));
    }

    @Test
    public void testLoopMultiple() {
        //            <------
        //            -> B -> C
        // Start -> A
        //            -> D -> End
        // =>
        // loop header: A
        // loop body: B, C

        // construct graph
        Graph graph = createGraph(new Type[] { });
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

        Block block3 = construction.newBlock();
        block3.addPred(proj22);
        construction.setCurrentBlock(block3);
        Node jmp3 = construction.newJmp();
        block3.mature();

        Block block4 = construction.newBlock();
        block4.addPred(jmp3);
        construction.setCurrentBlock(block4);
        Node jmp4 = construction.newJmp();
        block4.mature();

        block2.addPred(jmp4);
        block2.mature();

        Block block5 = construction.newBlock();
        block5.addPred(proj21);
        construction.setCurrentBlock(block5);
        Node return5 = construction.newReturn(construction.getCurrentMem(), new Node[] { });
        block5.mature();

        Block block6 = graph.getEndBlock();
        block6.addPred(return5);

        construction.finish();

        // run analysis
        LoopAnalysis analysis = new LoopAnalysis(graph);
        analysis.analyze();

        Map<Block, Set<Block>> loops = analysis.getLoops();

        // make assertions
        assertEquals(1, loops.size());

        assertTrue(loops.containsKey(block2));
        assertEquals(Set.of(block2, block3, block4), loops.get(block2));

        Map<Block, Set<Block>> blockLoops = analysis.getBlockLoops();
        assertEquals(Set.of(block2), blockLoops.get(block2));
        assertEquals(Set.of(block2), blockLoops.get(block3));
    }

    @Test
    public void testLoopCond() {
        //                 -> C ->
        //            -> B         E
        //                 -> D ->
        //            <-----------
        // Start -> A
        //            -> F -> End
        // =>
        // loop header: A
        // loop body: B, C, D, E

        // construct graph
        Graph graph = createGraph(new Type[] { });
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

        Block block3 = construction.newBlock();
        block3.addPred(proj22);
        construction.setCurrentBlock(block3);
        Node const3 = construction.newConst(TargetValue.getBFalse());
        Node cond3 = construction.newCond(const3);
        Node proj31 = construction.newProj(cond3, Mode.getX(), Cond.pnFalse);
        Node proj32 = construction.newProj(cond3, Mode.getX(), Cond.pnTrue);
        block3.mature();

        Block block4 = construction.newBlock();
        block4.addPred(proj31);
        construction.setCurrentBlock(block4);
        Node jmp4 = construction.newJmp();
        block4.mature();

        Block block5 = construction.newBlock();
        block5.addPred(proj32);
        construction.setCurrentBlock(block5);
        Node jmp5 = construction.newJmp();
        block5.mature();

        Block block6 = construction.newBlock();
        block6.addPred(jmp4);
        block6.addPred(jmp5);
        construction.setCurrentBlock(block6);
        Node jmp6 = construction.newJmp();
        block6.mature();

        block2.addPred(jmp6);
        block2.mature();

        Block block7 = construction.newBlock();
        block7.addPred(proj21);
        construction.setCurrentBlock(block7);
        Node return7 = construction.newReturn(construction.getCurrentMem(), new Node[] { });
        block7.mature();

        Block block8 = graph.getEndBlock();
        block8.addPred(return7);

        construction.finish();

        // run analysis
        LoopAnalysis analysis = new LoopAnalysis(graph);
        analysis.analyze();

        Map<Block, Set<Block>> loops = analysis.getLoops();

        // make assertions
        assertEquals(1, loops.size());

        assertTrue(loops.containsKey(block2));
        assertEquals(Set.of(block2, block3, block4, block5, block6), loops.get(block2));

        Map<Block, Set<Block>> blockLoops = analysis.getBlockLoops();
        assertEquals(Set.of(block2), blockLoops.get(block4));
        assertEquals(Set.of(block2), blockLoops.get(block5));
        assertEquals(Set.of(block2), blockLoops.get(block6));
    }

    @Test
    public void testLoopCondDirect() {
        //            <--------
        //                 -> C
        //            -> B
        //                 -> D
        //            <--------
        // Start -> A
        //            -> E -> End
        // =>
        // loop header: A
        // loop body: B, C, D

        // construct graph
        Graph graph = createGraph(new Type[] { });
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

        Block block3 = construction.newBlock();
        block3.addPred(proj22);
        construction.setCurrentBlock(block3);
        Node const3 = construction.newConst(TargetValue.getBFalse());
        Node cond3 = construction.newCond(const3);
        Node proj31 = construction.newProj(cond3, Mode.getX(), Cond.pnFalse);
        Node proj32 = construction.newProj(cond3, Mode.getX(), Cond.pnTrue);
        block3.mature();

        Block block4 = construction.newBlock();
        block4.addPred(proj31);
        construction.setCurrentBlock(block4);
        Node jmp4 = construction.newJmp();
        block4.mature();

        Block block5 = construction.newBlock();
        block5.addPred(proj32);
        construction.setCurrentBlock(block5);
        Node jmp5 = construction.newJmp();
        block5.mature();

        block2.addPred(jmp4);
        block2.addPred(jmp5);
        block2.mature();

        Block block6 = construction.newBlock();
        block6.addPred(proj21);
        construction.setCurrentBlock(block6);
        Node return6 = construction.newReturn(construction.getCurrentMem(), new Node[] { });
        block6.mature();

        Block block7 = graph.getEndBlock();
        block7.addPred(return6);

        construction.finish();

        // run analysis
        LoopAnalysis analysis = new LoopAnalysis(graph);
        analysis.analyze();

        Map<Block, Set<Block>> loops = analysis.getLoops();

        // make assertions
        assertEquals(1, loops.size());

        assertTrue(loops.containsKey(block2));
        assertEquals(Set.of(block2, block3, block4, block5), loops.get(block2));

        Map<Block, Set<Block>> blockLoops = analysis.getBlockLoops();
        assertEquals(Set.of(block2), blockLoops.get(block4));
        assertEquals(Set.of(block2), blockLoops.get(block5));
    }

    @Test
    public void testLoopLoop() {
        //                 -> C -> D
        //            -> B <------
        //                 -> E
        //            <------
        // Start -> A
        //            -> F -> End
        // =>
        // loop header: A
        // loop body: B, C, D, E
        //
        // loop header: B
        // loop body C, D

        // construct graph
        Graph graph = createGraph(new Type[] { });
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
        Node jmp4 = construction.newJmp();
        block4.mature();

        Block block5 = construction.newBlock();
        block5.addPred(jmp4);
        construction.setCurrentBlock(block5);
        Node jmp5 = construction.newJmp();
        block5.mature();

        block3.addPred(jmp5);
        block3.mature();

        Block block6 = construction.newBlock();
        block6.addPred(proj31);
        construction.setCurrentBlock(block6);
        Node jmp6 = construction.newJmp();
        block6.mature();

        block2.addPred(jmp6);
        block2.mature();

        Block block7 = construction.newBlock();
        block7.addPred(proj21);
        construction.setCurrentBlock(block7);
        Node return7 = construction.newReturn(construction.getCurrentMem(), new Node[] { });
        block7.mature();

        Block block8 = graph.getEndBlock();
        block8.addPred(return7);

        construction.finish();

        // run analysis
        LoopAnalysis analysis = new LoopAnalysis(graph);
        analysis.analyze();

        Map<Block, Set<Block>> loops = analysis.getLoops();

        // make assertions
        assertEquals(2, loops.size());

        assertTrue(loops.containsKey(block2));
        assertEquals(Set.of(block2, block3, block4, block5, block6), loops.get(block2));

        assertTrue(loops.containsKey(block3));
        assertEquals(Set.of(block3, block4, block5), loops.get(block3));

        Map<Block, Set<Block>> blockLoops = analysis.getBlockLoops();
        assertEquals(Set.of(block2, block3), blockLoops.get(block4));
        assertEquals(Set.of(block2), blockLoops.get(block6));
    }

    @Test
    public void testLoopLoopDirect() {
        //                  -> C -> D
        //            <-> B <------
        // Start -> A
        //             -> E -> End
        // =>
        // loop header: A
        // loop body: B, C, D
        //
        // loop header: B
        // loop body C, D

        // construct graph
        Graph graph = createGraph(new Type[] { });
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

        Block block3 = construction.newBlock();
        block3.addPred(proj22);
        construction.setCurrentBlock(block3);
        Node const3 = construction.newConst(TargetValue.getBFalse());
        Node cond3 = construction.newCond(const3);
        Node proj31 = construction.newProj(cond3, Mode.getX(), Cond.pnFalse);
        Node proj32 = construction.newProj(cond3, Mode.getX(), Cond.pnTrue);

        block2.addPred(proj31);
        block2.mature();

        Block block4 = construction.newBlock();
        block4.addPred(proj32);
        construction.setCurrentBlock(block4);
        Node jmp4 = construction.newJmp();
        block4.mature();

        Block block5 = construction.newBlock();
        block5.addPred(jmp4);
        construction.setCurrentBlock(block5);
        Node jmp5 = construction.newJmp();
        block5.mature();

        block3.addPred(jmp5);
        block3.mature();

        Block block6 = construction.newBlock();
        block6.addPred(proj21);
        construction.setCurrentBlock(block6);
        Node return6 = construction.newReturn(construction.getCurrentMem(), new Node[] { });
        block6.mature();

        Block block7 = graph.getEndBlock();
        block7.addPred(return6);

        construction.finish();

        // run analysis
        LoopAnalysis analysis = new LoopAnalysis(graph);
        analysis.analyze();

        Map<Block, Set<Block>> loops = analysis.getLoops();

        // make assertions
        assertEquals(2, loops.size());

        assertTrue(loops.containsKey(block2));
        assertEquals(Set.of(block2, block3, block4, block5), loops.get(block2));

        assertTrue(loops.containsKey(block3));
        assertEquals(Set.of(block3, block4, block5), loops.get(block3));

        Map<Block, Set<Block>> blockLoops = analysis.getBlockLoops();
        assertEquals(Set.of(block2), blockLoops.get(block2));
        assertEquals(Set.of(block2, block3), blockLoops.get(block3));
        assertEquals(Set.of(block2, block3), blockLoops.get(block4));
    }

}
