package edu.kit.compiler.register_allocation;

import edu.kit.compiler.intermediate_lang.Block;
import edu.kit.compiler.intermediate_lang.Instruction;
import edu.kit.compiler.intermediate_lang.RegisterSize;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DumbAllocatorTest {

    @Test
    public void testCompareToFirm() {
        RegisterSize[] sizes = new RegisterSize[] {
                RegisterSize.DOUBLE,
                RegisterSize.DOUBLE,
                RegisterSize.QUAD,
                RegisterSize.DOUBLE,
                RegisterSize.DOUBLE,
                RegisterSize.DOUBLE,
                RegisterSize.DOUBLE,
                RegisterSize.DOUBLE,
        };
        Block block = new Block(List.of(
                Instruction.newOp("movl $0x1, @6", List.of(), Optional.empty(), 6),
                Instruction.newOp("movl $0x4, @7", List.of(), Optional.empty(), 7),
                Instruction.newCall(List.of(6, 7), Optional.of(2), "calloc@PLT"),
                Instruction.newOp("movl $0x7, @0", List.of(), Optional.empty(), 0),
                Instruction.newOp("addl $77, @1", List.of(), Optional.of(0), 1),
                Instruction.newInput("movl @1, (@2)", List.of(1, 2)),
                Instruction.newOp("movl (@2), @4", List.of(2), Optional.empty(), 4),
                Instruction.newOp("movl $0x2, @5", List.of(), Optional.empty(), 5),
                Instruction.newDiv(4, 5, 3),
                Instruction.newCall(List.of(3), Optional.empty(), "print@PLT")
        ), 0, 0);

        RegisterAllocator alloc = new DumbAllocator();
        var result = alloc.performAllocation(0, List.of(block), sizes);
        var expected = new ArrayList<>();
        expected.add("pushq %rbp");
        expected.add("movq %rsp, %rbp");
        expected.add("subq $72, %rsp # allocate activation record");
        expected.add("pushq %rbx # push callee-saved register");
        expected.add("pushq %r12 # push callee-saved register");
        expected.add("pushq %r13 # push callee-saved register");

        expected.add(".L0:");
        expected.add("movl $0x1, %ebx");
        expected.add("movl %ebx, -56(%rbp) # spill for @6");
        expected.add("movl $0x4, %r12d");
        expected.add("movl %r12d, -64(%rbp) # spill for @7");
        expected.add("mov %rbx, %rdi # assign arg registers");
        expected.add("mov %r12, %rsi # assign arg registers");
        expected.add("call calloc@PLT");
        expected.add("movq %rax, -24(%rbp) # spill return value for @2");
        expected.add("movl $0x7, %ebx");
        expected.add("movl %ebx, -8(%rbp) # spill for @0");
        expected.add("addl $77, %ebx");
        expected.add("movl %ebx, -16(%rbp) # spill for @1");
        expected.add("movl %ebx, (%rax)");
        expected.add("movl (%rax), %ebx");
        expected.add("movl %ebx, -40(%rbp) # spill for @4");
        expected.add("movl $0x2, %r12d");
        expected.add("movl %r12d, -48(%rbp) # spill for @5");
        expected.add("movl %ebx, %eax # get dividend");
        expected.add("movl %r12d, %r13d # get divisor");
        expected.add("cltd # sign extension to edx:eax");
        expected.add("idivl %r13d");
        expected.add("movl %eax, -32(%rbp) # spill for @3");
        expected.add("mov %rax, %rdi # assign arg registers");
        expected.add("call print@PLT");

        expected.add(ApplyAssignment.FINAL_BLOCK_LABEL + ":");
        expected.add("popq %r13 # restore callee-saved register");
        expected.add("popq %r12 # restore callee-saved register");
        expected.add("popq %rbx # restore callee-saved register");
        expected.add("leave");
        expected.add("ret");
        assertEquals(expected, result);

        // Test is based on the following firm output for RegisterTest.mj:
        // pushq %rbp                       /* amd64_push_reg T[291:151] */
        // mov %rsp, %rbp                   /* be_Copy Lu[294:154] */
        // movl $0x1, %edi                  /* amd64_mov_imm Lu[149:12] */
        // movl $0x4, %esi                  /* amd64_mov_imm Lu[150:13] */
        // call calloc@PLT                  /* amd64_call T[151:14] */
        // movl $0x7, %ecx                  /* amd64_mov_imm Lu[154:17] */
        // addl $77, %ecx                   /* amd64_add T[296:156] */
        // movl %ecx, (%rax)                /* amd64_mov_store M[157:20] */
        // movslq (%rax), %rax              /* amd64_movs T[158:21] */
        // cqto                             /* amd64_cqto Lu[162:25] */
        // movl $0x2, %ecx                  /* amd64_mov_imm Lu[160:23] */
        // idivq %rcx                       /* amd64_idiv T[163:26] */
        // mov %rax, %rdi                   /* be_Copy Lu[286:146] */
        // call print@PLT                   /* amd64_call T[166:29] */
        // leave                            /* amd64_leave T[287:147] */
        // ret                              /* amd64_ret X[174:37] */
    }
}
