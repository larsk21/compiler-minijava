package edu.kit.compiler.optimizations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import edu.kit.compiler.transform.JFirmSingleton;

import firm.BlockWalker;
import firm.Construction;
import firm.Entity;
import firm.Graph;
import firm.MethodType;
import firm.Mode;
import firm.PrimitiveType;
import firm.Program;
import firm.TargetValue;
import firm.Type;
import firm.bindings.binding_irnode.ir_opcode;
import firm.nodes.Block;
import firm.nodes.Cond;
import firm.nodes.Node;
import firm.nodes.NodeVisitor;

public class LinearBlocksOptimizationTest {

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

    private List<Node> getNodes(Graph graph) {
        List<Node> nodes = new ArrayList<>();
        NodeVisitor nodeVisitor = new NodeVisitor.Default() {

            @Override
            public void defaultVisit(Node node) {
                nodes.add(node);
            }

        };
        graph.walkPostorder(nodeVisitor);

        return nodes;
    }

    private List<Block> getBlocks(Graph graph) {
        List<Block> blocks = new ArrayList<>();
        BlockWalker blockWalker = new BlockWalker() {
            @Override
            public void visitBlock(Block block) {
                blocks.add(block);
            }
        };
        graph.walkBlocksPostorder(blockWalker);

        return blocks;
    }

    private void assertContainsOpCode(List<Node> nodes, ir_opcode opcode) {
        assertTrue(nodes.stream().anyMatch(node -> node.getOpCode() == opcode));
    }

    private void assertDoesNotContainOpCode(List<Node> nodes, ir_opcode opcode) {
        assertFalse(nodes.stream().anyMatch(node -> node.getOpCode() == opcode));
    }

    @Test
    public void testLinearEmpty() {
        // Start -> A -> End
        // =>
        // Start -> End

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

        // run optimization
        LinearBlocksOptimization optimization = new LinearBlocksOptimization();
        optimization.optimize(graph, null);

        // make assertions
        List<Block> blocks = getBlocks(graph);
        assertEquals(2, blocks.size());

        assertEquals(block1, graph.getStartBlock());
        assertEquals(block3, graph.getEndBlock());

        List<Node> nodes = getNodes(graph);
        assertDoesNotContainOpCode(nodes, ir_opcode.iro_Jmp);
    }

    @Test
    public void testLinear() {
        // Start -> A -> End
        // =>
        // Start -> End

        // construct graph
        Graph graph = createGraph(new Type[] { INT_TYPE });
        Construction construction = new Construction(graph);

        Block block1 = construction.getCurrentBlock();
        Node const1 = construction.newConst(new TargetValue(17, Mode.getIs()));
        Node minus1 = construction.newMinus(const1);
        Node jmp1 = construction.newJmp();
        block1.mature();

        Block block2 = construction.newBlock();
        block2.addPred(jmp1);
        construction.setCurrentBlock(block2);
        Node const2 = construction.newConst(new TargetValue(42, Mode.getIs()));
        Node add2 = construction.newAdd(minus1, const2);
        Node return2 = construction.newReturn(construction.getCurrentMem(), new Node[] { add2 });
        block2.mature();

        Block block3 = graph.getEndBlock();
        block3.addPred(return2);

        construction.finish();

        // run optimization
        LinearBlocksOptimization optimization = new LinearBlocksOptimization();
        optimization.optimize(graph, null);

        // make assertions
        List<Block> blocks = getBlocks(graph);
        assertEquals(2, blocks.size());

        assertEquals(block1, graph.getStartBlock());
        assertEquals(block3, graph.getEndBlock());

        List<Node> nodes = getNodes(graph);
        assertContainsOpCode(nodes, ir_opcode.iro_Minus);
        assertContainsOpCode(nodes, ir_opcode.iro_Add);
        assertDoesNotContainOpCode(nodes, ir_opcode.iro_Jmp);

        assertEquals(block1, minus1.getBlock());
        assertEquals(block1, add2.getBlock());
    }

    @Test
    public void testLinearMultiple() {
        // Start -> A -> B -> End
        // =>
        // Start -> End

        // construct graph
        Graph graph = createGraph(new Type[] { INT_TYPE });
        Construction construction = new Construction(graph);

        Block block1 = construction.getCurrentBlock();
        Node const1 = construction.newConst(new TargetValue(17, Mode.getIs()));
        Node minus1 = construction.newMinus(const1);
        Node jmp1 = construction.newJmp();
        block1.mature();

        Block block2 = construction.newBlock();
        block2.addPred(jmp1);
        construction.setCurrentBlock(block2);
        Node const2 = construction.newConst(new TargetValue(42, Mode.getIs()));
        Node add2 = construction.newAdd(minus1, const2);
        Node jmp2 = construction.newJmp();
        block2.mature();

        Block block3 = construction.newBlock();
        block3.addPred(jmp2);
        construction.setCurrentBlock(block3);
        Node const3 = construction.newConst(new TargetValue(71, Mode.getIs()));
        Node sub3 = construction.newSub(add2, const3);
        Node return3 = construction.newReturn(construction.getCurrentMem(), new Node[] { sub3 });
        block3.mature();

        Block block4 = graph.getEndBlock();
        block4.addPred(return3);

        construction.finish();

        // run optimization
        LinearBlocksOptimization optimization = new LinearBlocksOptimization();
        optimization.optimize(graph, null);

        // make assertions
        List<Block> blocks = getBlocks(graph);
        assertEquals(2, blocks.size());

        assertEquals(block1, graph.getStartBlock());
        assertEquals(block4, graph.getEndBlock());

        List<Node> nodes = getNodes(graph);
        assertContainsOpCode(nodes, ir_opcode.iro_Minus);
        assertContainsOpCode(nodes, ir_opcode.iro_Add);
        assertContainsOpCode(nodes, ir_opcode.iro_Sub);
        assertDoesNotContainOpCode(nodes, ir_opcode.iro_Jmp);

        assertEquals(block1, minus1.getBlock());
        assertEquals(block1, add2.getBlock());
        assertEquals(block1, sub3.getBlock());
    }

    @Test
    public void testMultiplePredecessorsMultipleSuccessors() {
        //       -> A ->
        // Start         C -> End
        //       -> B ->
        // =>
        // <unchanged>

        // construct graph
        Graph graph = createGraph(new Type[] { INT_TYPE });
        Construction construction = new Construction(graph);

        Block block1 = construction.getCurrentBlock();
        Node const11 = construction.newConst(new TargetValue(17, Mode.getIs()));
        Node minus1 = construction.newMinus(const11);
        Node const12 = construction.newConst(TargetValue.getBTrue());
        Node cond1 = construction.newCond(const12);
        Node proj11 = construction.newProj(cond1, Mode.getX(), Cond.pnFalse);
        Node proj12 = construction.newProj(cond1, Mode.getX(), Cond.pnTrue);
        block1.mature();

        Block block2 = construction.newBlock();
        block2.addPred(proj11);
        construction.setCurrentBlock(block2);
        Node const2 = construction.newConst(new TargetValue(42, Mode.getIs()));
        Node add2 = construction.newAdd(minus1, const2);
        Node jmp2 = construction.newJmp();
        block2.mature();

        Block block3 = construction.newBlock();
        block3.addPred(proj12);
        construction.setCurrentBlock(block3);
        Node const3 = construction.newConst(new TargetValue(71, Mode.getIs()));
        Node sub3 = construction.newSub(minus1, const3);
        Node jmp3 = construction.newJmp();
        block3.mature();

        Block block4 = construction.newBlock();
        block4.addPred(jmp2);
        block4.addPred(jmp3);
        construction.setCurrentBlock(block4);
        Node phi4 = construction.newPhi(new Node[] { add2, sub3 }, Mode.getIs());
        Node const4 = construction.newConst(new TargetValue(113, Mode.getIs()));
        Node mul4 = construction.newMul(phi4, const4);
        Node return4 = construction.newReturn(construction.getCurrentMem(), new Node[] { mul4 });
        block4.mature();

        Block block5 = graph.getEndBlock();
        block5.addPred(return4);

        construction.finish();

        // run optimization
        LinearBlocksOptimization optimization = new LinearBlocksOptimization();
        optimization.optimize(graph, null);

        // make assertions
        List<Block> blocks = getBlocks(graph);
        assertEquals(5, blocks.size());

        assertEquals(block1, graph.getStartBlock());
        assertEquals(block5, graph.getEndBlock());

        List<Node> nodes = getNodes(graph);
        assertContainsOpCode(nodes, ir_opcode.iro_Minus);
        assertContainsOpCode(nodes, ir_opcode.iro_Add);
        assertContainsOpCode(nodes, ir_opcode.iro_Sub);
        assertContainsOpCode(nodes, ir_opcode.iro_Mul);
        assertContainsOpCode(nodes, ir_opcode.iro_Cond);
        assertContainsOpCode(nodes, ir_opcode.iro_Jmp);
        assertContainsOpCode(nodes, ir_opcode.iro_Phi);

        assertEquals(block1, minus1.getBlock());
        assertEquals(block2, add2.getBlock());
        assertEquals(block3, sub3.getBlock());
        assertEquals(block4, mul4.getBlock());
    }

    @Test
    public void testMultiplePredecessorsMultipleSuccessorsEmpty() {
        //       -> A (empty) ->
        // Start                 C -> End
        //       -> B         ->
        // =>
        //         ->
        // Start         C -> End
        //       -> B ->

        // construct graph
        Graph graph = createGraph(new Type[] { INT_TYPE });
        Construction construction = new Construction(graph);

        Block block1 = construction.getCurrentBlock();
        Node const11 = construction.newConst(new TargetValue(17, Mode.getIs()));
        Node minus1 = construction.newMinus(const11);
        Node const12 = construction.newConst(TargetValue.getBTrue());
        Node cond1 = construction.newCond(const12);
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
        Node const3 = construction.newConst(new TargetValue(71, Mode.getIs()));
        Node sub3 = construction.newSub(minus1, const3);
        Node jmp3 = construction.newJmp();
        block3.mature();

        Block block4 = construction.newBlock();
        block4.addPred(jmp2);
        block4.addPred(jmp3);
        construction.setCurrentBlock(block4);
        Node phi4 = construction.newPhi(new Node[] { minus1, sub3 }, Mode.getIs());
        Node const4 = construction.newConst(new TargetValue(113, Mode.getIs()));
        Node mul4 = construction.newMul(phi4, const4);
        Node return4 = construction.newReturn(construction.getCurrentMem(), new Node[] { mul4 });
        block4.mature();

        Block block5 = graph.getEndBlock();
        block5.addPred(return4);

        construction.finish();

        // run optimization
        LinearBlocksOptimization optimization = new LinearBlocksOptimization();
        optimization.optimize(graph, null);

        // make assertions
        List<Block> blocks = getBlocks(graph);
        assertEquals(4, blocks.size());

        assertEquals(block1, graph.getStartBlock());
        assertEquals(block5, graph.getEndBlock());

        assertEquals(proj11, block4.getPred(0));

        List<Node> nodes = getNodes(graph);
        assertContainsOpCode(nodes, ir_opcode.iro_Minus);
        assertContainsOpCode(nodes, ir_opcode.iro_Sub);
        assertContainsOpCode(nodes, ir_opcode.iro_Mul);
        assertContainsOpCode(nodes, ir_opcode.iro_Cond);
        assertContainsOpCode(nodes, ir_opcode.iro_Jmp);
        assertContainsOpCode(nodes, ir_opcode.iro_Phi);

        assertEquals(block1, minus1.getBlock());
        assertEquals(block3, sub3.getBlock());
        assertEquals(block4, mul4.getBlock());
    }

    @Test
    public void testMultipleEqualPredecessorsMultipleEqualSuccessorsPhi() {
        //       ->
        // Start    C (Phi) -> End
        //       ->
        // =>
        // <unchanged>

        // construct graph
        Graph graph = createGraph(new Type[] { INT_TYPE });
        Construction construction = new Construction(graph);

        Block block1 = construction.getCurrentBlock();
        Node const11 = construction.newConst(new TargetValue(17, Mode.getIs()));
        Node minus1 = construction.newMinus(const11);
        Node const12 = construction.newConst(TargetValue.getBTrue());
        Node cond1 = construction.newCond(const12);
        Node proj11 = construction.newProj(cond1, Mode.getX(), Cond.pnFalse);
        Node proj12 = construction.newProj(cond1, Mode.getX(), Cond.pnTrue);
        block1.mature();

        Block block2 = construction.newBlock();
        block2.addPred(proj11);
        block2.addPred(proj12);
        construction.setCurrentBlock(block2);
        Node const21 = construction.newConst(new TargetValue(42, Mode.getIs()));
        Node const22 = construction.newConst(new TargetValue(71, Mode.getIs()));
        Node phi2 = construction.newPhi(new Node[] { const21, const22 }, Mode.getIs());
        Node add2 = construction.newAdd(minus1, phi2);
        Node return2 = construction.newReturn(construction.getCurrentMem(), new Node[] { add2 });
        block2.mature();

        Block block3 = graph.getEndBlock();
        block3.addPred(return2);

        construction.finish();

        // run optimization
        LinearBlocksOptimization optimization = new LinearBlocksOptimization();
        optimization.optimize(graph, null);

        // make assertions
        List<Block> blocks = getBlocks(graph);
        assertEquals(3, blocks.size());

        assertEquals(block1, graph.getStartBlock());
        assertEquals(block3, graph.getEndBlock());

        List<Node> nodes = getNodes(graph);
        assertContainsOpCode(nodes, ir_opcode.iro_Minus);
        assertContainsOpCode(nodes, ir_opcode.iro_Add);
        assertContainsOpCode(nodes, ir_opcode.iro_Cond);
        assertContainsOpCode(nodes, ir_opcode.iro_Phi);

        assertEquals(block1, minus1.getBlock());
        assertEquals(block2, add2.getBlock());
    }

    @Test
    public void testMultipleEqualPredecessorsMultipleEqualSuccessorsNoPhi() {
        //       ->
        // Start    C (no Phi) -> End
        //       ->
        // =>
        // Start -> End

        // construct graph
        Graph graph = createGraph(new Type[] { INT_TYPE });
        Construction construction = new Construction(graph);

        Block block1 = construction.getCurrentBlock();
        Node const11 = construction.newConst(new TargetValue(17, Mode.getIs()));
        Node minus1 = construction.newMinus(const11);
        Node const12 = construction.newConst(TargetValue.getBTrue());
        Node cond1 = construction.newCond(const12);
        Node proj11 = construction.newProj(cond1, Mode.getX(), Cond.pnFalse);
        Node proj12 = construction.newProj(cond1, Mode.getX(), Cond.pnTrue);
        block1.mature();

        Block block2 = construction.newBlock();
        block2.addPred(proj11);
        block2.addPred(proj12);
        construction.setCurrentBlock(block2);
        Node const2 = construction.newConst(new TargetValue(42, Mode.getIs()));
        Node add2 = construction.newAdd(minus1, const2);
        Node return2 = construction.newReturn(construction.getCurrentMem(), new Node[] { add2 });
        block2.mature();

        Block block3 = graph.getEndBlock();
        block3.addPred(return2);

        construction.finish();

        // run optimization
        LinearBlocksOptimization optimization = new LinearBlocksOptimization();
        optimization.optimize(graph, null);

        // make assertions
        List<Block> blocks = getBlocks(graph);
        assertEquals(2, blocks.size());

        assertEquals(block1, graph.getStartBlock());
        assertEquals(block3, graph.getEndBlock());

        List<Node> nodes = getNodes(graph);
        assertContainsOpCode(nodes, ir_opcode.iro_Minus);
        assertContainsOpCode(nodes, ir_opcode.iro_Add);
        assertDoesNotContainOpCode(nodes, ir_opcode.iro_Cond);

        assertEquals(block1, minus1.getBlock());
        assertEquals(block1, add2.getBlock());
    }

    @Test
    public void testMultipleEqualPredecessorsMultipleEqualSuccessorsNoPhiThenLinear() {
        //       ->
        // Start    C (no Phi) -> D -> End
        //       ->
        // =>
        // Start -> End

        // construct graph
        Graph graph = createGraph(new Type[] { INT_TYPE });
        Construction construction = new Construction(graph);

        Block block1 = construction.getCurrentBlock();
        Node const11 = construction.newConst(new TargetValue(17, Mode.getIs()));
        Node minus1 = construction.newMinus(const11);
        Node const12 = construction.newConst(TargetValue.getBTrue());
        Node cond1 = construction.newCond(const12);
        Node proj11 = construction.newProj(cond1, Mode.getX(), Cond.pnFalse);
        Node proj12 = construction.newProj(cond1, Mode.getX(), Cond.pnTrue);
        block1.mature();

        Block block2 = construction.newBlock();
        block2.addPred(proj11);
        block2.addPred(proj12);
        construction.setCurrentBlock(block2);
        Node const2 = construction.newConst(new TargetValue(42, Mode.getIs()));
        Node add2 = construction.newAdd(minus1, const2);
        Node jmp2 = construction.newJmp();
        block2.mature();

        Block block3 = construction.newBlock();
        block3.addPred(jmp2);
        construction.setCurrentBlock(block3);
        Node const3 = construction.newConst(new TargetValue(71, Mode.getIs()));
        Node sub3 = construction.newSub(add2, const3);
        Node return3 = construction.newReturn(construction.getCurrentMem(), new Node[] { sub3 });
        block3.mature();

        Block block4 = graph.getEndBlock();
        block4.addPred(return3);

        construction.finish();

        // run optimization
        LinearBlocksOptimization optimization = new LinearBlocksOptimization();
        optimization.optimize(graph, null);

        // make assertions
        List<Block> blocks = getBlocks(graph);
        assertEquals(2, blocks.size());

        assertEquals(block1, graph.getStartBlock());
        assertEquals(block4, graph.getEndBlock());

        List<Node> nodes = getNodes(graph);
        assertContainsOpCode(nodes, ir_opcode.iro_Minus);
        assertContainsOpCode(nodes, ir_opcode.iro_Add);
        assertContainsOpCode(nodes, ir_opcode.iro_Minus);
        assertDoesNotContainOpCode(nodes, ir_opcode.iro_Cond);

        assertEquals(block1, minus1.getBlock());
        assertEquals(block1, add2.getBlock());
        assertEquals(block1, sub3.getBlock());
    }

    @Test
    public void testMultiplePredecessorsMultipleSuccessorsBothEmptyNoPhi() {
        //       -> A (empty) ->
        // Start                 C (no Phi) -> End
        //       -> B (empty) ->
        // =>
        // Start -> End

        // construct graph
        Graph graph = createGraph(new Type[] { INT_TYPE });
        Construction construction = new Construction(graph);

        Block block1 = construction.getCurrentBlock();
        Node const11 = construction.newConst(new TargetValue(17, Mode.getIs()));
        Node minus1 = construction.newMinus(const11);
        Node const12 = construction.newConst(TargetValue.getBTrue());
        Node cond1 = construction.newCond(const12);
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
        Node const4 = construction.newConst(new TargetValue(113, Mode.getIs()));
        Node mul4 = construction.newMul(minus1, const4);
        Node return4 = construction.newReturn(construction.getCurrentMem(), new Node[] { mul4 });
        block4.mature();

        Block block5 = graph.getEndBlock();
        block5.addPred(return4);

        construction.finish();

        // run optimization
        LinearBlocksOptimization optimization = new LinearBlocksOptimization();
        optimization.optimize(graph, null);

        // make assertions
        List<Block> blocks = getBlocks(graph);
        assertEquals(2, blocks.size());

        assertEquals(block1, graph.getStartBlock());
        assertEquals(block5, graph.getEndBlock());

        List<Node> nodes = getNodes(graph);
        assertContainsOpCode(nodes, ir_opcode.iro_Minus);
        assertContainsOpCode(nodes, ir_opcode.iro_Mul);
        assertDoesNotContainOpCode(nodes, ir_opcode.iro_Cond);
        assertDoesNotContainOpCode(nodes, ir_opcode.iro_Jmp);

        assertEquals(block1, minus1.getBlock());
        assertEquals(block1, mul4.getBlock());
    }

    @Test
    public void testComplex() {
        //       -> A -> B ->
        // Start              D -> E -> End
        //       ->    C   ->
        // =>
        //       -> A ->
        // Start         D -> End
        //       -> C ->

        // construct graph
        Graph graph = createGraph(new Type[] { INT_TYPE });
        Construction construction = new Construction(graph);

        Block block1 = construction.getCurrentBlock();
        Node const11 = construction.newConst(new TargetValue(17, Mode.getIs()));
        Node minus1 = construction.newMinus(const11);
        Node const12 = construction.newConst(TargetValue.getBTrue());
        Node cond1 = construction.newCond(const12);
        Node proj11 = construction.newProj(cond1, Mode.getX(), Cond.pnFalse);
        Node proj12 = construction.newProj(cond1, Mode.getX(), Cond.pnTrue);
        block1.mature();

        Block block2 = construction.newBlock();
        block2.addPred(proj11);
        construction.setCurrentBlock(block2);
        Node const2 = construction.newConst(new TargetValue(42, Mode.getIs()));
        Node add2 = construction.newAdd(minus1, const2);
        Node jmp2 = construction.newJmp();
        block2.mature();

        Block block3 = construction.newBlock();
        block3.addPred(jmp2);
        construction.setCurrentBlock(block3);
        Node const3 = construction.newConst(new TargetValue(3, Mode.getIu()));
        Node shl3 = construction.newShl(add2, const3);
        Node jmp3 = construction.newJmp();
        block3.mature();

        Block block4 = construction.newBlock();
        block4.addPred(proj12);
        construction.setCurrentBlock(block4);
        Node const4 = construction.newConst(new TargetValue(71, Mode.getIs()));
        Node sub4 = construction.newSub(minus1, const4);
        Node jmp4 = construction.newJmp();
        block4.mature();

        Block block5 = construction.newBlock();
        block5.addPred(jmp3);
        block5.addPred(jmp4);
        construction.setCurrentBlock(block5);
        Node phi5 = construction.newPhi(new Node[] { shl3, sub4 }, Mode.getIs());
        Node const5 = construction.newConst(new TargetValue(113, Mode.getIs()));
        Node mul5 = construction.newMul(phi5, const5);
        Node jmp5 = construction.newJmp();
        block5.mature();

        Block block6 = construction.newBlock();
        block6.addPred(jmp5);
        construction.setCurrentBlock(block6);
        Node const6 = construction.newConst(new TargetValue(5, Mode.getIu()));
        Node shr6 = construction.newShr(mul5, const6);
        Node return6 = construction.newReturn(construction.getCurrentMem(), new Node[] { shr6 });
        block6.mature();

        Block block7 = graph.getEndBlock();
        block7.addPred(return6);

        construction.finish();

        // run optimization
        LinearBlocksOptimization optimization = new LinearBlocksOptimization();
        optimization.optimize(graph, null);

        // make assertions
        List<Block> blocks = getBlocks(graph);
        assertEquals(5, blocks.size());

        assertEquals(block1, graph.getStartBlock());
        assertEquals(block7, graph.getEndBlock());

        List<Node> nodes = getNodes(graph);
        assertContainsOpCode(nodes, ir_opcode.iro_Minus);
        assertContainsOpCode(nodes, ir_opcode.iro_Add);
        assertContainsOpCode(nodes, ir_opcode.iro_Shl);
        assertContainsOpCode(nodes, ir_opcode.iro_Sub);
        assertContainsOpCode(nodes, ir_opcode.iro_Mul);
        assertContainsOpCode(nodes, ir_opcode.iro_Shr);
        assertContainsOpCode(nodes, ir_opcode.iro_Cond);
        assertContainsOpCode(nodes, ir_opcode.iro_Jmp);
        assertContainsOpCode(nodes, ir_opcode.iro_Phi);

        assertEquals(block1, minus1.getBlock());
        assertEquals(block2, add2.getBlock());
        assertEquals(block2, shl3.getBlock());
        assertEquals(block4, sub4.getBlock());
        assertEquals(block5, mul5.getBlock());
        assertEquals(block5, shr6.getBlock());
    }

    @Test
    public void testLoop() {
        //            <-> B
        // Start -> A          End
        //             -> C ->
        // =>
        // <unchanged>

        // construct graph
        Graph graph = createGraph(new Type[] { INT_TYPE });
        Construction construction = new Construction(graph);

        Block block1 = construction.getCurrentBlock();
        Node const11 = construction.newConst(new TargetValue(17, Mode.getIs()));
        Node minus1 = construction.newMinus(const11);
        Node jmp1 = construction.newJmp();
        block1.mature();

        Block block2 = construction.newBlock();
        block2.addPred(jmp1);
        construction.setCurrentBlock(block2);
        Node const2 = construction.newConst(TargetValue.getBTrue());
        Node cond1 = construction.newCond(const2);
        Node proj11 = construction.newProj(cond1, Mode.getX(), Cond.pnFalse);
        Node proj12 = construction.newProj(cond1, Mode.getX(), Cond.pnTrue);

        Block block3 = construction.newBlock();
        block3.addPred(proj12);
        construction.setCurrentBlock(block3);
        Node const3 = construction.newConst(new TargetValue(42, Mode.getIs()));
        Node add3 = construction.newAdd(construction.newBad(Mode.getIs()), const3);
        Node jmp3 = construction.newJmp();
        block3.mature();

        block2.addPred(jmp3);
        construction.setCurrentBlock(block2);
        Node phi2 = construction.newPhi(new Node[] { minus1, add3 }, Mode.getIs());
        Node const22 = construction.newConst(new TargetValue(71, Mode.getIs()));
        Node sub2 = construction.newSub(phi2, const22);
        block2.mature();

        add3.setPred(0, sub2);

        Block block4 = construction.newBlock();
        block4.addPred(proj11);
        construction.setCurrentBlock(block4);
        Node const4 = construction.newConst(new TargetValue(71, Mode.getIs()));
        Node mul4 = construction.newMul(sub2, const4);
        Node return4 = construction.newReturn(construction.getCurrentMem(), new Node[] { mul4 });
        block4.mature();

        Block block5 = graph.getEndBlock();
        block5.addPred(return4);

        construction.finish();

        // run optimization
        LinearBlocksOptimization optimization = new LinearBlocksOptimization();
        optimization.optimize(graph, null);

        // make assertions
        List<Block> blocks = getBlocks(graph);
        assertEquals(5, blocks.size());

        assertEquals(block1, graph.getStartBlock());
        assertEquals(block5, graph.getEndBlock());

        assertEquals(jmp1, block2.getPred(0));
        assertEquals(jmp3, block2.getPred(1));
        assertEquals(proj12, block3.getPred(0));
        assertEquals(proj11, block4.getPred(0));

        List<Node> nodes = getNodes(graph);
        assertContainsOpCode(nodes, ir_opcode.iro_Minus);
        assertContainsOpCode(nodes, ir_opcode.iro_Add);
        assertContainsOpCode(nodes, ir_opcode.iro_Sub);
        assertContainsOpCode(nodes, ir_opcode.iro_Mul);
        assertContainsOpCode(nodes, ir_opcode.iro_Cond);
        assertContainsOpCode(nodes, ir_opcode.iro_Jmp);
        assertContainsOpCode(nodes, ir_opcode.iro_Phi);

        assertEquals(block1, minus1.getBlock());
        assertEquals(block2, sub2.getBlock());
        assertEquals(block3, add3.getBlock());
        assertEquals(block4, mul4.getBlock());
    }

    @Test
    public void testLoopEmpty() {
        //            <-> B (empty)
        // Start -> A                  End
        //             -> C         ->
        // =>
        //          <->
        // Start -> A                  End
        //             -> C         ->

        // construct graph
        Graph graph = createGraph(new Type[] { INT_TYPE });
        Construction construction = new Construction(graph);

        Block block1 = construction.getCurrentBlock();
        Node const11 = construction.newConst(new TargetValue(17, Mode.getIs()));
        Node minus1 = construction.newMinus(const11);
        Node jmp1 = construction.newJmp();
        block1.mature();

        Block block2 = construction.newBlock();
        block2.addPred(jmp1);
        construction.setCurrentBlock(block2);
        Node const2 = construction.newConst(TargetValue.getBTrue());
        Node cond1 = construction.newCond(const2);
        Node proj11 = construction.newProj(cond1, Mode.getX(), Cond.pnFalse);
        Node proj12 = construction.newProj(cond1, Mode.getX(), Cond.pnTrue);

        Block block3 = construction.newBlock();
        block3.addPred(proj12);
        construction.setCurrentBlock(block3);
        Node jmp3 = construction.newJmp();
        block3.mature();

        block2.addPred(jmp3);
        construction.setCurrentBlock(block2);
        Node phi2 = construction.newPhi(new Node[] { minus1, construction.newBad(Mode.getIs()) }, Mode.getIs());
        Node const22 = construction.newConst(new TargetValue(71, Mode.getIs()));
        Node sub2 = construction.newSub(phi2, const22);
        phi2.setPred(1, sub2);
        block2.mature();

        Block block4 = construction.newBlock();
        block4.addPred(proj11);
        construction.setCurrentBlock(block4);
        Node const4 = construction.newConst(new TargetValue(71, Mode.getIs()));
        Node mul4 = construction.newMul(sub2, const4);
        Node return4 = construction.newReturn(construction.getCurrentMem(), new Node[] { mul4 });
        block4.mature();

        Block block5 = graph.getEndBlock();
        block5.addPred(return4);

        construction.finish();

        // run optimization
        LinearBlocksOptimization optimization = new LinearBlocksOptimization();
        optimization.optimize(graph, null);

        // make assertions
        List<Block> blocks = getBlocks(graph);
        assertEquals(4, blocks.size());

        assertEquals(block1, graph.getStartBlock());
        assertEquals(block5, graph.getEndBlock());

        assertEquals(jmp1, block2.getPred(0));
        assertEquals(proj12, block2.getPred(1));
        assertEquals(proj11, block4.getPred(0));

        List<Node> nodes = getNodes(graph);
        assertContainsOpCode(nodes, ir_opcode.iro_Minus);
        assertContainsOpCode(nodes, ir_opcode.iro_Sub);
        assertContainsOpCode(nodes, ir_opcode.iro_Mul);
        assertContainsOpCode(nodes, ir_opcode.iro_Cond);
        assertContainsOpCode(nodes, ir_opcode.iro_Jmp);
        assertContainsOpCode(nodes, ir_opcode.iro_Phi);

        assertEquals(block1, minus1.getBlock());
        assertEquals(block2, sub2.getBlock());
        assertEquals(block4, mul4.getBlock());
    }

}
