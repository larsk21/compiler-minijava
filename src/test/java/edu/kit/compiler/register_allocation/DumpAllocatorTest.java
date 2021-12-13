package edu.kit.compiler.register_allocation;

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
        };
        Instruction[] ir = new Instruction[] {
                Instruction.newOp("movl $0x7, @0", new int[] { }, Optional.empty(), 0),
                Instruction.newOp("addl $77, @1", new int[] { }, Optional.of(0), 1),
                Instruction.newOp("movl $0x2, @2", new int[] { }, Optional.empty(), 2),
                Instruction.newDiv(1, 2, 3),
                Instruction.newCall(new int[] { 3 }, Optional.empty(), "print@PLT"),
        };
        RegisterAllocator alloc = new DumbAllocator();
        var result = alloc.performAllocation(0, List.of(ir), sizes);
        var expected = new ArrayList<>();
        expected.add("pushq %rbp");
        expected.add("movq %rsp, %rbp");
        expected.add("subq $32, %rsp # allocate activation record");
        expected.add("pushq %r10 # push callee-saved register");
        expected.add("movl $0x7, %r10d");
        expected.add("movl %r10d, -8(%rbp) # spill for @0");
        expected.add("movl -8(%rbp), %r10d # reload for @0 [overwrite]");
        expected.add("addl $77, %r10d");
        expected.add("movl %r10d, -16(%rbp) # spill for @1");
        expected.add("movl $0x2, %r10d");
        expected.add("movl %r10d, -24(%rbp) # spill for @2");
        expected.add("movslq -16(%rbp), %rax # get dividend");
        expected.add("cqto # sign extension to octoword");
        expected.add("movslq -24(%rbp), %r10 # get divisor");
        expected.add("idivq %r10");
        expected.add("leal 0(%rax), %eax # get result of division");
        expected.add("movl %eax, -32(%rbp) # spill for @3");
        expected.add("movl -32(%rbp), %edi # load @3 as arg 0");
        expected.add("call print@PLT");
        expected.add("popq %r10 # restore callee-saved register");
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
