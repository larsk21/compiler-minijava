package edu.kit.compiler.codegen;

import firm.nodes.Node;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PhiPermutationSolver {

    private Map<PhiSourceNodeRegister, PhiSourceNodeRegister> mapping;
    private static final PhiSourceNodeRegister unknownPhiSource = new PhiSourceNodeRegister(-1, null);

    public PhiPermutationSolver() {
        this.mapping = new HashMap<>();
    }

    public void addMapping(PhiSourceNodeRegister input, PhiSourceNodeRegister target) {
        mapping.put(input, target);
    }

    /**
     * Solves the mapping by returning a list of assignments that implements the mapping.
     * Also resets the state.
     *
     * @param temporary register that can be used for spilling, must not be part of the mapping
     * @return list of assignments
     */
    public List<PhiAssignment> solve(PhiSourceNodeRegister temporary) {
        assert !mapping.containsKey(temporary);

        List<PhiAssignment> result = new ArrayList<>();
        List<PhiSourceNodeRegister> assignOrder = new ArrayList<>();
        while (!mapping.isEmpty()) {
            // each iteration handles one "connected component"
            assignOrder.clear();
            PhiSourceNodeRegister currentTarget = unknownPhiSource;
            for (Map.Entry<PhiSourceNodeRegister, PhiSourceNodeRegister> entry: mapping.entrySet()) {
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
                PhiSourceNodeRegister prev = assignOrder.get(i);
                result.add(new PhiAssignment(prev, currentTarget));
                currentTarget = prev;
                mapping.remove(prev);
            }
        }

        return result;
    }

    @Data
    public static class PhiAssignment {
        private PhiSourceNodeRegister input;
        private PhiSourceNodeRegister target;

        public PhiAssignment(PhiSourceNodeRegister input, PhiSourceNodeRegister target) {
            this.input = input;
            this.target = target;
        }

        @Override
        public String toString() {
            return String.format("%d=>%d", input, target);
        }
    }

    @Getter
    @RequiredArgsConstructor
    public static class PhiSourceNodeRegister {
        private final int sourceRegister;
        private final Node sourceNode;
    }

}
