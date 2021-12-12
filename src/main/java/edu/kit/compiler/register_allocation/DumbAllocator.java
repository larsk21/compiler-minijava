package edu.kit.compiler.register_allocation;

import edu.kit.compiler.data.ast_nodes.MethodNode;
import edu.kit.compiler.intermediate_lang.Instruction;
import edu.kit.compiler.intermediate_lang.RegisterSize;

import java.util.List;

/**
 * Allocate registers by assigning a stack slot to each vRegister.
 */
public class DumbAllocator implements RegisterAllocator {
    @Override
    public List<String> performAllocation(int nArgs, List<Instruction> input, RegisterSize[] sizes) {
        RegisterAssignment[] assignment = new RegisterAssignment[sizes.length];

        for (int i = 0; i < assignment.length; i++) {
            assignment[i] = new RegisterAssignment(-8 * (i + 1));
        }

        ApplyAssignment apply = new ApplyAssignment(assignment, sizes, input);
        var result = apply.doApply();
        // TODO: properly integrate prolog and epilog
        List<String> prolog = apply.createFunctionProlog(nArgs, result.getUsedRegisters());
        List<String> epilog = apply.createFunctionEpilog(nArgs);
        prolog.addAll(result.getInstructions());
        prolog.addAll(epilog);
        return prolog;
    }
}
