package edu.kit.compiler.assembly;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

public class ElfAssemblyWriterTest {

    @Test
    public void testEmptyFile() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ElfAssemblyWriter assemblyWriter = new ElfAssemblyWriter();

        assemblyWriter.writeAssembly(Arrays.asList(), output);

        String result = output.toString();

        assertEquals(
            "        .text\n",
            result
        );
    }

    @Test
    public void testEmptyFunction() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ElfAssemblyWriter assemblyWriter = new ElfAssemblyWriter();

        assemblyWriter.writeAssembly(Arrays.asList(
            new FunctionInstructions("myFunction", Arrays.asList())
        ), output);

        String result = output.toString();

        assertEquals(
            "        .text\n" +
            "# -- Begin  myFunction\n" +
            "        .p2align 4,,15\n" +
            "        .globl myFunction\n" +
            "        .type myFunction, @function\n" +
            "myFunction:\n" +
            "        .size myFunction, .-myFunction\n" +
            "# -- End  myFunction\n" +
            "\n",
            result
        );
    }

    @Test
    public void testFunctionWithFinalLabel() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ElfAssemblyWriter assemblyWriter = new ElfAssemblyWriter();

        assemblyWriter.writeAssembly(Arrays.asList(
            new FunctionInstructions("myFunction", Arrays.asList(
                ".L_final"
            ))
        ), output);

        String result = output.toString();

        assertEquals(
            "        .text\n" +
            "# -- Begin  myFunction\n" +
            "        .p2align 4,,15\n" +
            "        .globl myFunction\n" +
            "        .type myFunction, @function\n" +
            "myFunction:\n" +
            ".LFE_myFunction:\n" +
            "        .size myFunction, .-myFunction\n" +
            "# -- End  myFunction\n" +
            "\n",
            result
        );
    }

    @Test
    public void testFunctionWithSingleBlock() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ElfAssemblyWriter assemblyWriter = new ElfAssemblyWriter();

        assemblyWriter.writeAssembly(Arrays.asList(
            new FunctionInstructions("myFunction", Arrays.asList(
                "pushq %rbp",
                "mov %rsp, %rbp",
                "mov %rsi, %rax",
                "imull %esi, %eax",
                "leave",
                "ret"
            ))
        ), output);

        String result = output.toString();

        assertEquals(
            "        .text\n" +
            "# -- Begin  myFunction\n" +
            "        .p2align 4,,15\n" +
            "        .globl myFunction\n" +
            "        .type myFunction, @function\n" +
            "myFunction:\n" +
            "        pushq %rbp\n" +
            "        mov %rsp, %rbp\n" +
            "        mov %rsi, %rax\n" +
            "        imull %esi, %eax\n" +
            "        leave\n" +
            "        ret\n" +
            "        .size myFunction, .-myFunction\n" +
            "# -- End  myFunction\n" +
            "\n",
            result
        );
    }

    @Test
    public void testFunctionWithMultipleBlocks() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ElfAssemblyWriter assemblyWriter = new ElfAssemblyWriter();

        assemblyWriter.writeAssembly(Arrays.asList(
            new FunctionInstructions("myFunction", Arrays.asList(
                "pushq %rbp",
                "mov %rsp, %rbp",
                "testl %esi, %esi",
                "jle .L1",
                ".L2:",
                "subl $1, %esi",
                "negl %esi",
                "addl $1, %esi",
                ".L1:",
                "movl $0x2, %eax",
                "imull %esi, %eax",
                "leave",
                "ret"
            ))
        ), output);

        String result = output.toString();

        assertEquals(
            "        .text\n" +
            "# -- Begin  myFunction\n" +
            "        .p2align 4,,15\n" +
            "        .globl myFunction\n" +
            "        .type myFunction, @function\n" +
            "myFunction:\n" +
            "        pushq %rbp\n" +
            "        mov %rsp, %rbp\n" +
            "        testl %esi, %esi\n" +
            "        jle .L1\n" +
            ".L2:\n" +
            "        subl $1, %esi\n" +
            "        negl %esi\n" +
            "        addl $1, %esi\n" +
            ".L1:\n" +
            "        movl $0x2, %eax\n" +
            "        imull %esi, %eax\n" +
            "        leave\n" +
            "        ret\n" +
            "        .size myFunction, .-myFunction\n" +
            "# -- End  myFunction\n" +
            "\n",
            result
        );
    }

    @Test
    public void testMultipleFunctions() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ElfAssemblyWriter assemblyWriter = new ElfAssemblyWriter();

        assemblyWriter.writeAssembly(Arrays.asList(
            new FunctionInstructions("myFunction1", Arrays.asList(
                "pushq %rbp",
	            "mov %rsp, %rbp",
	            "mov %rsi, %rax",
	            "leave",
	            "ret"
            )),
            new FunctionInstructions("myFunction2", Arrays.asList(
                "pushq %rbp",
                "mov %rsp, %rbp",
                "call Test_id_1",
                "leave",
                "ret"
            ))
        ), output);

        String result = output.toString();

        assertEquals(
            "        .text\n" +
            "# -- Begin  myFunction1\n" +
            "        .p2align 4,,15\n" +
            "        .globl myFunction1\n" +
            "        .type myFunction1, @function\n" +
            "myFunction1:\n" +
            "        pushq %rbp\n" +
            "        mov %rsp, %rbp\n" +
            "        mov %rsi, %rax\n" +
            "        leave\n" +
            "        ret\n" +
            "        .size myFunction1, .-myFunction1\n" +
            "# -- End  myFunction1\n" +
            "\n" +
            "# -- Begin  myFunction2\n" +
            "        .p2align 4,,15\n" +
            "        .globl myFunction2\n" +
            "        .type myFunction2, @function\n" +
            "myFunction2:\n" +
            "        pushq %rbp\n" +
            "        mov %rsp, %rbp\n" +
            "        call Test_id_1\n" +
            "        leave\n" +
            "        ret\n" +
            "        .size myFunction2, .-myFunction2\n" +
            "# -- End  myFunction2\n" +
            "\n",
            result
        );
    }

    @Test
    public void testFunctionWithComments() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ElfAssemblyWriter assemblyWriter = new ElfAssemblyWriter();

        assemblyWriter.writeAssembly(Arrays.asList(
            new FunctionInstructions("myFunction", Arrays.asList(
                "mov %eax, %eax # unnecessary move",
                "leave # leave the function",
                "ret # return"
            ))
        ), output);

        String result = output.toString();

        assertEquals(
            "        .text\n" +
            "# -- Begin  myFunction\n" +
            "        .p2align 4,,15\n" +
            "        .globl myFunction\n" +
            "        .type myFunction, @function\n" +
            "myFunction:\n" +
            "        mov %eax, %eax                  # unnecessary move\n" +
            "        leave                           # leave the function\n" +
            "        ret                             # return\n" +
            "        .size myFunction, .-myFunction\n" +
            "# -- End  myFunction\n" +
            "\n",
            result
        );
    }

}
