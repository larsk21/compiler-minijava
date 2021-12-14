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
                if (analysis.isDividend(i)) {
                    // dividend must be assigned to %rax
                    assignment[i] = new RegisterAssignment(Register.RAX);
                } else {
                    assignment[i] = new RegisterAssignment(-8 * slotIndex);
                    slotIndex++;
                }
            }
        }

        ApplyAssignment apply = new ApplyAssignment(assignment, sizes, analysis.getLifetimes(), input);
        var result = apply.doApply();
        // TODO: properly integrate prolog and epilog
        List<String> prolog = apply.createFunctionProlog(nArgs, result.getUsedRegisters());
        List<String> epilog = apply.createFunctionEpilog();
        prolog.addAll(result.getInstructions());
        prolog.addAll(epilog);
        return prolog;
    }
}
