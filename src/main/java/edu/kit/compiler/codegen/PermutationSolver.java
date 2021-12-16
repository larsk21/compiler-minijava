package edu.kit.compiler.codegen;

import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PermutationSolver {
    private Map<Integer, Integer> mapping;

    public PermutationSolver() {
        this.mapping = new HashMap<>();
    }

    public void addMapping(int input, int target) {
        mapping.put(input, target);
    }

    /**
     * Solves the mapping by returning a list of assignments that implements the mapping.
     * Also resets the state.
     *
     * @param temporary register that can be used for spilling, must not be part of the mapping
     * @return list of assignments
     */
    public List<Assignment> solve(int temporary) {
        assert !mapping.containsKey(temporary);

        List<Assignment> result = new ArrayList<>();
        List<Integer> assignOrder = new ArrayList<>();
        while (!mapping.isEmpty()) {
            // each iteration handles one "connected component"
            assignOrder.clear();
            int currentTarget = -1;
            for (Map.Entry<Integer, Integer> entry: mapping.entrySet()) {
                currentTarget = entry.getValue();
                assignOrder.add(entry.getKey());
                break;
            }
            while (mapping.containsKey(currentTarget)) {
                if (currentTarget == assignOrder.get(0) && assignOrder.size() > 1) {
                    // handle cycle
                    assignOrder.add(0, temporary);
                    currentTarget = temporary;
                    break;
                } else if (currentTarget == assignOrder.get(0)) {
                    // self-assignment
                    mapping.remove(currentTarget);
                    assignOrder.clear();
                    break;
                }
                assignOrder.add(currentTarget);
                currentTarget = mapping.get(currentTarget);
            }
            for (int i = assignOrder.size() - 1; i >= 0; i--) {
                int prev = assignOrder.get(i);
                result.add(new Assignment(prev, currentTarget));
                currentTarget = prev;
                mapping.remove(prev);
            }
        }

        assert mapping.isEmpty();
        return result;
    }

    @Data
    public static class Assignment {
        private int input;
        private int target;

        public Assignment(int input, int target) {
            this.input = input;
            this.target = target;
        }

        @Override
        public String toString() {
            return String.format("%d=>%d", input, target);
        }
    }
}
