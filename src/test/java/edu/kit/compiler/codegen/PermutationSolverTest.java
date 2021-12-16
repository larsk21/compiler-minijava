package edu.kit.compiler.codegen;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

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

    @RepeatedTest(100)
    public void testRandomized() {
        PermutationSolver solver = new PermutationSolver();
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

        for (int i = 0; i < size; i++) {
            solver.addMapping(input.get(i), targets.get(i));
        }

        int[] data = new int[inputRange + 1];
        for (int i = 0; i <= inputRange; i++) {
            data[i] = i;
        }
        for (var ass: solver.solve(inputRange)) {
            data[ass.getTarget()] = data[ass.getInput()];
        }
        for (int i = 0; i < size; i++) {
            assertEquals(input.get(i), data[targets.get(i)]);
        }
    }
}
