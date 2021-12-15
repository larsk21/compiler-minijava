package edu.kit.compiler.codegen;

import org.junit.jupiter.api.Test;

import java.io.IOException;

public class PhiRemovalTest {

    @Test
    public void testNoPhiRemoval() throws IOException {
        Patterns p = new Patterns();
        for (var graph : SimpleTestCompiler.getFirmGraphs("edu/kit/compiler/codegen/PhiTest.java")) {
            InstructionSelection is = InstructionSelection.apply(graph, p);
            System.out.println(is.getBlocks());
            System.out.println(is.getRegisters());

            SimpleTestCompiler.dumpGraph(graph);
        }
    }
}
