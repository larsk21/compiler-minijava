package edu.kit.compiler.codegen;

import com.sun.jna.Pointer;
import edu.kit.compiler.codegen.PhiPermutationSolver.PhiAssignment;
import edu.kit.compiler.codegen.PhiPermutationSolver.PhiSourceNodeRegister;
import edu.kit.compiler.intermediate_lang.Instruction;
import edu.kit.compiler.intermediate_lang.RegisterSize;
import firm.Mode;
import firm.nodes.Block;
import firm.nodes.Node;
import firm.nodes.NodeVisitor;

import java.util.List;
import java.util.Optional;

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
            PhiPermutationSolver permutationSolver = new PhiPermutationSolver();
            int maxRegister = 0;
            for (var phiInstruction : block.getValue().getPhiInstructions()) {
                for (var source : phiInstruction.getEntries()) {
                    Block predBlock = source.getPredBlock();
                    PhiSourceNodeRegister sourceRegister = new PhiSourceNodeRegister(source.getRegister(), predBlock);
                    PhiSourceNodeRegister targetRegister = new PhiSourceNodeRegister(phiInstruction.getTargetRegister(), nullNode);
                    permutationSolver.addMapping(sourceRegister, targetRegister);
                    int max = Math.max(source.getRegister(), phiInstruction.getTargetRegister());
                    if (max > maxRegister) {
                        maxRegister = max + 1;
                    }
                }
            }
            // solve permutations
            List<PhiAssignment> permutations = permutationSolver.solve(new PhiSourceNodeRegister(maxRegister, nullNode));
            List<Instruction> instructions = block.getValue().getInstructions();

            for (var permutation : permutations) {
                // insert phi instructions at beginning of block
                Node n = permutation.getInput().getSourceNode();
                BasicBlocks.BlockEntry e = basicBlocks.getEntry(n);
                e.getInstructions().add(0, assignmentToMove(permutation));
            }

            // clear phis
            block.getValue().getPhiInstructions().clear();
        }
        return basicBlocks;
    }

    private Instruction assignmentToMove(PhiAssignment a) {
        return Instruction.newMov(a.getInput().getSourceRegister(), a.getTarget().getSourceRegister());
    }
}
