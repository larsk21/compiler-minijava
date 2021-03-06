package edu.kit.compiler.register_allocation;

import edu.kit.compiler.intermediate_lang.Block;
import edu.kit.compiler.intermediate_lang.Instruction;
import edu.kit.compiler.intermediate_lang.Register;
import edu.kit.compiler.intermediate_lang.RegisterSize;
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
        expected.add("addl %rax, %rbx, %r12");
        expected.add("movq %r12, -16(%rbp) # spill for @2");
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
                new Lifetime(-1, 6),
                new Lifetime(-1, 3, true),
                new Lifetime(-1, 6),
                new Lifetime(-1, 6, true),
                new Lifetime(2, 6),
                new Lifetime(-1, 6),
        };
        Block block = new Block(List.of(
                Instruction.newOp("addl @0, @2", List.of( 0 ), Optional.of(1), 2),
                Instruction.newOp("xorl @0, @3", List.of( 0 ), Optional.of(1), 3),
                Instruction.newOp("subl @0, @4", List.of( 0 ), Optional.of(1), 4),
                Instruction.newOp("addl @0, @4", List.of( 0 ), Optional.of(5), 4),
                Instruction.newOp("xorl @0, @3", List.of( 0 ), Optional.of(5), 3),
                Instruction.newOp("mull @0, @5", List.of( 0 ), Optional.of(3), 5)
        ), 0, 0);
        ApplyAssignment ass = new ApplyAssignment(assignment, sizes, lifetimes, List.of(block), 6);
        var result = ass.doApply();
        var expected = new ArrayList<>();
        expected.add(".L0:");
        expected.add("movq %rbx, %rcx # move for @1 [overwrite]");
        expected.add("addl %rax, %rcx");
        expected.add("movq %rbx, %r12 # move for @1 [overwrite]");
        expected.add("xorl %rax, %r12");
        expected.add("movq %r12, -8(%rbp) # spill for @3");
        expected.add("subl %rax, %rbx");
        expected.add("movq -16(%rbp), %rbx # reload for @5 [overwrite]");
        expected.add("addl %rax, %rbx");
        expected.add("movq -16(%rbp), %r12 # reload for @5 [overwrite]");
        expected.add("xorl %rax, %r12");
        expected.add("movq %r12, -8(%rbp) # spill for @3");
        expected.add("mull %rax, %r12");
        expected.add("movq %r12, -16(%rbp) # spill for @5");
        assertEquals(expected, result.getInstructions());
        // check that temporary registers are marked as used
        assert result.getUsedRegisters().contains(Register.R12);
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
                new Lifetime(-1, 2),
                new Lifetime(-1, 2),
                new Lifetime(-1, 2),
                new Lifetime(-1, 2),
        };
        Block block = new Block(List.of(
                Instruction.newDiv(0, 1, 2),
                Instruction.newDiv(1, 3, 4)
        ), 0, 0);
        ApplyAssignment ass = new ApplyAssignment(assignment, sizes, lifetimes, List.of(block), 2);
        var result = ass.doApply();
        var expected = new ArrayList<>();
        expected.add(".L0:");

        expected.add("cltd # sign extension to edx:eax");
        expected.add("idivl %r8d");
        expected.add("movl %eax, %r9d # move result to @2");

        expected.add("movl %r8d, %eax # get dividend");
        expected.add("movl -8(%rbp), %ebx # get divisor");
        expected.add("cltd # sign extension to edx:eax");
        expected.add("idivl %ebx");
        expected.add("movl %eax, -16(%rbp) # spill for @4");

        assertEquals(expected, result.getInstructions());

        // check that temporary registers are marked as used
        assert result.getUsedRegisters().contains(Register.RDX);
    }

    @Test
    public void testCompareToFirm() {
        RegisterAssignment[] assignment = new RegisterAssignment[] {
                new RegisterAssignment(Register.RAX),
                new RegisterAssignment(Register.RAX),
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
        Block block = new Block(List.of(
                Instruction.newOp("movl $0x7, @0", List.of(), Optional.empty(), 0),
                Instruction.newOp("addl $77, @1", List.of(), Optional.of(0), 1),
                Instruction.newOp("movl $0x2, @2", List.of(), Optional.empty(), 2),
                Instruction.newDiv(1, 2, 3)
        ), 0, 0);
        ApplyAssignment ass = new ApplyAssignment(assignment, sizes, lifetimes, List.of(block), 4);
        var result = ass.doApply();
        var expected = new ArrayList<>();
        expected.add(".L0:");
        expected.add("movl $0x7, %eax");
        expected.add("addl $77, %eax");
        expected.add("movl $0x2, %ebx");
        expected.add("cltd # sign extension to edx:eax");
        expected.add("idivl %ebx");
        expected.add("movl %eax, %edi # move result to @3");
        assertEquals(expected, result.getInstructions());
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
        expected.add("movl -16(%rbp), %r12d # reload @3 ...");
        expected.add("pushq %r12 # ... and pass it as arg 3");
        expected.add("pushq %r9 # pass @4 as arg 4");
        expected.add("pushq %rcx # pass @5 as arg 5");
        expected.add("mov %r8, %rcx # assign arg registers");
        expected.add("movl -8(%rbp), %edx # load @2 as arg 2");
        expected.add("call _foo");
        expected.add("addq $24, %rsp # remove args from stack");
        expected.add("movl %eax, %edi # move return value into @6");
        assertEquals(expected, result.getInstructions());
    }

    @Test
    public void testCallAlignment() {
        CallingConvention cconv = new CallingConvention(EnumSet.of(Register.RAX, Register.RBX, Register.RCX, Register.RDX),
                new Register[]{Register.RBX, Register.RCX, Register.RDX}, Register.RAX);
        RegisterAssignment[] assignment = new RegisterAssignment[] {new RegisterAssignment(Register.RAX)};
        RegisterSize[] sizes = new RegisterSize[] {RegisterSize.DOUBLE};
        Lifetime[] lifetimes = new Lifetime[] {new Lifetime(-1, 1)};
        Block block = new Block(List.of(
                Instruction.newCall(List.of(0), Optional.empty(), "print")
        ), 0, 0);
        ApplyAssignment ass = new ApplyAssignment(assignment, sizes, lifetimes, List.of(block), 1, cconv);
        var result = ass.doApply();
        var expected = new ArrayList<>();
        expected.add(".L0:");
        expected.add("pushq %rax # push caller-saved register");
        expected.add("mov %rax, %rbx # assign arg registers");
        expected.add("subq $8, %rsp # align stack to 16 byte");
        expected.add("call print");
        expected.add("addq $8, %rsp # remove args from stack");
        expected.add("popq %rax # restore caller-saved register");
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
        expectedProlog.add("subq $24, %rsp # allocate activation record");
        expectedProlog.add("pushq %r8 # push callee-saved register");
        expectedProlog.add("pushq %r9 # push callee-saved register");
        expectedProlog.add("pushq %r10 # push callee-saved register");
        expectedProlog.add("movl %edx, -8(%rbp) # initialize @2 from arg");
        expectedProlog.add("movl 32(%rbp), %eax # load to temporary...");
        expectedProlog.add("movl %eax, -12(%rbp) # ...initialize @3 from arg");
        expectedProlog.add("mov %rbx, %rdx # assign args to registers");
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
