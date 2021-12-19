package edu.kit.compiler.register_allocation;

import edu.kit.compiler.intermediate_lang.Block;
import edu.kit.compiler.intermediate_lang.Instruction;
import edu.kit.compiler.intermediate_lang.RegisterSize;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LinearScanTest {

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

        RegisterAllocator alloc = new LinearScan();
        var result = alloc.performAllocation(0, List.of(block), sizes);
        var expected = new ArrayList<>();
        expected.add("pushq %rbp");
        expected.add("movq %rsp, %rbp");

        expected.add(".L0:");
        expected.add("movl $0x1, %edi");
        expected.add("movl $0x4, %esi");
        expected.add("pushq %rsi # push caller-saved register");
        expected.add("pushq %rdi # push caller-saved register");
        expected.add("movq 0(%rsp), %rdi # reload @6 as arg 0");
        expected.add("movq 8(%rsp), %rsi # reload @7 as arg 1");
        expected.add("call calloc@PLT");
        expected.add("addq $8, %rsp # clear stack");
        expected.add("addq $8, %rsp # clear stack");
        expected.add("movl $0x7, %edx");
        expected.add("addl $77, %edx");
        expected.add("movl %edx, (%rax)");
        expected.add("movslq (%rax), %rax");
        expected.add("movq $0x2, %rcx");
        expected.add("cqto # sign extension to octoword");
        expected.add("idivq %rcx");
        expected.add("movl %eax, %edi # move result to @3");
        expected.add("pushq %rdi # push caller-saved register");
        expected.add("movq 0(%rsp), %rdi # reload @3 as arg 0");
        expected.add("call print@PLT");
        expected.add("addq $8, %rsp # clear stack");

        expected.add(ApplyAssignment.FINAL_BLOCK_LABEL + ":");
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

        RegisterAllocator alloc = new LinearScan();
        var result = alloc.performAllocation(0, List.of(block0, block1, block2, block3), sizes);

        var expected = new ArrayList<>();
        expected.add("pushq %rbp");
        expected.add("movq %rsp, %rbp");
        expected.add("subq $8, %rsp # allocate activation record");
        expected.add("pushq %rbx # push callee-saved register");

        expected.add(".L0:");
        expected.add("movl $10, %ebx");
        expected.add("movl $4, %esi");
        expected.add("pushq %rsi # push caller-saved register");
        expected.add("movl %ebx, %edi # move @1 into arg 0");
        expected.add("movq 0(%rsp), %rsi # reload @2 as arg 1");
        expected.add("call calloc@PLT");
        expected.add("movq %rax, %rcx # move return value into @3");
        expected.add("addq $8, %rsp # clear stack");
        expected.add("movl $1, 8(%rcx)");
        expected.add("movl $2, 12(%rcx)");
        expected.add("movl $3, 16(%rcx)");
        expected.add("movl $4, 20(%rcx)");
        expected.add("movl $5, 24(%rcx)");
        expected.add("movl $6, 28(%rcx)");
        expected.add("movl $2, %esi");
        expected.add("movl $0, %edi");

        expected.add(".L1:");
        expected.add("movl %ebx, %eax");
        expected.add("subl $2, %eax");
        expected.add("cmpl %esi, %eax");
        expected.add("jle .L3");

        expected.add(".L2:");
        expected.add("movslq %esi, %rax");
        expected.add("imulq $4, %rax");
        expected.add("movq %rax, %r8 # move for @7 [overwrite]");
        expected.add("addq %rcx, %r8");
        expected.add("movl -8(%r8), %eax");
        expected.add("movslq %eax, %rax");
        expected.add("movq $3, %r9");
        expected.add("cqto # sign extension to octoword");
        expected.add("idivq %r9");
        expected.add("addl %edi, %eax");
        expected.add("movl %eax, %edi");
        expected.add("movl -4(%r8), %eax");
        expected.add("movslq %eax, %rax");
        expected.add("movq $2, %r9");
        expected.add("cqto # sign extension to octoword");
        expected.add("idivq %r9");
        expected.add("addl %edi, %eax");
        expected.add("movl %eax, %edi");
        expected.add("movl (%r8), %eax");
        expected.add("addl %edi, %eax");
        expected.add("movl %eax, %edi");
        expected.add("movl 4(%r8), %eax");
        expected.add("movslq %eax, %rax");
        expected.add("movq $2, %r9");
        expected.add("cqto # sign extension to octoword");
        expected.add("idivq %r9");
        expected.add("addl %edi, %eax");
        expected.add("movl %eax, %edi");
        expected.add("movl 8(%r8), %eax");
        expected.add("movslq %eax, %rax");
        expected.add("movq $3, %r8");
        expected.add("cqto # sign extension to octoword");
        expected.add("idivq %r8");
        expected.add("addl %edi, %eax");
        expected.add("movl %eax, %edi");
        expected.add("movl %esi, %eax # move for @4 [overwrite]");
        expected.add("addl $1, %eax");
        expected.add("movl %eax, %esi");
        expected.add("jmp .L1");

        expected.add(".L3:");
        expected.add("subl $1, %edi");
        expected.add("pushq %rdi # push caller-saved register");
        expected.add("movq 0(%rsp), %rdi # reload @32 as arg 0");
        expected.add("call print@PLT");
        expected.add("addq $8, %rsp # clear stack");

        expected.add(ApplyAssignment.FINAL_BLOCK_LABEL + ":");
        expected.add("popq %rbx # restore callee-saved register");
        expected.add("leave");
        expected.add("ret");

        assertEquals(expected, result);
    }
}
