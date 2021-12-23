package edu.kit.compiler.codegen;

import com.sun.jna.Pointer;
import edu.kit.compiler.intermediate_lang.Instruction;
import firm.nodes.Block;
import firm.nodes.Node;
import firm.nodes.NodeVisitor;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PhiRemover {

    private final BasicBlocks basicBlocks;
    private static final Node nullNode = new Node(Pointer.createConstant(1)) {
        @Override
        public void accept(NodeVisitor nodeVisitor) {
            // do nothing
        }
    };

    public PhiRemover(BasicBlocks basicBlocks) {
        this.basicBlocks = basicBlocks;
    }

    /**
     * transforms this list of blocks into a new one without any phi instructions
     *
     * @return list of basic blocks without phis
     */
    public BasicBlocks removePhis() {
        for (var block : basicBlocks.getBlocks().entrySet()) {
            // collect phi instructions and use permutation solver to generate new instructions
            PermutationSolver permutationSolver = new PermutationSolver();
            Map<Block, List<Assignment>> assignments = new HashMap<>();
            for (var phiInstruction : block.getValue().getPhiInstructions()) {
                for (var source : phiInstruction.getEntries()) {
                    // split phi instructions in buckets belonging to their source node s
                    Block predBlock = source.getPredBlock();
                    List<Assignment> as = assignments.computeIfAbsent(predBlock, k -> new ArrayList<>());
                    Assignment a = new Assignment(source.getRegister(), phiInstruction.getTargetRegister());
                    as.add(a);
                }
            }
            // solve permutations for each block
            for (var entry : assignments.entrySet()) {
                Block b = entry.getKey();
                List<Assignment> a = entry.getValue();
                PermutationSolver solver = new PermutationSolver();

                int maxRegister = -1;
                for (var assignment : a) {
                    int max = Math.max(assignment.sourceRegister, assignment.destinationRegister);
                    if (max > maxRegister) {
                        maxRegister = max + 1;
                    }
                    solver.addMapping(assignment.sourceRegister, assignment.destinationRegister);
                }
                List<PermutationSolver.Assignment> permutationAssignments = solver.solve(maxRegister);
                BasicBlocks.BlockEntry e = basicBlocks.getEntry(b);
                for (var permutation : permutationAssignments) {
                    // insert phi instructions at beginning of block
                    e.getInstructions().add(0, assignmentToMove(permutation));
                }
            }
            // clear phis after they have been inserted at the right positions
            block.getValue().getPhiInstructions().clear();
        }
        return basicBlocks;
    }

    @Data
    @AllArgsConstructor
    private static class Assignment {
        public int sourceRegister;
        public int destinationRegister;
    }

    private Instruction assignmentToMove(PermutationSolver.Assignment a) {
        return Instruction.newMov(a.getInput(), a.getTarget());
    }
}
