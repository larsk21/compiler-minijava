package edu.kit.compiler.assembly;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class JumpInversionTest {

    private JumpInversion optimization;

    @BeforeEach
    public void setup() {
        optimization = new JumpInversion();
    }

    @Test
    public void testReplaceJNZ() {
        var res = optimize("jnz .L1", "jmp .L2", ".L1:");
        assertEquals(List.of("jz .L2", ".L1:"), res);
    }

    @Test
    public void testReplaceJG() {
        var res = optimize("jg .L1", "jmp .L2", ".L1:");
        assertEquals(List.of("jng .L2", ".L1:"), res);
    }

    @Test
    public void testNoDefaultJump() {
        var res = optimize("jnz .L2", ".L1");
        assertEquals(List.of("jnz .L2", ".L1"), res);
    }

    @Test
    public void testNoCondJump() {
        var res = optimize("addl %rax, %rbx", "jump .L2", ".L1");
        assertEquals(List.of("addl %rax, %rbx", "jump .L2", ".L1"), res);
    }

    @Test
    public void testNoLabel() {
        var res = optimize("jnz .L1", "jmp .L2", "addl %rax, %rbx");
        assertEquals(List.of("jnz .L1", "jmp .L2", "addl %rax, %rbx"), res);
    }

    private List<String> optimize(String... instructions) {
        var input = Arrays.stream(instructions).iterator();
        var output = optimization.optimize(input);
        var result = new ArrayList<String>();
        output.forEachRemaining(result::add);
        return result;
    }
}
