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
        expected.add("movq 4(%rax), %r8");
        expected.add("movq %r8, -8(%rbp) # spill for @1");
        expected.add("incrl %r8");
        expected.add("movq %r8, -8(%rbp) # spill for @1");
        expected.add("movq -8(%rbp), %r8 # reload for @1");
        expected.add("addl %rax, %r8, %r9");
        expected.add("movq %r9, -16(%rbp) # spill for @2");
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
        expected.add("mov %rbx, %r8 # move for @1 [overwrite]");
        expected.add("xorl %rax, %r8");
        expected.add("movq %r8, -8(%rbp) # spill for @3");
        expected.add("subl %rax, %rbx");
        expected.add("movq -16(%rbp), %rbx # reload for @5 [overwrite]");
        expected.add("addl %rax, %rbx");
        expected.add("movq -16(%rbp), %r8 # reload for @5 [overwrite]");
        expected.add("xorl %rax, %r8");
        expected.add("movq %r8, -8(%rbp) # spill for @3");
        assertEquals(expected, result.getInstructions());
        // check that temporary registers are marked as used
        assert result.getUsedRegisters().contains(Register.R8);
    }

    @Test
    public void testDiv() {
        RegisterAssignment[] assignment = new RegisterAssignment[] {
                new RegisterAssignment(Register.RAX),
                new RegisterAssignment(Register.R8),
                new RegisterAssignment(Register.R9),
                new RegisterAssignment(-8),
                new RegisterAssignment(-16),
        };
        RegisterSize[] sizes = new RegisterSize[] {
                RegisterSize.DOUBLE,
                RegisterSize.DOUBLE,
                RegisterSize.DOUBLE,
                RegisterSize.DOUBLE,
                RegisterSize.DOUBLE,
        };
        Lifetime[] lifetimes = new Lifetime[] {
                new Lifetime(-1, 1, true),
                new Lifetime(-1, 4),
                new Lifetime(-1, 3, true),
                new Lifetime(-1, 4),
                new Lifetime(-1, 4),
        };
        Instruction[] ir = new Instruction[] {
                Instruction.newDiv(0, 1, 2),
                Instruction.newDiv(1, 3, 4),
                Instruction.newMod(4, 2, 1),
                Instruction.newDiv(1, 3, 4),
        };
        ApplyAssignment ass = new ApplyAssignment(assignment, sizes, lifetimes, Arrays.asList(ir));
        var result = ass.doApply(logger);
        var expected = new ArrayList<>();
        expected.add("movslq %eax, %rax # get dividend");
        expected.add("cqto # sign extension to octoword");
        expected.add("movslq %r8d, %r10 # get divisor");
        expected.add("idivq %r10");
        expected.add("leal 0(%rax), %r9d # get result of division");

        expected.add("movslq %r8d, %rax # get dividend");
        expected.add("cqto # sign extension to octoword");
        expected.add("movslq -8(%rbp), %r10 # get divisor");
        expected.add("idivq %r10");
        expected.add("leal 0(%rax), %eax # get result of division");
        expected.add("movl %eax, -16(%rbp) # spill for @4");

        expected.add("movslq -16(%rbp), %rax # get dividend");
        expected.add("cqto # sign extension to octoword");
        expected.add("movslq %r9d, %r9 # get divisor");
        expected.add("idivq %r9");
        expected.add("leal 0(%rdx), %r8d # get result of division");

        expected.add("movslq %r8d, %rax # get dividend");
        expected.add("cqto # sign extension to octoword");
        expected.add("movslq -8(%rbp), %r9 # get divisor");
        expected.add("idivq %r9");
        expected.add("leal 0(%rax), %eax # get result of division");
        expected.add("movl %eax, -16(%rbp) # spill for @4");
        assertEquals(expected, result.getInstructions());
        // check that temporary registers are marked as used
        assert result.getUsedRegisters().contains(Register.RDX);
    }
}
