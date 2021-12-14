package edu.kit.compiler.register_allocation;

import edu.kit.compiler.intermediate_lang.Block;
import edu.kit.compiler.intermediate_lang.Instruction;
import edu.kit.compiler.intermediate_lang.Register;
import edu.kit.compiler.intermediate_lang.RegisterSize;
import edu.kit.compiler.logger.Logger;
import org.junit.jupiter.api.Test;

import java.util.*;

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
        Block block = new Block(List.of(
                Instruction.newInput("movq $0, 0(@0)", List.of( 0 ))
        ), 0, 0);
        ApplyAssignment ass = new ApplyAssignment(assignment, sizes, lifetimes, List.of(block), 1);
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
        Block block = new Block(List.of(
                Instruction.newOp("movq 4(@0), @1", List.of( 0 ), Optional.empty(), 1),
                Instruction.newOp("incrl @1", List.of(), Optional.empty(), 1),
                Instruction.newOp("addl @0, @1, @2", List.of( 0, 1 ), Optional.empty(), 2)
        ), 0, 0);
        ApplyAssignment ass = new ApplyAssignment(assignment, sizes, lifetimes, List.of(block), 3);
        var result = ass.doApply();
        var expected = new ArrayList<>();
        expected.add(".L0:");
        expected.add("movq 4(%rax), %rbx");
        expected.add("movq %rbx, -8(%rbp) # spill for @1");
        expected.add("incrl %rbx");
        expected.add("movq %rbx, -8(%rbp) # spill for @1");
        expected.add("movq -8(%rbp), %rbx # reload for @1");
        expected.add("addl %rax, %rbx, %r10");
        expected.add("movq %r10, -16(%rbp) # spill for @2");
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
        Block block = new Block(List.of(
                Instruction.newOp("addl @0, @2", List.of( 0 ), Optional.of(1), 2),
                Instruction.newOp("xorl @0, @3", List.of( 0 ), Optional.of(1), 3),
                Instruction.newOp("subl @0, @4", List.of( 0 ), Optional.of(1), 4),
                Instruction.newOp("addl @0, @4", List.of( 0 ), Optional.of(5), 4),
                Instruction.newOp("xorl @0, @3", List.of( 0 ), Optional.of(5), 3)
        ), 0, 0);
        ApplyAssignment ass = new ApplyAssignment(assignment, sizes, lifetimes, List.of(block), 5);
        var result = ass.doApply();
        var expected = new ArrayList<>();
        expected.add(".L0:");
        expected.add("mov %rbx, %rcx # move for @1 [overwrite]");
        expected.add("addl %rax, %rcx");
        expected.add("mov %rbx, %r10 # move for @1 [overwrite]");
        expected.add("xorl %rax, %r10");
        expected.add("movq %r10, -8(%rbp) # spill for @3");
        expected.add("subl %rax, %rbx");
        expected.add("movq -16(%rbp), %rbx # reload for @5 [overwrite]");
        expected.add("addl %rax, %rbx");
        expected.add("movq -16(%rbp), %r10 # reload for @5 [overwrite]");
        expected.add("xorl %rax, %r10");
        expected.add("movq %r10, -8(%rbp) # spill for @3");
        assertEquals(expected, result.getInstructions());
        // check that temporary registers are marked as used
        assert result.getUsedRegisters().contains(Register.R10);
    }

    @Test
    public void testDiv() {
        RegisterAssignment[] assignment = new RegisterAssignment[] {
                new RegisterAssignment(Register.RAX),
                new RegisterAssignment(Register.R8),
                new RegisterAssignment(Register.R9),
                new RegisterAssignment(-8),
                new RegisterAssignment(-16),
                new RegisterAssignment(Register.RAX),
                new RegisterAssignment(Register.RBX),
                new RegisterAssignment(Register.RAX),
                new RegisterAssignment(Register.RBX),
        };
        RegisterSize[] sizes = new RegisterSize[] {
                RegisterSize.DOUBLE,
                RegisterSize.DOUBLE,
                RegisterSize.DOUBLE,
                RegisterSize.DOUBLE,
                RegisterSize.DOUBLE,
                RegisterSize.QUAD,
                RegisterSize.QUAD,
                RegisterSize.QUAD,
                RegisterSize.QUAD,
        };
        Lifetime[] lifetimes = new Lifetime[] {
                new Lifetime(-1, 1, true),
                new Lifetime(-1, 6),
                new Lifetime(-1, 3),
                new Lifetime(-1, 6),
                new Lifetime(-1, 6),
                new Lifetime(0, 3, true),
                new Lifetime(1, 3, true),
                new Lifetime(3, 6, true),
                new Lifetime(4, 6, true),
        };
        Block block = new Block(List.of(
                Instruction.newOp("movslq @0, @5", List.of(0), Optional.empty(), 5),
                Instruction.newOp("movslq @1, @6", List.of(1), Optional.empty(), 6),
                Instruction.newDiv(5, 6, 2),
                Instruction.newOp("movslq @1, @7", List.of(1), Optional.empty(), 7),
                Instruction.newOp("movslq @3, @8", List.of(3), Optional.empty(), 8),
                Instruction.newDiv(7, 8, 4)
                //Instruction.newMod(4, 2, 1),
                //Instruction.newDiv(1, 3, 4),
        ), 0, 0);
        ApplyAssignment ass = new ApplyAssignment(assignment, sizes, lifetimes, List.of(block), 6);
        var result = ass.doApply();
        var expected = new ArrayList<>();
        expected.add(".L0:");

        expected.add("movslq %eax, %rax");
        expected.add("movslq %r8d, %rbx");
        expected.add("cqto # sign extension to octoword");
        expected.add("idivq %rbx");
        expected.add("movl %eax, %r9d # move result to @2");

        expected.add("movslq %r8d, %rax");
        expected.add("movl -8(%rbp), %ebx # reload for @3");
        expected.add("movslq %ebx, %rbx");
        expected.add("cqto # sign extension to octoword");
        expected.add("idivq %rbx");
        expected.add("movl %eax, -16(%rbp) # spill for @4");

        // expected.add("movslq -16(%rbp), %rax # get dividend");
        // expected.add("cqto # sign extension to octoword");
        // expected.add("movslq %r9d, %r9 # get divisor");
        // expected.add("idivq %r9");
        // expected.add("leal 0(%rdx), %r8d # get result of division");

        // expected.add("movslq %r8d, %rax # get dividend");
        // expected.add("cqto # sign extension to octoword");
        // expected.add("movslq -8(%rbp), %rbx # get divisor");
        // expected.add("idivq %rbx");
        // expected.add("leal 0(%rax), %eax # get result of division");
        // expected.add("movl %eax, -16(%rbp) # spill for @4");
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
                new RegisterAssignment(Register.RAX),
                new RegisterAssignment(Register.RBX),
        };
        RegisterSize[] sizes = new RegisterSize[] {
                RegisterSize.DOUBLE,
                RegisterSize.DOUBLE,
                RegisterSize.DOUBLE,
                RegisterSize.DOUBLE,
                RegisterSize.QUAD,
                RegisterSize.QUAD,
        };
        Lifetime[] lifetimes = new Lifetime[] {
                new Lifetime(-1, 2, true),
                new Lifetime(1, 6, true),
                new Lifetime(2, 5, true),
                new Lifetime(3, 6),
                new Lifetime(3, 6, true),
                new Lifetime(4, 6, true),
        };
        Block block = new Block(List.of(
                Instruction.newOp("movl $0x7, @0", List.of(), Optional.empty(), 0),
                Instruction.newOp("addl $77, @1", List.of(), Optional.of(0), 1),
                Instruction.newOp("movl $0x2, @2", List.of(), Optional.empty(), 2),
                Instruction.newOp("movslq @1, @4", List.of(1), Optional.empty(), 4),
                Instruction.newOp("movslq @2, @5", List.of(2), Optional.empty(), 5),
                Instruction.newDiv(4, 5, 3)
        ), 0, 0);
        ApplyAssignment ass = new ApplyAssignment(assignment, sizes, lifetimes, List.of(block), 6);
        var result = ass.doApply();
        var expected = new ArrayList<>();
        expected.add(".L0:");
        expected.add("movl $0x7, %ecx");
        expected.add("addl $77, %ecx");
        expected.add("movl $0x2, %ebx");
        expected.add("movslq %ecx, %rax");
        expected.add("movslq %ebx, %rbx");
        expected.add("cqto # sign extension to octoword");
        expected.add("idivq %rbx");
        expected.add("movl %eax, %edi # move result to @3");
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
        Block block = new Block(List.of(
                Instruction.newCall(List.of(0, 1, 2, 3, 4, 5), Optional.of(6), "_foo")
        ), 0, 0);
        ApplyAssignment ass = new ApplyAssignment(assignment, sizes, lifetimes, List.of(block), 1, cconv);
        var result = ass.doApply();
        var expected = new ArrayList<>();
        expected.add(".L0:");
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
    public void testRet() {
        RegisterAssignment[] assignment = new RegisterAssignment[]{
                new RegisterAssignment(Register.RAX),
                new RegisterAssignment(Register.RBX),
                new RegisterAssignment(-8)
        };
        RegisterSize[] sizes = new RegisterSize[]{
                RegisterSize.DOUBLE,
                RegisterSize.DOUBLE,
                RegisterSize.DOUBLE,
        };
        Lifetime[] lifetimes = new Lifetime[]{
                new Lifetime(-1, 3, true),
                new Lifetime(-1, 3, true),
                new Lifetime(-1, 3, true),
        };
        Block block = new Block(List.of(
                Instruction.newRet(Optional.empty()),
                Instruction.newRet(Optional.of(0)),
                Instruction.newRet(Optional.of(1)),
                Instruction.newRet(Optional.of(2))
        ), 0, 0);
        ApplyAssignment ass = new ApplyAssignment(assignment, sizes, lifetimes, List.of(block), 4);
        var result = ass.doApply();
        var expected = new ArrayList<>();
        expected.add(".L0:");
        expected.add("jmp " + ApplyAssignment.FINAL_BLOCK_LABEL);
        expected.add("jmp " + ApplyAssignment.FINAL_BLOCK_LABEL);
        expected.add("movl %ebx, %eax # set return value");
        expected.add("jmp " + ApplyAssignment.FINAL_BLOCK_LABEL);
        expected.add("movl -8(%rbp), %eax # set return value");
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
        Block block = new Block(List.of(), 0, 0);
        ApplyAssignment ass = new ApplyAssignment(assignment, sizes, lifetimes, List.of(block), 0, cconv);
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
        expectedEpilog.add(ApplyAssignment.FINAL_BLOCK_LABEL + ":");
        expectedEpilog.add("popq %r10 # restore callee-saved register");
        expectedEpilog.add("popq %r9 # restore callee-saved register");
        expectedEpilog.add("popq %r8 # restore callee-saved register");
        expectedEpilog.add("leave");
        expectedEpilog.add("ret");
        assertEquals(expectedEpilog, epilog);
    }
}
