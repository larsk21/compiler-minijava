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
                RegisterSize.QUAD,
                RegisterSize.QUAD,
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
                Instruction.newOp("movslq (@2), @4", List.of(2), Optional.empty(), 4),
                Instruction.newOp("movq $0x2, @5", List.of(), Optional.empty(), 5),
                Instruction.newDiv(4, 5, 3),
                Instruction.newCall(List.of(3), Optional.empty(), "print@PLT")
        ), 0, 0);

        RegisterAllocator alloc = new DumbAllocator();
        var result = alloc.performAllocation(0, List.of(block), sizes);
        var expected = new ArrayList<>();
        expected.add("pushq %rbp");
        expected.add("movq %rsp, %rbp");
        expected.add("subq $64, %rsp # allocate activation record");
        expected.add("pushq %rbx # push callee-saved register");
        expected.add("pushq %r10 # push callee-saved register");

        expected.add(".L0:");
        expected.add("movl $0x1, %ebx");
        expected.add("movl %ebx, -48(%rbp) # spill for @6");
        expected.add("movl $0x4, %ebx");
        expected.add("movl %ebx, -56(%rbp) # spill for @7");
        expected.add("movl -48(%rbp), %edi # load @6 as arg 0");
        expected.add("movl -56(%rbp), %esi # load @7 as arg 1");
        expected.add("call calloc@PLT");
        expected.add("movq %rax, -24(%rbp) # spill return value for @2");
        expected.add("movl $0x7, %ebx");
        expected.add("movl %ebx, -8(%rbp) # spill for @0");
        expected.add("movl -8(%rbp), %ebx # reload for @0 [overwrite]");
        expected.add("addl $77, %ebx");
        expected.add("movl %ebx, -16(%rbp) # spill for @1");
        expected.add("movl -16(%rbp), %ebx # reload for @1");
        expected.add("movq -24(%rbp), %r10 # reload for @2");
        expected.add("movl %ebx, (%r10)");
        expected.add("movq -24(%rbp), %rbx # reload for @2");
        expected.add("movslq (%rbx), %rax");
        expected.add("movq $0x2, %rbx");
        expected.add("movq %rbx, -40(%rbp) # spill for @5");
        expected.add("movq -40(%rbp), %rbx # get divisor");
        expected.add("cqto # sign extension to octoword");
        expected.add("idivq %rbx");
        expected.add("movl %eax, -32(%rbp) # spill for @3");
        expected.add("movl -32(%rbp), %edi # load @3 as arg 0");
        expected.add("call print@PLT");

        expected.add(ApplyAssignment.FINAL_BLOCK_LABEL + ":");
        expected.add("popq %r10 # restore callee-saved register");
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

    @Test
    public void testComplexProgram() {
        // This assembly test program calculates a kind of sliding average for values in an array of size 10
        // and outputs the sum (the result is obviously 42)

        RegisterSize[] sizes = new RegisterSize[] {
                RegisterSize.DOUBLE, RegisterSize.DOUBLE, RegisterSize.DOUBLE, RegisterSize.QUAD, RegisterSize.DOUBLE,
                RegisterSize.DOUBLE, RegisterSize.DOUBLE, RegisterSize.QUAD, RegisterSize.QUAD, RegisterSize.DOUBLE,
                RegisterSize.QUAD, RegisterSize.QUAD, RegisterSize.DOUBLE, RegisterSize.DOUBLE, RegisterSize.DOUBLE,
                RegisterSize.QUAD, RegisterSize.QUAD, RegisterSize.DOUBLE, RegisterSize.DOUBLE, RegisterSize.DOUBLE,
                RegisterSize.DOUBLE, RegisterSize.DOUBLE, RegisterSize.QUAD, RegisterSize.QUAD, RegisterSize.DOUBLE,
                RegisterSize.DOUBLE, RegisterSize.DOUBLE, RegisterSize.QUAD, RegisterSize.QUAD, RegisterSize.DOUBLE,
                RegisterSize.DOUBLE, RegisterSize.DOUBLE, RegisterSize.DOUBLE, RegisterSize.DOUBLE, RegisterSize.QUAD,
                RegisterSize.DOUBLE
        };
        assert sizes.length == 36;

        Block block0 = new Block(List.of(
                Instruction.newOp("movl $10, @1", List.of(), Optional.empty(), 1),
                Instruction.newOp("movl $4, @2", List.of(), Optional.empty(), 2),
                Instruction.newCall(List.of(1, 2), Optional.of(3), "calloc@PLT"),
                Instruction.newInput("movl $1, 8(@3)", List.of(3)),
                Instruction.newInput("movl $2, 12(@3)", List.of(3)),
                Instruction.newInput("movl $3, 16(@3)", List.of(3)),
                Instruction.newInput("movl $4, 20(@3)", List.of(3)),
                Instruction.newInput("movl $5, 24(@3)", List.of(3)),
                Instruction.newInput("movl $6, 28(@3)", List.of(3)),

                Instruction.newOp("movl $2, @4", List.of(), Optional.empty(), 4),
                Instruction.newOp("movl $0, @35", List.of(), Optional.empty(), 35)
        ), 0, 0);

        Block block1 = new Block(List.of(
                Instruction.newOp("movl @1, @5", List.of(1), Optional.empty(), 5),
                Instruction.newOp("subl $2, @6", List.of(), Optional.of(5), 6),
                Instruction.newInput("cmpl @4, @6", List.of(4, 6)),
                Instruction.newJmp("jle .L3", 3)
        ), 1, 1);

        Block block2 = new Block(List.of(
                Instruction.newOp("movslq @4, @34", List.of(4), Optional.empty(), 34),
                Instruction.newOp("imulq $4, @7", List.of(), Optional.of(34), 7),
                Instruction.newOp("addq @3, @8", List.of(3), Optional.of(7), 8),

                Instruction.newOp("movl -8(@8), @9", List.of(8), Optional.empty(), 9),
                Instruction.newOp("movslq @9, @10", List.of(9), Optional.empty(), 10),
                Instruction.newOp("movq $3, @11", List.of(), Optional.empty(), 11),
                Instruction.newDiv(10, 11, 12),
                Instruction.newOp("addl @35, @13", List.of(35), Optional.of(12), 13),
                Instruction.newOp("movl @13, @35", List.of(13), Optional.empty(), 35),

                Instruction.newOp("movl -4(@8), @14", List.of(8), Optional.empty(), 14),
                Instruction.newOp("movslq @14, @15", List.of(14), Optional.empty(), 15),
                Instruction.newOp("movq $2, @16", List.of(), Optional.empty(), 16),
                Instruction.newDiv(15, 16, 17),
                Instruction.newOp("addl @35, @18", List.of(35), Optional.of(17), 18),
                Instruction.newOp("movl @18, @35", List.of(18), Optional.empty(), 35),

                Instruction.newOp("movl (@8), @19", List.of(8), Optional.empty(), 19),
                Instruction.newOp("addl @35, @20", List.of(35), Optional.of(19), 20),
                Instruction.newOp("movl @20, @35", List.of(20), Optional.empty(), 35),

                Instruction.newOp("movl 4(@8), @21", List.of(8), Optional.empty(), 21),
                Instruction.newOp("movslq @21, @22", List.of(21), Optional.empty(), 22),
                Instruction.newOp("movq $2, @23", List.of(), Optional.empty(), 23),
                Instruction.newDiv(22, 23, 24),
                Instruction.newOp("addl @35, @25", List.of(35), Optional.of(24), 25),
                Instruction.newOp("movl @25, @35", List.of(25), Optional.empty(), 35),

                Instruction.newOp("movl 8(@8), @26", List.of(8), Optional.empty(), 26),
                Instruction.newOp("movslq @26, @27", List.of(26), Optional.empty(), 27),
                Instruction.newOp("movq $3, @28", List.of(), Optional.empty(), 28),
                Instruction.newDiv(27, 28, 29),
                Instruction.newOp("addl @35, @30", List.of(35), Optional.of(29), 30),
                Instruction.newOp("movl @30, @35", List.of(30), Optional.empty(), 35),

                Instruction.newOp("addl $1, @31", List.of(), Optional.of(4), 31),
                Instruction.newOp("movl @31, @4", List.of(31), Optional.empty(), 4),
                Instruction.newJmp("jmp .L1", 1)
        ), 2, 0);

        Block block3 = new Block(List.of(
                Instruction.newOp("subl $1, @32", List.of(), Optional.of(35), 32),
                Instruction.newCall(List.of(32), Optional.empty(), "print@PLT")
        ), 3, 0);

        RegisterAllocator alloc = new DumbAllocator();
        var result = alloc.performAllocation(0, List.of(block0, block1, block2, block3), sizes);

        var expected = new ArrayList<>();
        expected.add("pushq %rbp");
        expected.add("movq %rsp, %rbp");
        expected.add("subq $240, %rsp # allocate activation record");
        expected.add("pushq %rbx # push callee-saved register");
        expected.add("pushq %r10 # push callee-saved register");

        expected.add(".L0:");
        expected.add("movl $10, %ebx");
        expected.add("movl %ebx, -8(%rbp) # spill for @1");
        expected.add("movl $4, %ebx");
        expected.add("movl %ebx, -16(%rbp) # spill for @2");
        expected.add("movl -8(%rbp), %edi # load @1 as arg 0");
        expected.add("movl -16(%rbp), %esi # load @2 as arg 1");
        expected.add("call calloc@PLT");
        expected.add("movq %rax, -24(%rbp) # spill return value for @3");
        expected.add("movq -24(%rbp), %rbx # reload for @3");
        expected.add("movl $1, 8(%rbx)");
        expected.add("movq -24(%rbp), %rbx # reload for @3");
        expected.add("movl $2, 12(%rbx)");
        expected.add("movq -24(%rbp), %rbx # reload for @3");
        expected.add("movl $3, 16(%rbx)");
        expected.add("movq -24(%rbp), %rbx # reload for @3");
        expected.add("movl $4, 20(%rbx)");
        expected.add("movq -24(%rbp), %rbx # reload for @3");
        expected.add("movl $5, 24(%rbx)");
        expected.add("movq -24(%rbp), %rbx # reload for @3");
        expected.add("movl $6, 28(%rbx)");
        expected.add("movl $2, %ebx");
        expected.add("movl %ebx, -32(%rbp) # spill for @4");
        expected.add("movl $0, %ebx");
        expected.add("movl %ebx, -240(%rbp) # spill for @35");

        expected.add(".L1:");
        expected.add("movl -8(%rbp), %ebx # reload for @1");
        expected.add("movl %ebx, %r10d");
        expected.add("movl %r10d, -40(%rbp) # spill for @5");
        expected.add("movl -40(%rbp), %ebx # reload for @5 [overwrite]");
        expected.add("subl $2, %ebx");
        expected.add("movl %ebx, -48(%rbp) # spill for @6");
        expected.add("movl -32(%rbp), %ebx # reload for @4");
        expected.add("movl -48(%rbp), %r10d # reload for @6");
        expected.add("cmpl %ebx, %r10d");
        expected.add("jle .L3");

        expected.add(".L2:");
        expected.add("movl -32(%rbp), %ebx # reload for @4");
        expected.add("movslq %ebx, %r10");
        expected.add("movq %r10, -232(%rbp) # spill for @34");
        expected.add("movq -232(%rbp), %rbx # reload for @34 [overwrite]");
        expected.add("imulq $4, %rbx");
        expected.add("movq %rbx, -56(%rbp) # spill for @7");
        expected.add("movq -24(%rbp), %rbx # reload for @3");
        expected.add("movq -56(%rbp), %r10 # reload for @7 [overwrite]");
        expected.add("addq %rbx, %r10");
        expected.add("movq %r10, -64(%rbp) # spill for @8");
        expected.add("movq -64(%rbp), %rbx # reload for @8");
        expected.add("movl -8(%rbx), %r10d");
        expected.add("movl %r10d, -72(%rbp) # spill for @9");
        expected.add("movl -72(%rbp), %ebx # reload for @9");
        expected.add("movslq %ebx, %rax");
        expected.add("movq $3, %rbx");
        expected.add("movq %rbx, -80(%rbp) # spill for @11");
        expected.add("movq -80(%rbp), %rbx # get divisor");
        expected.add("cqto # sign extension to octoword");
        expected.add("idivq %rbx");
        expected.add("movl %eax, -88(%rbp) # spill for @12");
        expected.add("movl -240(%rbp), %ebx # reload for @35");
        expected.add("movl -88(%rbp), %r10d # reload for @12 [overwrite]");
        expected.add("addl %ebx, %r10d");
        expected.add("movl %r10d, -96(%rbp) # spill for @13");
        expected.add("movl -96(%rbp), %ebx # reload for @13");
        expected.add("movl %ebx, %r10d");
        expected.add("movl %r10d, -240(%rbp) # spill for @35");
        expected.add("movq -64(%rbp), %rbx # reload for @8");
        expected.add("movl -4(%rbx), %r10d");
        expected.add("movl %r10d, -104(%rbp) # spill for @14");
        expected.add("movl -104(%rbp), %ebx # reload for @14");
        expected.add("movslq %ebx, %rax");
        expected.add("movq $2, %rbx");
        expected.add("movq %rbx, -112(%rbp) # spill for @16");
        expected.add("movq -112(%rbp), %rbx # get divisor");
        expected.add("cqto # sign extension to octoword");
        expected.add("idivq %rbx");
        expected.add("movl %eax, -120(%rbp) # spill for @17");
        expected.add("movl -240(%rbp), %ebx # reload for @35");
        expected.add("movl -120(%rbp), %r10d # reload for @17 [overwrite]");
        expected.add("addl %ebx, %r10d");
        expected.add("movl %r10d, -128(%rbp) # spill for @18");
        expected.add("movl -128(%rbp), %ebx # reload for @18");
        expected.add("movl %ebx, %r10d");
        expected.add("movl %r10d, -240(%rbp) # spill for @35");
        expected.add("movq -64(%rbp), %rbx # reload for @8");
        expected.add("movl (%rbx), %r10d");
        expected.add("movl %r10d, -136(%rbp) # spill for @19");
        expected.add("movl -240(%rbp), %ebx # reload for @35");
        expected.add("movl -136(%rbp), %r10d # reload for @19 [overwrite]");
        expected.add("addl %ebx, %r10d");
        expected.add("movl %r10d, -144(%rbp) # spill for @20");
        expected.add("movl -144(%rbp), %ebx # reload for @20");
        expected.add("movl %ebx, %r10d");
        expected.add("movl %r10d, -240(%rbp) # spill for @35");
        expected.add("movq -64(%rbp), %rbx # reload for @8");
        expected.add("movl 4(%rbx), %r10d");
        expected.add("movl %r10d, -152(%rbp) # spill for @21");
        expected.add("movl -152(%rbp), %ebx # reload for @21");
        expected.add("movslq %ebx, %rax");
        expected.add("movq $2, %rbx");
        expected.add("movq %rbx, -160(%rbp) # spill for @23");
        expected.add("movq -160(%rbp), %rbx # get divisor");
        expected.add("cqto # sign extension to octoword");
        expected.add("idivq %rbx");
        expected.add("movl %eax, -168(%rbp) # spill for @24");
        expected.add("movl -240(%rbp), %ebx # reload for @35");
        expected.add("movl -168(%rbp), %r10d # reload for @24 [overwrite]");
        expected.add("addl %ebx, %r10d");
        expected.add("movl %r10d, -176(%rbp) # spill for @25");
        expected.add("movl -176(%rbp), %ebx # reload for @25");
        expected.add("movl %ebx, %r10d");
        expected.add("movl %r10d, -240(%rbp) # spill for @35");
        expected.add("movq -64(%rbp), %rbx # reload for @8");
        expected.add("movl 8(%rbx), %r10d");
        expected.add("movl %r10d, -184(%rbp) # spill for @26");
        expected.add("movl -184(%rbp), %ebx # reload for @26");
        expected.add("movslq %ebx, %rax");
        expected.add("movq $3, %rbx");
        expected.add("movq %rbx, -192(%rbp) # spill for @28");
        expected.add("movq -192(%rbp), %rbx # get divisor");
        expected.add("cqto # sign extension to octoword");
        expected.add("idivq %rbx");
        expected.add("movl %eax, -200(%rbp) # spill for @29");
        expected.add("movl -240(%rbp), %ebx # reload for @35");
        expected.add("movl -200(%rbp), %r10d # reload for @29 [overwrite]");
        expected.add("addl %ebx, %r10d");
        expected.add("movl %r10d, -208(%rbp) # spill for @30");
        expected.add("movl -208(%rbp), %ebx # reload for @30");
        expected.add("movl %ebx, %r10d");
        expected.add("movl %r10d, -240(%rbp) # spill for @35");
        expected.add("movl -32(%rbp), %ebx # reload for @4 [overwrite]");
        expected.add("addl $1, %ebx");
        expected.add("movl %ebx, -216(%rbp) # spill for @31");
        expected.add("movl -216(%rbp), %ebx # reload for @31");
        expected.add("movl %ebx, %r10d");
        expected.add("movl %r10d, -32(%rbp) # spill for @4");
        expected.add("jmp .L1");

        expected.add(".L3:");
        expected.add("movl -240(%rbp), %ebx # reload for @35 [overwrite]");
        expected.add("subl $1, %ebx");
        expected.add("movl %ebx, -224(%rbp) # spill for @32");
        expected.add("movl -224(%rbp), %edi # load @32 as arg 0");
        expected.add("call print@PLT");

        expected.add(ApplyAssignment.FINAL_BLOCK_LABEL + ":");
        expected.add("popq %r10 # restore callee-saved register");
        expected.add("popq %rbx # restore callee-saved register");
        expected.add("leave");
        expected.add("ret");

        assertEquals(expected, result);
    }
}
