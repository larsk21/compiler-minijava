package edu.kit.compiler.register_allocation;

import edu.kit.compiler.intermediate_lang.Instruction;
import edu.kit.compiler.intermediate_lang.InstructionType;
import edu.kit.compiler.intermediate_lang.Register;
import edu.kit.compiler.intermediate_lang.RegisterSize;
import edu.kit.compiler.logger.Logger;
import lombok.Getter;

import java.util.*;

public class ApplyAssignment {
    private RegisterAssignment[] assignment;
    private RegisterSize[] sizes;
    private Lifetime[] lifetimes;
    private List<Instruction> ir;
    private List<String> result;

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
        this(assignment, sizes, completeLifetimes(assignment.length, ir.size()), ir);
    }

    public AssignmentResult doApply(Logger logger) {
        result = new ArrayList<>();
        LifetimeTracker tracker = new LifetimeTracker();
        Map<Integer, String> replace = new HashMap<>();
        tracker.printState(logger);

        for (int i = 0; i < ir.size(); i++) {

            switch (ir.get(i).getType()) {
                case GENERAL -> {
                    handleGeneralInstruction(tracker, replace, i);
                    tracker.printState(logger);
                }
                default -> throw new UnsupportedOperationException();
            }
        }

        return new AssignmentResult(result, tracker.getRegisters().getUsedRegisters());
    }

    private void handleGeneralInstruction(LifetimeTracker tracker, Map<Integer, String> replace, int index) {
        Instruction instr = ir.get(index);
        tracker.enterInstruction(index);
        List<Register> tmpRegisters = tracker.getTmpRegisters(countRequiredTmps(instr));

        // handle spilled input registers and set names
        int tmpIdx = 0;
        for (int vRegister: instr.getInputRegisters()) {
            RegisterSize size = sizes[vRegister];
            String registerName;
            if (assignment[vRegister].isSpilled()) {
                int stackSlot = assignment[vRegister].getStackSlot().get();
                registerName = tmpRegisters.get(tmpIdx).asSize(size);
                output("mov%c %d(%%rbp), %s # reload for @%s",
                        size.getSuffix(), stackSlot, registerName, vRegister);
                tmpIdx++;
            } else {
                Register r = assignment[vRegister].getRegister().get();
                tracker.assertMapping(vRegister, r);
                registerName = r.asSize(size);
            }
            replace.put(vRegister, registerName);
        }

        if (!instr.getTargetRegister().isPresent()) {
            // output the instruction itself
            output(instr.mapRegisters(replace));
            tracker.leaveInstruction(index);
        } else {
            int target = instr.getTargetRegister().get();
            RegisterSize size = sizes[target];
            Register tRegister;
            if (assignment[target].isSpilled()) {
                tRegister = tmpRegisters.get(tmpRegisters.size() - 1);
            } else {
                tRegister = assignment[target].getRegister().get();
            }
            String targetName = tRegister.asSize(size);
            replace.put(target, targetName);

            // handle the overwrite register
            if (instr.getOverwriteRegister().isPresent()) {
                int overwrite = instr.getOverwriteRegister().get();
                if (assignment[overwrite].isSpilled()) {
                    int stackSlot = assignment[overwrite].getStackSlot().get();
                    output("mov%c %d(%%rbp), %s # reload for @%s [overwrite]",
                            size.getSuffix(), stackSlot, targetName, overwrite);
                } else {
                    Register ovRegister = assignment[overwrite].getRegister().get();
                    if (ovRegister != tRegister) {
                        String ovName = assignment[overwrite].getRegister().get().asSize(size);
                        output("mov %s, %s # move for @%s [overwrite]",
                                ovName, targetName, overwrite);
                    }
                }
            }

            // output the instruction itself
            output(instr.mapRegisters(replace));
            tracker.leaveInstruction(index);

            // possibly spill the target register
            if (assignment[target].isSpilled()) {
                int stackSlot = assignment[target].getStackSlot().get();
                output("mov%c %s, %d(%%rbp) # spill for @%s",
                        size.getSuffix(), targetName, stackSlot, target);
            } else {
                tracker.assertMapping(target, assignment[target].getRegister().get());
            }
        }
    }

    private int countRequiredTmps(Instruction instr) {
        assert instr.getType() == InstructionType.GENERAL;
        int n = 0;
        for (int vRegister: instr.getInputRegisters()) {
            if (assignment[vRegister].isSpilled()) {
                n++;
            }
        }
        if (instr.getTargetRegister().isPresent() &&
                assignment[instr.getTargetRegister().get()].isSpilled()) {
            n++;
        }
        return n;
    }

    private void output(String instr) {
        result.add(instr);
    }

    private void output(String format, Object... args) {
        result.add(String.format(format, args));
    }

    // debugging
    public void testRun(Logger logger) {
        LifetimeTracker tracker = new LifetimeTracker();
        tracker.printState(logger);
        for (int i = 0; i < ir.size(); i++) {
            tracker.enterInstruction(i);
            tracker.printState(logger);
            logger.debug("%d: %s", i, ir.get(i));
            tracker.leaveInstruction(i);
            tracker.printState(logger);
        }
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
                        assert slotA >= slotB + sizes[j].getBytes() || slotB >= slotA + sizes[i].getBytes();
                    }
                }
            }
        }
    }

    private static Lifetime[] completeLifetimes(int nRegisters, int nInstructions) {
        Lifetime[] lifetimes = new Lifetime[nRegisters];
        for(int i = 0; i < nRegisters; i++) {
            lifetimes[i] = new Lifetime(-1, nInstructions, false);
        }
        return  lifetimes;
    }

    /**
     * Tracks the mapping of hardware registers to virtual registers during the
     * allocation in order to correctly provide free registers when needed
     */
    private class LifetimeTracker {
        /**
         * directly modifying `registers` should be avoided
         */
        @Getter
        private RegisterTracker registers;
        private List<Integer> lifetimeStarts;
        private List<Integer> lifetimeEnds;

        // prevents erroneously requesting temporary registers twice
        private boolean tmpRequested;

        LifetimeTracker() {
            this.registers = new RegisterTracker();
            this.lifetimeStarts = sortByLifetime((l1, l2) -> l1.getBegin() - l2.getBegin());
            this.lifetimeEnds = sortByLifetime((l1, l2) -> {
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
            while (!lifetimeStarts.isEmpty() && lifetimes[peek(lifetimeStarts)].getBegin() < 0) {
                setRegister(pop(lifetimeStarts));
            }
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
            assert lifetimeStarts.isEmpty() || lifetimes[peek(lifetimeStarts)].getBegin() >= index;

            tmpRequested = false;
            while (!lifetimeEnds.isEmpty() && lifetimes[peek(lifetimeEnds)].getEnd() <= index) {
                clearRegister(pop(lifetimeEnds));
            }
        }

        /**
         * sets the state after the instruction is executed
         */
        public void leaveInstruction(int index) {
            assert lifetimeEnds.isEmpty() || lifetimes[peek(lifetimeEnds)].getEnd() > index;

            tmpRequested = false;
            while (!lifetimeEnds.isEmpty() && lifetimes[peek(lifetimeEnds)].getEnd() == index + 1 &&
                    // `isLastInstrIsInput` must be checked here, otherwise the state could be invalid at the end of a loop
                    lifetimes[peek(lifetimeEnds)].isLastInstrIsInput()) {
                clearRegister(pop(lifetimeEnds));
            }
            while (!lifetimeStarts.isEmpty() && lifetimes[peek(lifetimeStarts)].getBegin() <= index) {
                setRegister(pop(lifetimeStarts));
            }
        }

        public void assertMapping(int vRegister, Register r) {
            assert registers.get(r).isPresent();
            assert registers.get(r).get() == vRegister;
        }

        private List<Integer> sortByLifetime(Comparator<Lifetime> compare) {
            List<Integer> result = new ArrayList<>();
            for (int i = 0; i < lifetimes.length; i++) {
                if (!lifetimes[i].isTrivial() && !assignment[i].isSpilled()) {
                    result.add(i);
                }
            }
            // We want to sort in descending order, so we can pop the last elements
            result.sort((j, k) -> compare.compare(lifetimes[k],lifetimes[j]));
            return result;
        }

        private void clearRegister(int vRegister) {
            Register r = assignment[vRegister].getRegister().get();
            registers.clear(r);
        }

        private void setRegister(int vRegister) {
            Register r = assignment[vRegister].getRegister().get();
            registers.set(r, vRegister);
        }

        private int pop(List<Integer> l) {
            return l.remove(l.size()- 1);
        }

        private int peek(List<Integer> l) {
            return l.get(l.size()- 1);
        }

        // debugging
        public void printState(Logger logger) {
            logger.debug("== Register tracking state ==");
            if (!lifetimeStarts.isEmpty()) {
                int vR = peek(lifetimeStarts);
                logger.debug("- starting lifetime for vRegister %d at index %d", vR, lifetimes[vR].getBegin());
            }
            if (!lifetimeEnds.isEmpty()) {
                int vR = peek(lifetimeEnds);
                logger.debug("- ending lifetime for vRegister %d at index %d", vR, lifetimes[vR].getEnd());
            }
            registers.printState(logger);
        }
    }
}
