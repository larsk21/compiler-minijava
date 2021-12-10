package edu.kit.compiler.register_allocation;

import edu.kit.compiler.intermediate_lang.Instruction;
import edu.kit.compiler.intermediate_lang.Register;
import edu.kit.compiler.intermediate_lang.RegisterSize;
import edu.kit.compiler.logger.Logger;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ApplyAssignmentTest {
    private Logger logger = new Logger(Logger.Verbosity.DEBUG, true);

    @Test
    public void testTrivialInit() {
        RegisterAssignment[] assignment = new RegisterAssignment[] {
          new RegisterAssignment(Register.RAX)
        };
        RegisterSize[] sizes = new RegisterSize[] {
          RegisterSize.QUAD
        };
        Lifetime[] lifetimes = new Lifetime[] {
          new Lifetime(0, 1)
        };
        Instruction[] ir = new Instruction[] {
            Instruction.newOp("xorl @0, @0", new int[] {}, Optional.empty(), 0)
        };
        ApplyAssignment ass = new ApplyAssignment(assignment, sizes, lifetimes, Arrays.asList(ir));
        ass.testRun(logger);
    }

    @Test
    public void testTrivialRun() {
        RegisterAssignment[] assignment = new RegisterAssignment[] {
                new RegisterAssignment(Register.RAX)
        };
        RegisterSize[] sizes = new RegisterSize[] {
                RegisterSize.QUAD
        };
        Lifetime[] lifetimes = new Lifetime[] {
                new Lifetime(-1, 1)
        };
        Instruction[] ir = new Instruction[] {
                Instruction.newInput("movq $0, 0(@0)", new int[] { 0 })
        };
        ApplyAssignment ass = new ApplyAssignment(assignment, sizes, lifetimes, Arrays.asList(ir));
        var result = ass.doApply(logger);
        for (var line: result.getInstructions()) {
            logger.info("%s", line);
        }
    }

    @Test
    public void testLongerRun() {
        RegisterAssignment[] assignment = new RegisterAssignment[] {
                new RegisterAssignment(Register.RAX),
                new RegisterAssignment(-8),
                new RegisterAssignment(-16),
        };
        RegisterSize[] sizes = new RegisterSize[] {
                RegisterSize.QUAD,
                RegisterSize.QUAD,
                RegisterSize.QUAD,
        };
        Lifetime[] lifetimes = new Lifetime[] {
                new Lifetime(-1, 3),
                new Lifetime(0, 3),
                new Lifetime(-1, 3),
        };
        Instruction[] ir = new Instruction[] {
                Instruction.newOp("movq 4(@0), @1", new int[] { 0 }, Optional.empty(), 1),
                Instruction.newOp("incrl @1", new int[] { }, Optional.empty(), 1),
                Instruction.newOp("addl @0, @1, @2", new int[] { 0, 1 }, Optional.empty(), 2),
        };
        ApplyAssignment ass = new ApplyAssignment(assignment, sizes, lifetimes, Arrays.asList(ir));
        var result = ass.doApply(logger);
        var expected = new ArrayList<>();
        expected.add("movq 4(%rax), %rbx");
        expected.add("movq %rbx, -8(%rbp) # spill for @1");
        expected.add("incrl %rbx");
        expected.add("movq %rbx, -8(%rbp) # spill for @1");
        expected.add("movq -8(%rbp), %rbx # reload for @1");
        expected.add("addl %rax, %rbx, %rcx");
        expected.add("movq %rcx, -16(%rbp) # spill for @2");
        assertEquals(expected, result.getInstructions());
    }

    @Test
    public void testOverwrite() {
        RegisterAssignment[] assignment = new RegisterAssignment[] {
                new RegisterAssignment(Register.RAX),
                new RegisterAssignment(Register.RBX),
                new RegisterAssignment(Register.RCX),
                new RegisterAssignment(-8),
                new RegisterAssignment(Register.RBX),
                new RegisterAssignment(-16),
        };
        RegisterSize[] sizes = new RegisterSize[] {
                RegisterSize.QUAD,
                RegisterSize.QUAD,
                RegisterSize.QUAD,
                RegisterSize.QUAD,
                RegisterSize.QUAD,
                RegisterSize.QUAD,
        };
        Lifetime[] lifetimes = new Lifetime[] {
                new Lifetime(-1, 5),
                new Lifetime(-1, 2, true),
                new Lifetime(-1, 5),
                new Lifetime(-1, 5),
                new Lifetime(2, 5),
                new Lifetime(-1, 5),
        };
        Instruction[] ir = new Instruction[] {
                Instruction.newOp("addl @0, @2", new int[] { 0 }, Optional.of(1), 2),
                Instruction.newOp("xorl @0, @3", new int[] { 0 }, Optional.of(1), 3),
                Instruction.newOp("subl @0, @4", new int[] { 0 }, Optional.of(1), 4),
                Instruction.newOp("addl @0, @4", new int[] { 0 }, Optional.of(5), 4),
                Instruction.newOp("xorl @0, @3", new int[] { 0 }, Optional.of(5), 3),
        };
        ApplyAssignment ass = new ApplyAssignment(assignment, sizes, lifetimes, Arrays.asList(ir));
        var result = ass.doApply(logger);
        var expected = new ArrayList<>();
        expected.add("mov %rbx, %rcx # move for @1 [overwrite]");
        expected.add("addl %rax, %rcx");
        expected.add("mov %rbx, %rdx # move for @1 [overwrite]");
        expected.add("xorl %rax, %rdx");
        expected.add("movq %rdx, -8(%rbp) # spill for @3");
        expected.add("subl %rax, %rbx");
        expected.add("movq -16(%rbp), %rbx # reload for @5 [overwrite]");
        expected.add("addl %rax, %rbx");
        expected.add("movq -16(%rbp), %rdx # reload for @5 [overwrite]");
        expected.add("xorl %rax, %rdx");
        expected.add("movq %rdx, -8(%rbp) # spill for @3");
        assertEquals(expected, result.getInstructions());
    }
}
