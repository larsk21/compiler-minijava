package edu.kit.compiler.register_allocation;

import edu.kit.compiler.intermediate_lang.Block;
import edu.kit.compiler.intermediate_lang.Instruction;
import edu.kit.compiler.intermediate_lang.RegisterSize;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DumpAllocatorTest {

    @Test
    public void testCompareToFirm() {
        RegisterSize[] sizes = new RegisterSize[] {
                RegisterSize.DOUBLE,
                RegisterSize.DOUBLE,
                RegisterSize.DOUBLE,
                RegisterSize.DOUBLE,
                RegisterSize.QUAD,
                RegisterSize.QUAD,
        };
        Block block = new Block(List.of(
                Instruction.newOp("movl $0x7, @0", List.of(), Optional.empty(), 0),
                Instruction.newOp("addl $77, @1", List.of(), Optional.of(0), 1),
                Instruction.newOp("movl $0x2, @2", List.of(), Optional.empty(), 2),
                Instruction.newOp("movslq @1, @4", List.of(1), Optional.empty(), 4),
                Instruction.newOp("movslq @2, @5", List.of(2), Optional.empty(), 5),
                Instruction.newDiv(4, 5, 3),
                Instruction.newCall(List.of(3), Optional.empty(), "print@PLT")
        ), 0, 0);

        RegisterAllocator alloc = new DumbAllocator();
        var result = alloc.performAllocation(0, List.of(block), sizes);
        var expected = new ArrayList<>();
        expected.add("pushq %rbp");
        expected.add("movq %rsp, %rbp");
        expected.add("subq $40, %rsp # allocate activation record");
        expected.add("pushq %rbx # push callee-saved register");
        expected.add("pushq %r10 # push callee-saved register");
        expected.add("movl $0x7, %ebx");
        expected.add("movl %ebx, -8(%rbp) # spill for @0");
        expected.add("movl -8(%rbp), %ebx # reload for @0 [overwrite]");
        expected.add("addl $77, %ebx");
        expected.add("movl %ebx, -16(%rbp) # spill for @1");
        expected.add("movl $0x2, %ebx");
        expected.add("movl %ebx, -24(%rbp) # spill for @2");
        expected.add("movl -16(%rbp), %ebx # reload for @1");
        expected.add("movslq %ebx, %rax");
        expected.add("movl -24(%rbp), %ebx # reload for @2");
        expected.add("movslq %ebx, %r10");
        expected.add("movq %r10, -40(%rbp) # spill for @5");
        expected.add("movq -40(%rbp), %rbx # get divisor");
        expected.add("cqto # sign extension to octoword");
        expected.add("idivq %rbx");
        expected.add("movl %eax, -32(%rbp) # spill for @3");
        expected.add("movl -32(%rbp), %edi # load @3 as arg 0");
        expected.add("call print@PLT");
        expected.add("popq %r10 # restore callee-saved register");
        expected.add("popq %rbx # restore callee-saved register");
        expected.add("leave");
        expected.add("ret");
        assertEquals(expected, result);

        // Test is based on the following firm output for RegisterTest.mj:
        // movl $0x7, %ecx
        // addl $77, %ecx
        // movl %ecx, (%rax)
        // movslq (%rax), %rax
        // cqto
        // movl $0x2, %ecx
        // idivq %rcx
        // mov %rax, %rdi
        // call print@PLT
    }
}
