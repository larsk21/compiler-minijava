package edu.kit.compiler.register_allocation;

import edu.kit.compiler.intermediate_lang.Instruction;
import edu.kit.compiler.intermediate_lang.Register;
import edu.kit.compiler.intermediate_lang.RegisterSize;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

public class ApplyAssignment {
    private RegisterAssignment[] assignment;
    private RegisterSize[] sizes;
    private Lifetime[] lifetimes;
    private List<Instruction> ir;

    public ApplyAssignment(RegisterAssignment[] assignment, RegisterSize[] sizes,
                           Lifetime[] lifetimes, List<Instruction> ir) {
        assert assignment.length == sizes.length && assignment.length == lifetimes.length;
        this.assignment = assignment;
        this.sizes = sizes;
        this.lifetimes = lifetimes;
        this.ir = ir;

        assertRegistersDontInterfere(assignment, sizes, lifetimes);
    }

    /**
     * Assumes that all register lifetimes interfere.
     */
    public ApplyAssignment(RegisterAssignment[] assignment, RegisterSize[] sizes, List<Instruction> ir) {
        this(assignment, sizes, trivialLifetimes(assignment.length, ir.size()), ir);
    }

    public AssignmentResult doApply() {
        throw new UnsupportedOperationException();
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

    private static Lifetime[] trivialLifetimes(int nRegisters, int nInstructions) {
        Lifetime[] lifetimes = new Lifetime[nRegisters];
        for(int i = 0; i < nRegisters; i++) {
            lifetimes[i] = new Lifetime(0, nInstructions, false);
        }
        return  lifetimes;
    }

    /**
     * Tracks the mapping of hardware registers to virtual registers during the
     * allocation in order to correctly provide free registers when needed
     */
    private class LifetimeTracker {
        private RegisterTracker registers;
        private List<Integer> lifetimeStarts;
        private List<Integer> lifetimeEnds;

        // prevents erroneously requesting temporary registers twice
        private boolean tmpRequested;

        LifetimeTracker() {
            this.registers = new RegisterTracker();
            this.lifetimeStarts = sortByLifetime(lifetimes, (l1, l2) -> l1.getBegin() - l2.getBegin());
            this.lifetimeEnds = sortByLifetime(lifetimes, (l1, l2) -> {
                int val = l1.getEnd() - l2.getEnd();
                if (val != 0) {
                    return val;
                }
                if (l1.isLastInstrIsInput() && !l2.isLastInstrIsInput()) {
                    return -1;
                } else if (!l1.isLastInstrIsInput() && l2.isLastInstrIsInput()) {
                    return 1;
                }
                return 0;
            });
            this.tmpRequested = false;

            assert lifetimes.length == lifetimeStarts.size();
            assert lifetimes.length == lifetimeEnds.size();
        }

        /**
         * Temporary register do _not_ outlive the execution of an original instruction
         * (unless special care is taken).
         */
        public List<Register> getTmpRegisters(int num) {
            assert !tmpRequested;
            tmpRequested = true;
            return registers.getFreeRegisters(num);
        }

        /**
         * sets the state before the instruction is executed
         */
        public void enterInstruction(int index) {
            assert lifetimes[peek(lifetimeStarts)].getBegin() >= index;

            tmpRequested = false;
            while (!lifetimeEnds.isEmpty() && lifetimes[peek(lifetimeEnds)].getEnd() <= index) {
                clearRegister(pop(lifetimeEnds));
            }
        }

        /**
         * sets the state after the instruction is executed
         */
        public void leaveInstruction(int index) {
            assert lifetimes[peek(lifetimeEnds)].getEnd() > index;

            tmpRequested = false;
            while (!lifetimeEnds.isEmpty() && lifetimes[peek(lifetimeEnds)].getEnd() == index + 1 &&
                    // `isLastInstrIsInput` must be checked here, otherwise the state could be invalid at the end of a loop
                    lifetimes[peek(lifetimeEnds)].isLastInstrIsInput()) {
                clearRegister(pop(lifetimeEnds));
            }
            while (!lifetimeStarts.isEmpty() && lifetimes[peek(lifetimeStarts)].getBegin() <= index) {
                int vRegister = pop(lifetimeStarts);
                Register r = assignment[vRegister].getRegister().get();
                registers.set(r, vRegister);
            }
        }

        private List<Integer> sortByLifetime(Lifetime[] lifetimes, Comparator<Lifetime> compare) {
            List<Integer> result = new ArrayList<>();
            for (int i = 0; i < lifetimes.length; i++) {
                result.add(i);
            }
            // We want to sort in descending order, so we can pop the last elements
            result.sort((j, k) -> compare.compare(lifetimes[k],lifetimes[j]));
            return result;
        }

        private void clearRegister(int vRegister) {
            Register r = assignment[vRegister].getRegister().get();
            registers.clear(r);
        }

        private int pop(List<Integer> l) {
            return l.remove(l.size()- 1);
        }

        private int peek(List<Integer> l) {
            return l.get(l.size()- 1);
        }
    }
}
