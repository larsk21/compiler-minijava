package edu.kit.compiler.assembly;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

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
        var expected = List.of("jz .L2", ".L1:");
        var actual = optimize("jnz .L1", "jmp .L2", ".L1:");

        assertEquals(Optional.of(expected), actual);
    }

    @Test
    public void testReplaceJG() {
        var expected = List.of("jng .L2", ".L1:");
        var actual = optimize("jg .L1", "jmp .L2", ".L1:");

        assertEquals(Optional.of(expected), actual);
    }

    @Test
    public void testReplaceComplexLabel() {
        var expected = List.of("jle .LFE_Main_main", ".Labc_1234_foo:");
        var actual = optimize("jnle .Labc_1234_foo", "jmp .LFE_Main_main", ".Labc_1234_foo:");

        assertEquals(Optional.of(expected), actual);
    }

    @Test
    public void testReplaceIllegalLabel() {
        var actual = optimize("jnle .Labc_1234_foo", "jmp abc", ".Labc_1234_foo:");

        assertEquals(Optional.empty(), actual);
    }

    @Test
    public void testNoDefaultJump() {
        var actual = optimize(".L0", "jnz .L2", ".L1");
        assertEquals(Optional.empty(), actual);
    }

    @Test
    public void testNoCondJump() {
        var actual = optimize("addl %rax, %rbx", "jump .L2", ".L1");
        assertEquals(Optional.empty(), actual);
    }

    @Test
    public void testNoLabel() {
        var actual = optimize("jnz .L1", "jmp .L2", "addl %rax, %rbx");
        assertEquals(Optional.empty(), actual);
    }

    private Optional<List<String>> optimize(String... instructions) {
        return optimization.optimize(instructions).map(Arrays::asList);
    }
}
