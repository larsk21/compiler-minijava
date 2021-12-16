package edu.kit.compiler.codegen;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PermutationSolverTest {
    @Test
    public void testSelfAssign() {
        PermutationSolver solver = new PermutationSolver();
        solver.addMapping(0, 0);
        solver.addMapping(1, 1);
        for (var ass: solver.solve(2)) {
            assert false;
        }
    }

    @Test
    public void testSwap() {
        List<Integer> input = List.of(0, 1);
        List<Integer> targets = List.of(1, 0);
        checkForData(input, targets, 2);
    }

    @Test
    public void testCycle() {
        List<Integer> input = List.of(0, 1, 2);
        List<Integer> targets = List.of(2, 0, 1);
        checkForData(input, targets, 3);
    }

    @Test
    public void testTree() {
        List<Integer> input = List.of(0, 1, 0, 1);
        List<Integer> targets = List.of(1, 3, 2, 4);
        checkForData(input, targets, 5);
    }

    @Test
    public void testCycleWithLeafs1() {
        List<Integer> input = List.of(0, 1, 2, 0, 1, 2, 0, 1, 2);
        List<Integer> targets = List.of(1, 2, 0, 3, 4, 5, 6, 7, 8);
        checkForData(input, targets, 9);
    }

    @Test
    public void testCycleWithLeafs2() {
        List<Integer> input = List.of(0, 1, 2, 0, 1, 2, 0, 1, 2);
        List<Integer> targets = List.of(3, 4, 5, 6, 7, 8, 1, 2, 0);
        checkForData(input, targets, 9);
    }

    @Test
    public void testBadInput() {
        List<Integer> input = List.of(3, 3, 3, 0, 0, 3, 2, 3, 4, 1, 1);
        List<Integer> targets = List.of(10, 4, 11, 6, 1, 8, 3, 2, 5, 9, 7);
        checkForData(input, targets, 12);
    }

    @RepeatedTest(100)
    public void testRandomized() {
        int size = (int)(10 * Math.random());
        int inputRange = size + (int)(3 * Math.random());
        List<Integer> input = new ArrayList<>();
        List<Integer> targets = new ArrayList<>();

        for (int i = 0; i < size; i++) {
            int next;
            do {
                next = (int) (inputRange * Math.random());
            } while (input.contains(next));
            input.add(next);
        }

        for (int i = 0; i < size; i++) {
            int next;
            do {
                next = (int)(size * Math.random());
            } while (targets.contains(next));
            targets.add(next);
        }

        checkForData(input, targets, inputRange);
    }

    @RepeatedTest(100)
    public void testRandomizedOverlappingInput() {
        int size = (int)(10 * Math.random()) + 2;
        List<Integer> input = new ArrayList<>();
        List<Integer> targets = new ArrayList<>();

        for (int i = 0; i < size; i++) {
            input.add((int)(size * Math.random()) / 2);
        }

        for (int i = 0; i < size; i++) {
            int next;
            do {
                next = (int)((size + 1) * Math.random());
            } while (targets.contains(next));
            targets.add(next);
        }

        checkForData(input, targets, size + 2);

        for (int first: input) {
            if (!targets.contains(first)) {
                checkForData(input, targets, size, Optional.of(first));
            }
        }
    }

    private void checkForData(List<Integer> input, List<Integer> targets, int maxInput) {
        checkForData(input, targets, maxInput, Optional.empty());
    }

    private void checkForData(List<Integer> input, List<Integer> targets, int maxInput, Optional<Integer> first) {
        assertEquals(input.size(), targets.size());
        PermutationSolver solver = new PermutationSolver();

        for (int i = 0; i < input.size(); i++) {
            solver.addMapping(input.get(i), targets.get(i));
        }

        int[] data = new int[maxInput + 1];
        for (int i = 0; i <= maxInput; i++) {
            data[i] = i;
        }
        for (var ass: first.isPresent() ? solver.solveFromNonCycle(first.get()) : solver.solve(maxInput)) {
            data[ass.getTarget()] = data[ass.getInput()];
        }
        for (int i = 0; i < input.size(); i++) {
            assertEquals(input.get(i), data[targets.get(i)], "index=" + i);
        }
    }
}
