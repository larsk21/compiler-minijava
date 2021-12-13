package edu.kit.compiler.register_allocation;

import edu.kit.compiler.intermediate_lang.Instruction;
import edu.kit.compiler.intermediate_lang.Register;
import edu.kit.compiler.intermediate_lang.RegisterSize;
import edu.kit.compiler.logger.Logger;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ApplyAssignmentTest {
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
        ass.doApply();
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
        var result = ass.doApply();
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
                new Lifetime(-1, 3, true),
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
        var result = ass.doApply();
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
        var result = ass.doApply();
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

    @Test
    public void testCompareToFirm() {
        RegisterAssignment[] assignment = new RegisterAssignment[] {
                new RegisterAssignment(Register.RCX),
                new RegisterAssignment(Register.RCX),
                new RegisterAssignment(Register.RBX),
                new RegisterAssignment(Register.RDI),
        };
        RegisterSize[] sizes = new RegisterSize[] {
                RegisterSize.DOUBLE,
                RegisterSize.DOUBLE,
                RegisterSize.DOUBLE,
                RegisterSize.DOUBLE,
        };
        Lifetime[] lifetimes = new Lifetime[] {
                new Lifetime(-1, 2, true),
                new Lifetime(1, 4, true),
                new Lifetime(2, 4, true),
                new Lifetime(3, 4),
        };
        Instruction[] ir = new Instruction[] {
                Instruction.newOp("movl $0x7, @0", new int[] { }, Optional.empty(), 0),
                Instruction.newOp("addl $77, @1", new int[] { }, Optional.of(0), 1),
                Instruction.newOp("movl $0x2, @2", new int[] { }, Optional.empty(), 2),
                Instruction.newDiv(1, 2, 3),
        };
        ApplyAssignment ass = new ApplyAssignment(assignment, sizes, lifetimes, Arrays.asList(ir));
        var result = ass.doApply();
        var expected = new ArrayList<>();
        expected.add("movl $0x7, %ecx");
        expected.add("addl $77, %ecx");
        expected.add("movl $0x2, %ebx");
        expected.add("movslq %ecx, %rax # get dividend");
        expected.add("cqto # sign extension to octoword");
        expected.add("movslq %ebx, %rbx # get divisor");
        expected.add("idivq %rbx");
        expected.add("leal 0(%rax), %edi # get result of division");
        assertEquals(expected, result.getInstructions());

        // Test is based on the following firm output for RegisterTest.mj:
        // movl $0x7, %ecx
        // addl $77, %ecx
        // movl %ecx, (%rax)
        // movslq (%rax), %rax
        // cqto
        // movl $0x2, %ecx
        // idivq %rcx
        // mov %rax, %rdi
    }

    @Test
    public void testCall() {
        CallingConvention cconv = new CallingConvention(EnumSet.of(Register.RAX, Register.RBX, Register.RCX, Register.RDX),
                new Register[]{Register.RBX, Register.RCX, Register.RDX}, Register.RAX);
        RegisterAssignment[] assignment = new RegisterAssignment[]{
                new RegisterAssignment(Register.RBX),
                new RegisterAssignment(Register.R8),
                new RegisterAssignment(-8),
                new RegisterAssignment(-16),
                new RegisterAssignment(Register.R9),
                new RegisterAssignment(Register.RCX),
                new RegisterAssignment(Register.RDI),
        };
        RegisterSize[] sizes = new RegisterSize[]{
                RegisterSize.DOUBLE,
                RegisterSize.DOUBLE,
                RegisterSize.DOUBLE,
                RegisterSize.DOUBLE,
                RegisterSize.DOUBLE,
                RegisterSize.DOUBLE,
                RegisterSize.DOUBLE,
        };
        Lifetime[] lifetimes = new Lifetime[]{
                new Lifetime(-1, 1, true),
                new Lifetime(-1, 1, true),
                new Lifetime(-1, 1, true),
                new Lifetime(-1, 1, true),
                new Lifetime(-1, 1, true),
                new Lifetime(-1, 1, true),
                new Lifetime(0, 1),
        };
        Instruction[] ir = new Instruction[]{
                Instruction.newCall(new int[] {0, 1, 2, 3, 4, 5}, Optional.of(6), "_foo"),
        };
        ApplyAssignment ass = new ApplyAssignment(assignment, sizes, lifetimes, Arrays.asList(ir), cconv);
        var result = ass.doApply();
        var expected = new ArrayList<>();
        expected.add("pushq %rbx # push caller-saved register");
        expected.add("pushq %rcx # push caller-saved register");
        expected.add("movq 8(%rsp), %rbx # reload @0 as arg 0");
        expected.add("mov %r8, %rcx # move @1 into arg 1");
        expected.add("movl -8(%rbp), %edx # load @2 as arg 2");
        expected.add("movl -16(%rbp), %rax # reload @3 ...");
        expected.add("pushq %rax # ... and pass it as arg 3");
        expected.add("pushq %r9 # pass @4 as arg 4");
        expected.add("pushq 0(%rsp) # reload @5 as arg 5");
        expected.add("call _foo");
        expected.add("addq $24, %rsp # remove args from stack");
        expected.add("popq %rcx # restore caller-saved register");
        expected.add("popq %rbx # restore caller-saved register");
        expected.add("mov %rax, %rdi # move return value into @6");
        assertEquals(expected, result.getInstructions());
    }

    @Test
    public void testPrologEpilog() {
        CallingConvention cconv = new CallingConvention(EnumSet.of(Register.RAX, Register.RBX, Register.RCX, Register.RDX),
                new Register[]{Register.RBX, Register.RCX, Register.RDX}, Register.RAX);
        RegisterAssignment[] assignment = new RegisterAssignment[]{
                new RegisterAssignment(Register.RDX),
                new RegisterAssignment(Register.RCX),
                new RegisterAssignment(-8),
                new RegisterAssignment(-12),
                new RegisterAssignment(Register.RBX),
                new RegisterAssignment(Register.R8),
        };
        RegisterSize[] sizes = new RegisterSize[]{
                RegisterSize.DOUBLE,
                RegisterSize.DOUBLE,
                RegisterSize.DOUBLE,
                RegisterSize.DOUBLE,
                RegisterSize.DOUBLE,
                RegisterSize.DOUBLE,
        };
        Lifetime[] lifetimes = new Lifetime[]{
                new Lifetime(-1, 1, true),
                new Lifetime(-1, 1, true),
                new Lifetime(-1, 1, true),
                new Lifetime(-1, 1, true),
                new Lifetime(-1, 1, true),
                new Lifetime(-1, 1, true),
        };
        Instruction[] ir = new Instruction[]{};
        ApplyAssignment ass = new ApplyAssignment(assignment, sizes, lifetimes, Arrays.asList(ir), cconv);
        var prolog = ass.createFunctionProlog(6, EnumSet.of(
                Register.RAX, Register.RBX, Register.RCX, Register.RDX, Register.R8, Register.R9, Register.R10
        ));
        var expectedProlog = new ArrayList<>();
        expectedProlog.add("pushq %rbp");
        expectedProlog.add("movq %rsp, %rbp");
        expectedProlog.add("subq $16, %rsp # allocate activation record");
        expectedProlog.add("pushq %r8 # push callee-saved register");
        expectedProlog.add("pushq %r9 # push callee-saved register");
        expectedProlog.add("pushq %r10 # push callee-saved register");
        expectedProlog.add("movl %ebx, %edx # initialize @0 from arg");
        expectedProlog.add("movl %edx, -8(%rbp) # initialize @2 from arg");
        expectedProlog.add("movl 32(%rbp), -12(%rbp) # initialize @3 from arg");
        expectedProlog.add("movl 24(%rbp), %ebx # initialize @4 from arg");
        expectedProlog.add("movl 16(%rbp), %r8d # initialize @5 from arg");
        assertEquals(expectedProlog, prolog);

        var epilog = ass.createFunctionEpilog();
        var expectedEpilog = new ArrayList<>();
        expectedEpilog.add("popq %r10 # restore callee-saved register");
        expectedEpilog.add("popq %r9 # restore callee-saved register");
        expectedEpilog.add("popq %r8 # restore callee-saved register");
        expectedEpilog.add("leave");
        expectedEpilog.add("ret");
        assertEquals(expectedEpilog, epilog);
    }
}
