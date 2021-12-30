package edu.kit.compiler.register_allocation;

import edu.kit.compiler.data.ast_nodes.MethodNode;
import edu.kit.compiler.intermediate_lang.Block;
import edu.kit.compiler.intermediate_lang.Instruction;
import edu.kit.compiler.intermediate_lang.Register;
import edu.kit.compiler.intermediate_lang.RegisterSize;

import java.util.List;

/**
 * Allocate registers by assigning a stack slot to each vRegister.
 */
public class DumbAllocator implements RegisterAllocator {
    @Override
    public List<String> performAllocation(int nArgs, List<Block> input, RegisterSize[] sizes) {
        RegisterAssignment[] assignment = new RegisterAssignment[sizes.length];

        LifetimeAnalysis analysis = LifetimeAnalysis.run(input, sizes.length, nArgs);

        int slotIndex = 1;
        for (int i = 0; i < assignment.length; i++) {
            if (analysis.isAlive(i)) {
                assignment[i] = new RegisterAssignment(-8 * slotIndex);
                slotIndex++;
            } else {
                assignment[i] = new RegisterAssignment();
            }
        }

        return ApplyAssignment.createFunctionBody(
                assignment, sizes, analysis.getLifetimes(), input, analysis.getNumInstructions(), nArgs);
    }
}
