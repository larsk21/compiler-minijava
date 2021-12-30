package edu.kit.compiler.intermediate_lang;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class InstructionTest {
    @Test
    public void testSimpleReplacement() {
        Instruction instr = Instruction.newOp("mov @0, @1", List.of(0), Optional.empty(), 1);
        Map<Integer, String> mapping = Map.of(0, "0", 1, "1");
        assertEquals("mov 0, 1", instr.mapRegisters(mapping));
    }

    @Test
    public void testReplacementWithPrefix() {
        Instruction instr = Instruction.newInput("op @1, @10, @100, @1000", List.of(1, 10, 100, 1000));
        Map<Integer, String> mapping = Map.of(1, "1", 10, "2", 100, "3", 1000, "4");
        assertEquals("op 1, 2, 3, 4", instr.mapRegisters(mapping));
    }
}
