package edu.kit.compiler.codegen;

import org.junit.jupiter.api.Test;

import java.io.IOException;

public class PhiRemovalTest {

    @Test
    public void testNoPhiRemoval() throws IOException { // WHO TF DID THIS SHIT
        PatternCollection p = new PatternCollection();
        for (var graph : SimpleTestCompiler.getFirmGraphs("edu/kit/compiler/codegen/PhiTest.java")) {
            InstructionSelection is = InstructionSelection.apply(graph, p);
            System.out.println(is.getBlocks());

            SimpleTestCompiler.dumpGraph(graph);
        }
    }
}
