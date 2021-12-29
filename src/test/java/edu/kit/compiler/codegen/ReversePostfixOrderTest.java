package edu.kit.compiler.codegen;

import edu.kit.compiler.intermediate_lang.Block;
import edu.kit.compiler.intermediate_lang.Instruction;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ReversePostfixOrderTest {
    @Test
    public void testIfElse() {
        Block ifBlock = new Block(List.of(
                Instruction.newJmp("", 1), Instruction.newJmp("", 2)
        ), 0, 0);
        Block thenBlock = new Block(List.of(
                Instruction.newJmp("", 3)
        ), 1, 0);
        Block elseBlock = new Block(List.of(
                Instruction.newJmp("", 3)
        ), 2, 0);
        Block finalBlock = new Block(List.of(
                Instruction.newRet(Optional.empty())
        ), 3, 0);

        var blocks = List.of(elseBlock, ifBlock, finalBlock, thenBlock);
        Map<Integer, Block> map = new HashMap<>();
        for (Block b: blocks) {
            map.put(b.getBlockId(), b);
        }
        var result = ReversePostfixOrder.apply(map, 0);
        assertEquals(List.of(0, 2, 1, 3),
                result.stream().map(Block::getBlockId).collect(Collectors.toList()));
        assertEquals(List.of(0, 0, 0, 0),
                result.stream().map(Block::getNumBackReferences).collect(Collectors.toList()));
    }

    @Test
    public void testSimpleLoop() {
        Block start = new Block(List.of(
                Instruction.newJmp("", 1)
        ), 0, 0);
        Block header = new Block(List.of(
                Instruction.newJmp("", 2), Instruction.newJmp("", 3)
        ), 1, 0);
        Block body = new Block(List.of(
                Instruction.newJmp("", 1)
        ), 2, 0);
        Block finalBlock = new Block(List.of(
                Instruction.newRet(Optional.empty())
        ), 3, 0);

        var blocks = List.of(start, finalBlock, body, header);
        Map<Integer, Block> map = new HashMap<>();
        for (Block b: blocks) {
            map.put(b.getBlockId(), b);
        }
        var result = ReversePostfixOrder.apply(map, 0);
        assertEquals(List.of(0, 1, 2, 3),
                result.stream().map(Block::getBlockId).collect(Collectors.toList()));
        assertEquals(List.of(0, 1, 0, 0),
                result.stream().map(Block::getNumBackReferences).collect(Collectors.toList()));
    }

    @Test
    public void testComplexLoop() {
        Block start = new Block(List.of(
                Instruction.newJmp("", 1)
        ), 0, 0);
        Block headerIf = new Block(List.of(
                Instruction.newJmp("", 2), Instruction.newJmp("", 3)
        ), 1, 0);
        Block headerThen = new Block(List.of(
                Instruction.newJmp("", 4)
        ), 2, 0);
        Block headerElse = new Block(List.of(
                Instruction.newJmp("", 4)
        ), 3, 0);
        Block headerFinal = new Block(List.of(
                Instruction.newJmp("", 5), Instruction.newJmp("", 8)
        ), 4, 0);
        Block bodyIf = new Block(List.of(
                Instruction.newJmp("", 6), Instruction.newJmp("", 7)
        ), 5, 0);
        Block bodyThen = new Block(List.of(
                Instruction.newJmp("", 1)
        ), 6, 0);
        Block bodyElse = new Block(List.of(
                Instruction.newJmp("", 1)
        ), 7, 0);
        Block finalBlock = new Block(List.of(
                Instruction.newRet(Optional.empty())
        ), 8, 0);

        var blocks = List.of(start, headerIf, headerThen, headerElse, headerFinal,
                bodyIf, bodyThen, bodyElse, finalBlock);
        Map<Integer, Block> map = new HashMap<>();
        for (Block b: blocks) {
            map.put(b.getBlockId(), b);
        }
        var result = ReversePostfixOrder.apply(map, 0);
        assertEquals(List.of(0, 1, 3, 2, 4, 5, 7, 6, 8),
                result.stream().map(Block::getBlockId).collect(Collectors.toList()));
        assertEquals(List.of(0, 2, 0, 0, 0, 0, 0, 0, 0),
                result.stream().map(Block::getNumBackReferences).collect(Collectors.toList()));
    }

    @Test
    public void testNestedComplexLoopWithRet() {
        Block start = new Block(List.of(
                Instruction.newJmp("", 1)
        ), 0, 0);
        Block headerIf = new Block(List.of(
                Instruction.newJmp("", 2), Instruction.newJmp("", 3)
        ), 1, 0);
        Block headerThen = new Block(List.of(
                Instruction.newJmp("", 4)
        ), 2, 0);
        Block headerElse = new Block(List.of(
                Instruction.newJmp("", 4)
        ), 3, 0);
        Block headerFinal = new Block(List.of(
                Instruction.newJmp("", 5), Instruction.newJmp("", 10)
        ), 4, 0);

        Block nestedHeader = new Block(List.of(
                Instruction.newJmp("", 6), Instruction.newJmp("", 9)
        ), 5, 0);
        Block nestedBodyIf = new Block(List.of(
                Instruction.newJmp("", 7), Instruction.newJmp("", 8)
        ), 6, 0);
        Block nestedBodyThen = new Block(List.of(
                Instruction.newRet(Optional.empty())
        ), 7, 0);
        Block nestedBodyElse = new Block(List.of(
                Instruction.newJmp("", 5)
        ), 8, 0);
        Block nestedExit = new Block(List.of(
                Instruction.newJmp("", 1)
        ), 9, 0);


        Block finalBlock = new Block(List.of(
                Instruction.newRet(Optional.empty())
        ), 10, 0);

        var blocks = List.of(start, headerIf, headerThen, headerElse, headerFinal,
                nestedHeader, nestedBodyIf, nestedBodyThen, nestedBodyElse, nestedExit, finalBlock);
        Map<Integer, Block> map = new HashMap<>();
        for (Block b: blocks) {
            map.put(b.getBlockId(), b);
        }
        var result = ReversePostfixOrder.apply(map, 0);
        assertEquals(List.of(0, 1, 3, 2, 4, 5, 6, 8, 7, 9, 10),
                result.stream().map(Block::getBlockId).collect(Collectors.toList()));
        assertEquals(List.of(0, 1, 0, 0, 0, 1, 0, 0, 0, 0, 0),
                result.stream().map(Block::getNumBackReferences).collect(Collectors.toList()));
    }
}
