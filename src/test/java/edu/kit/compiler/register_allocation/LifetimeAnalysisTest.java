package edu.kit.compiler.register_allocation;

import edu.kit.compiler.intermediate_lang.Block;
import edu.kit.compiler.intermediate_lang.Instruction;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LifetimeAnalysisTest {
    @Test
    public void testSimpleLoop() {
        Block start = new Block(List.of(
                Instruction.newOp("addl $77, @1", List.of(), Optional.of(0), 1),
                Instruction.newOp("mull $2, @2", List.of(), Optional.of(1), 2),
                Instruction.newJmp("jmp .L1", 1)
        ), 0, 0);
        Block loopHead = new Block(List.of(
                Instruction.newInput("cmp @1, @2", List.of(1, 2)),
                Instruction.newJmp("jl .L2", 2),
                Instruction.newJmp("jmp .L3", 3)
        ), 1, 1);
        loopHead.setBlockLoopDepth(1);
        Block loopBody = new Block(List.of(
                Instruction.newOp("addl $1, @3", List.of(), Optional.of(1), 3),
                Instruction.newOp("mov @3, @1", List.of(3), Optional.empty(), 1),
                Instruction.newCall(List.of(1), Optional.empty(), "foo"),
                Instruction.newJmp("jmp .L1", 1)
        ), 2, 0);
        loopBody.setBlockLoopDepth(1);
        Block end = new Block(List.of(
                Instruction.newOp("addl $7, @2", List.of(), Optional.of(0), 2),
                Instruction.newRet(Optional.of(2))
        ), 3, 0);
        List<Block> ir = List.of(start, loopHead, loopBody, end);

        LifetimeAnalysis analysis = LifetimeAnalysis.run(ir, 4, 1);

        assertEquals(-1, analysis.getLifetime(0).getBegin());
        assertEquals(11, analysis.getLifetime(0).getEnd());
        assertEquals(true, analysis.getLifetime(0).isLastInstrIsInput());
        assertEquals(0, analysis.getLoopDepth(0));
        assertEquals(1, analysis.numInterferingCalls(0, false));

        assertEquals(0, analysis.getLifetime(1).getBegin());
        assertEquals(10, analysis.getLifetime(1).getEnd());
        assertEquals(false, analysis.getLifetime(1).isLastInstrIsInput());
        assertEquals(1, analysis.getLoopDepth(1));
        assertEquals(1, analysis.numInterferingCalls(1, false));

        assertEquals(1, analysis.getLifetime(2).getBegin());
        assertEquals(12, analysis.getLifetime(2).getEnd());
        assertEquals(true, analysis.getLifetime(2).isLastInstrIsInput());
        assertEquals(1, analysis.getLoopDepth(2));
        assertEquals(1, analysis.numInterferingCalls(2, false));

        assertEquals(6, analysis.getLifetime(3).getBegin());
        assertEquals(8, analysis.getLifetime(3).getEnd());
        assertEquals(true, analysis.getLifetime(3).isLastInstrIsInput());
        assertEquals(1, analysis.getLoopDepth(3));
        assertEquals(0, analysis.numInterferingCalls(3, false));
    }
}
