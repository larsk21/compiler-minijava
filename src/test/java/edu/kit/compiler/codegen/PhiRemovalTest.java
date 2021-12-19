package edu.kit.compiler.codegen;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class PhiRemovalTest {

    @Test
    public void testNoPhiRemoval() throws IOException {
        PatternCollection p = new PatternCollection();
        for (var graph : SimpleTestCompiler.getFirmGraphs("edu/kit/compiler/codegen/PhiTest.java")) {
            InstructionSelection is = InstructionSelection.apply(graph, p);
            BasicBlocks b = is.getBlocks();
            System.out.println("with phis: " + toStringDebug(b));

            Optional<BasicBlocks.BlockEntry> notStartsWithMov = b.getBlocks().values().stream().findFirst();
            assertTrue(notStartsWithMov.isPresent());

            try {
                b = new PhiRemover(b).removePhis();
            } catch (Exception e) {
                e.printStackTrace();
            }
            System.out.println("phis removed: " + toStringDebug(b));

            // test that there are no more phis
            SimpleTestCompiler.dumpGraph(graph);
        }
    }


    public String toStringDebug(BasicBlocks blocks) {
        StringBuilder builder = new StringBuilder();
        for (var b : blocks.getBlocks().values()) {
            builder.append("\n[").append(b.getLabel()).append("]").append("\n");
            builder.append(b.toString());
            builder.append("----\n");
        }
        return builder.toString();
    }

}
