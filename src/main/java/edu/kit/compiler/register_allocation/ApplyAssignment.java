package edu.kit.compiler.register_allocation;

import edu.kit.compiler.intermediate_lang.Instruction;
import edu.kit.compiler.intermediate_lang.RegisterSize;

import java.util.List;

public class ApplyAssignment {
    public static AssignmentResult apply(RegisterAssignment[] assignment, RegisterSize[] sizes,
                                         Lifetime[] lifetimes, List<Instruction> ir) {
        assert assignment.length == sizes.length && assignment.length == lifetimes.length;
        assertRegistersDontInterfere(assignment, sizes, lifetimes);

        throw new UnsupportedOperationException();
    }

    /**
     * Assumes that all register lifetimes interfere.
     */
    public static AssignmentResult apply(RegisterAssignment[] assignment, RegisterSize[] sizes,
                                         List<Instruction> ir) {
        assert assignment.length == sizes.length;

        // create trivial lifetimes
        Lifetime[] lifetimes = new Lifetime[assignment.length];
        for(int i = 0; i < lifetimes.length; i++) {
            lifetimes[i] = new Lifetime(0, ir.size(), false);
        }
        return apply(assignment, sizes, lifetimes, ir);
    }

    private static void assertRegistersDontInterfere(RegisterAssignment[] assignment,
                                                     RegisterSize[] sizes, Lifetime[] lifetimes) {
        for(int i = 0; i < lifetimes.length; i++) {
            for(int j = i + 1; j < lifetimes.length; j++) {
                if (lifetimes[i].interferes(lifetimes[j])) {
                    if (!assignment[i].isSpilled() && !assignment[j].isSpilled()) {
                        assert assignment[i].getRegister().get() != assignment[j].getRegister().get();
                    } else if (assignment[i].isSpilled() && assignment[j].isSpilled()) {
                        int slotA = assignment[i].getStackSlot().get();
                        int slotB = assignment[j].getStackSlot().get();
                        // assert that stack slots are disjoint
                        assert slotA >= slotB + sizes[j].getBytes();
                        assert slotB >= slotA + sizes[i].getBytes();
                    }
                }
            }
        }
    }
}
