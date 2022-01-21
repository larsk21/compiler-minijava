package edu.kit.compiler.codegen;

import edu.kit.compiler.intermediate_lang.Block;
import edu.kit.compiler.intermediate_lang.Instruction;
import edu.kit.compiler.intermediate_lang.RegisterSize;
import firm.bindings.binding_irnode;
import firm.nodes.Cond;
import firm.nodes.Node;
import firm.nodes.Proj;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Transforms the output of the instruction selection by replacing the phis
 * and returning a mapping from block ids to IL blocks.
 *
 * Note: the blocks are _not_ in topological order yet and the number of
 * backrefs is not set.
 */
public class PhiResolver {
    public static Map<Integer, Block> apply(InstructionSelection selection) {
        Map<Integer, Block> mapping = new HashMap<>();
        for (var block : selection.getBlocks().getEntries()) {
            resolvePhisForBlock(selection, mapping, block);
        }

        translateExistingBlocks(selection, mapping);
        return mapping;
    }

    private static void translateExistingBlocks(InstructionSelection selection, Map<Integer, Block> mapping) {
        for (var block : selection.getBlocks().getEntries()) {
            Block ilBlock = new Block(block.getLabel());
            ilBlock.getInstructions().addAll(block.getInstructions());
            ilBlock.getInstructions().addAll(block.getExitInstructions());

            assert !mapping.containsKey(ilBlock.getBlockId());
            mapping.put(ilBlock.getBlockId(), ilBlock);
        }
    }

    private static void resolvePhisForBlock(InstructionSelection selection, Map<Integer, Block> mapping,
                                            BasicBlocks.BlockEntry block) {
        Map<Integer, PhiAssignments> predMapping = new HashMap<>();

        // for each predecessor block, collect the required phi assignments
        for (var phiInstruction : block.getPhiInstructions()) {
            var entries = phiInstruction.getEntries();
            for (int i = 0; i < entries.size(); i++) {
                var predBlock = selection.getBlocks().getEntry(entries.get(i).getPredBlock());
                var assignments = predMapping.computeIfAbsent(
                        i, j -> new PhiAssignments(predBlock, block.getFirmBlock().getPred(j))
                );
                assert assignments.getPred().equals(predBlock);
                assignments.add(entries.get(i).getRegister(), phiInstruction.getTargetRegister());
            }
        }

        PermutationSolver solver = new PermutationSolver();
        for (PhiAssignments assignments: predMapping.values()) {
            List<Instruction> instructions = new ArrayList<>();

            // solve the permutation and add the according move instructions
            for (PermutationSolver.Assignment ass: assignments.getAssignments()) {
                solver.addMapping(ass);
            }
            int tmpRegister = selection.getMatcher().getNewRegister(RegisterSize.QUAD);
            for (PermutationSolver.Assignment ass: solver.solve(tmpRegister)) {
                instructions.add(Instruction.newUnsignedMov(ass.getInput(), ass.getTarget()));
            }

            ExitCondition condition = assignments.getPred().getExitCondition().get();
            if (condition.isUnconditional()) {
                // add instructions to predecessor block
                assignments.getPred().append(instructions);
            } else {
                // add a new block if the edge is critical
                instructions.add(Instruction.newJmp(
                        Util.formatJmp("jmp", block.getLabel()), block.getLabel())
                );

                int newBlockId = selection.getBlocks().newLabel();
                Block newBlock = new Block(instructions, newBlockId, 0);

                assert !mapping.containsKey(newBlockId);
                mapping.put(newBlockId, newBlock);
                condition.replaceBlock(assignments.isTrueExit(), newBlockId);
            }
        }
    }

    /**
     * Phi assignments that belong to a specific predecessor block.
     */
    @Data
    private static class PhiAssignments {
        private final BasicBlocks.BlockEntry pred;
        private final Node controlFlowPredecessor;
        private final  List<PermutationSolver.Assignment> assignments = new ArrayList<>();

        public void add(int source, int target) {
            assignments.add(new PermutationSolver.Assignment(source, target));
        }

        public boolean isTrueExit() {
            assert controlFlowPredecessor.getOpCode() == binding_irnode.ir_opcode.iro_Proj;
            Proj condProj = (Proj) controlFlowPredecessor;
            return condProj.getNum() == Cond.pnTrue;
        }
    }
}
