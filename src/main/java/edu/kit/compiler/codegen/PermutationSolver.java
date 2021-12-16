package edu.kit.compiler.codegen;

import lombok.Data;

import java.util.*;

public class PermutationSolver {
    private Map<Integer, Deque<Integer>> mapping;

    public PermutationSolver() {
        this.mapping = new HashMap<>();
    }

    public void addMapping(int input, int target) {
        if (mapping.containsKey(input)) {
            mapping.get(input).push(target);
        } else {
            Deque<Integer> vals = new ArrayDeque<>();
            vals.push(target);
            mapping.put(input, vals);
        }
    }

    public void addMapping(Assignment assignment) {
        addMapping(assignment.getInput(), assignment.getTarget());
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

            int firstInput = -1;
            int currentTarget = -1;
            Deque<Integer> currentVals = null;
            for (var entry: mapping.entrySet()) {
                currentVals = entry.getValue();
                firstInput = entry.getKey();
                currentTarget = currentVals.pop();
                assignOrder.add(firstInput);
                break;
            }

            boolean cycle = false;
            while (mapping.containsKey(currentTarget)) {
                if (currentTarget == firstInput) {
                    if (assignOrder.size() > 1) {
                        // handle cycle
                        assignOrder.add(0, temporary);
                        mapping.put(firstInput, currentVals);
                        currentTarget = temporary;
                    } else {
                        // self-assignment
                        if (currentVals.isEmpty()) {
                            mapping.remove(firstInput);
                        }
                        assignOrder.clear();
                    }
                    cycle = true;
                    break;
                }

                // if more assignments are remaining for this input,
                // we propagate them to the target so that they are
                // processed in another round
                Deque<Integer> tmp = currentVals;
                currentVals = mapping.get(currentTarget);
                mapping.put(currentTarget, tmp);
                assignOrder.add(currentTarget);
                currentTarget = currentVals.pop();
            }

            // update mapping for beginning and end if there was no cycle
            if (!cycle) {
                mapping.remove(firstInput);
                if (!currentVals.isEmpty()) {
                    mapping.put(currentTarget, currentVals);
                }
            }

            // output moves and cleanup
            for (int i = assignOrder.size() - 1; i >= 0; i--) {
                int prev = assignOrder.get(i);
                result.add(new Assignment(prev, currentTarget));
                currentTarget = prev;
                if (mapping.containsKey(prev) && mapping.get(prev).isEmpty()) {
                    mapping.remove(prev);
                }
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
