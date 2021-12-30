package edu.kit.compiler.register_allocation;

import edu.kit.compiler.intermediate_lang.*;

import java.util.*;

/**
 * Allocate registers by assigning a stack slot to each vRegister.
 */
public class LinearScan implements RegisterAllocator {
    private static final CallingConvention CCONV = CallingConvention.X86_64;

    @Override
    public List<String> performAllocation(int nArgs, List<Block> input, RegisterSize[] sizes) {
        RegisterAssignment[] assignment = new RegisterAssignment[sizes.length];
        for (int i = 0; i < assignment.length; i++) {
            assignment[i] = new RegisterAssignment();
        }

        LifetimeAnalysis analysis = LifetimeAnalysis.run(input, sizes.length, nArgs);
        ScanState state = new ScanState(analysis, assignment, sizes);

        for (int arg = 0; arg < nArgs; arg++) {
            if (analysis.isAlive(arg)) {
                RegisterPreference preference = calculatePreference(arg, assignment, analysis);
                state.allocateRegister(arg, preference);
            }
        }

        int i = 0;
        for (Block b: input) {
            for (Instruction instr: b.getInstructions()) {
                state.enterInstruction(i);
                switch (instr.getType()) {
                    case GENERAL -> {
                        int nTemps = countRequiredTmps(assignment, instr);
                        state.assertCapacity(nTemps);
                    }
                    case DIV, MOD -> {
                        int dividend = instr.inputRegister(0);
                        int quotient = instr.inputRegister(1);
                        if (!analysis.getLifetime(dividend).isLastInstructionAndInput(i) ||
                                assignment[dividend].isSpilled() || assignment[dividend].getRegister().get() != Register.RAX) {
                            state.assertFree(Register.RAX);
                        }
                        state.assertFree(Register.RDX);
                        if (assignment[quotient].isSpilled()) {
                            state.assertCapacity(1);
                        }
                    }
                    case MOV_S, MOV_U -> {
                        if (assignment[instr.inputRegister(0)].isSpilled() &&
                                assignment[instr.getTargetRegister().get()].isSpilled()) {
                            state.assertCapacity(1);
                        }
                    }
                    case CALL, RET -> { }
                }

                state.leaveInstruction(i);
                if (instr.getTargetRegister().isPresent()) {
                    int target = instr.getTargetRegister().get();
                    if (analysis.getLifetime(target).getBegin() == i) {
                        // allocate the new register
                        RegisterPreference preference = calculatePreference(target, assignment, analysis);
                        state.allocateRegister(target, preference);
                    }
                }
                i++;
            }
        }
        state.assertFinallyEmpty(i);
        state.calculateStackSlots();

        return ApplyAssignment.createFunctionBody(
                assignment, sizes, analysis.getLifetimes(), input, analysis.getNumInstructions(), nArgs);
    }

    private static RegisterPreference calculatePreference(int vRegister, RegisterAssignment[] assignment,
                                                          LifetimeAnalysis analysis) {
        // basic preference
        boolean avoidCallerSaved = analysis.numInterferingCalls(vRegister, false) > 0;
        boolean avoidDiv = analysis.numInterferingDivs(vRegister, false) > 0 ||
                // the quotient needs to avoid RAX/RDX, too
                (analysis.numInterferingDivs(vRegister, true) > 0 &&
                        analysis.getLastInstruction(vRegister).get().isDivOrMod() && !analysis.isDividend(vRegister));
        RegisterPreference preference = RegisterPreference.fromFlags(avoidCallerSaved, avoidDiv);

        // is there a register that would be specifically good?
        if (analysis.getFirstInstruction(vRegister).isPresent()) {
            Instruction first = analysis.getFirstInstruction(vRegister).get();
            assert first.getTargetRegister().get() == vRegister;

            if (first.isDivOrMod()) {
                // div result
                preference = withPrefIfNotAvoided(preference,
                        switch (first.getType()) {
                            case DIV -> Register.RAX;
                            case MOD -> Register.RDX;
                            default -> throw new IllegalStateException();
                        }
                );
            } else if (first.getType() == InstructionType.CALL) {
                // call result
                preference = withPrefIfNotAvoided(preference, CCONV.getReturnRegister());
            } else if (first.isMov()) {
                Optional<Register> source = assignment[first.inputRegister(0)].getRegister();
                if (source.isPresent()) {
                    preference = withPrefIfNotAvoided(preference, source.get());
                }
            } else if (first.getType() == InstructionType.GENERAL) {
                // check for overwrite
                Optional<Integer> overwrite = first.getOverwriteRegister();
                if (overwrite.isPresent()) {
                    Optional<Register> ovRegister = assignment[overwrite.get()].getRegister();
                    if (ovRegister.isPresent()) {
                        preference = withPrefIfNotAvoided(preference, ovRegister.get());
                    }
                }
            }
        } else {
            // argument
            assert analysis.getLifetime(vRegister).getBegin() < 0;

            Optional<Register> argRegister = CCONV.getArgRegister(vRegister);
            if (argRegister.isPresent()) {
                preference = withPrefIfNotAvoided(preference, argRegister.get());
            }
        }
        if (analysis.getLastInstruction(vRegister).isPresent() &&
                analysis.getLifetime(vRegister).isLastInstrIsInput()) {
            Instruction last = analysis.getLastInstruction(vRegister).get();
            if (last.getType() == InstructionType.CALL) {
                // argument of called function
                int argIndex = last.getInputRegisters().indexOf(vRegister);
                assert argIndex >= 0;

                Optional<Register> argRegister = CCONV.getArgRegister(argIndex);
                if (argRegister.isPresent()) {
                    preference = withPrefIfNotAvoided(preference, argRegister.get());
                }
            } else if (last.getType() == InstructionType.RET) {
                // return value
                assert last.inputRegister(0) == vRegister;
                preference = withPrefIfNotAvoided(preference, CCONV.getReturnRegister());
            } else if (last.isDivOrMod()) {
                if (analysis.isDividend(vRegister)) {
                    preference = withPrefIfNotAvoided(preference, Register.RAX);
                }
            } else if (last.isMov()) {
                Optional<Register> target = assignment[last.getTargetRegister().get()].getRegister();
                if (target.isPresent()) {
                    preference = withPrefIfNotAvoided(preference, target.get());
                }
            }
        }

        return preference;
    }

    private static RegisterPreference withPrefIfNotAvoided(RegisterPreference preference, Register r) {
        if (!preference.isAvoided(r)) {
            return preference.withPreferredRegister(r);
        } else {
            return preference;
        }
    }

    private static int countRequiredTmps(RegisterAssignment[] assignment, Instruction instr) {
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
}

class ScanState {
    private LifetimeAnalysis analysis;
    private RegisterAssignment[] assignment;
    private RegisterSize[] sizes;
    private RegisterTracker registers;
    private List<Integer> lifetimeEnds;
    private StackSlots stackSlots;

    public ScanState(LifetimeAnalysis analysis, RegisterAssignment[] assignment, RegisterSize[] sizes) {
        assert assignment.length == sizes.length;
        this.analysis = analysis;
        this.assignment = assignment;
        this.sizes = sizes;
        this.registers = new RegisterTracker();
        this.stackSlots = new StackSlots(assignment.length);

        lifetimeEnds = new ArrayList<>();
        for (int i = 0; i < assignment.length; i++) {
            if (analysis.isAlive(i)) {
                lifetimeEnds.add(i);
            }
        }

        lifetimeEnds.sort((j, k) -> {
            // We want to sort in descending order, so we can pop the last elements
            Lifetime l1 = analysis.getLifetime(k);
            Lifetime l2 = analysis.getLifetime(j);
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
    }

    /**
     * sets the state before the instruction is executed
     */
    public void enterInstruction(int index) {
        while (!lifetimeEnds.isEmpty() && analysis.getLifetime(peek()).getEnd() <= index) {
            clearNext();
        }
    }

    /**
     * sets the state after the instruction is executed
     */
    public void leaveInstruction(int index) {
        assert lifetimeEnds.isEmpty() || analysis.getLifetime(peek()).getEnd() > index;

        while (!lifetimeEnds.isEmpty() && analysis.getLifetime(peek()).getEnd() == index + 1 &&
                // `isLastInstrIsInput` must be checked here, otherwise the state could be invalid at the end of a loop
                analysis.getLifetime(peek()).isLastInstrIsInput()) {
            clearNext();
        }
    }

    public void assertFinallyEmpty(int numInstrs) {
        enterInstruction(numInstrs);
        assert registers.isEmpty();
    }

    public Optional<Register> allocateRegister(int vRegister, RegisterPreference preference) {
        Optional<Register> allocated = tryFindRegister(vRegister, preference);
        if (allocated.isPresent()) {
            registers.set(allocated.get(), vRegister);
            assignment[vRegister] = new RegisterAssignment(allocated.get());
        }
        return allocated;
    }

    public void assertCapacity(int capacity) {
        while (registers.numFree() < capacity) {
            spill(selectSpillRegister(registers.getCurrentVRegisters()));
        }
    }

    public void assertFree(Register r) {
        Optional<Integer> vRegister = registers.get(r);
        if (vRegister.isPresent()) {
            spill(vRegister.get());
        }
    }

    public void calculateStackSlots() {
        for (int vRegister = 0; vRegister < assignment.length; vRegister++) {
            if (assignment[vRegister].isSpilled()) {
                assignment[vRegister] = new RegisterAssignment(
                        stackSlots.calculateSlot(vRegister, sizes[vRegister]));
            }
        }
    }

    private int peek() {
        return lifetimeEnds.get(lifetimeEnds.size()- 1);
    }

    private void clearNext() {
        int vRegister = lifetimeEnds.remove(lifetimeEnds.size()- 1);
        Optional<Register> r = assignment[vRegister].getRegister();
        if (r.isPresent()) {
            registers.clear(r.get());
        }
    }

    private int selectSpillRegister(Iterable<Integer> vRegisters) {
        int best = -1;
        int bestLoopDepth = Integer.MAX_VALUE;
        int bestLifetimeEnd = -1;
        for (int r: vRegisters) {
            int loopDepth = analysis.getLoopDepth(r);
            int lifetimeEnd = analysis.getLifetime(r).getEnd();
            if (loopDepth < bestLoopDepth ||
                    (loopDepth == bestLoopDepth && lifetimeEnd > bestLifetimeEnd)) {
                best = r;
                bestLoopDepth = loopDepth;
                bestLifetimeEnd = lifetimeEnd;
            }
        }
        return best;
    }

    private void spill(int vRegister) {
        assignment[vRegister] = new RegisterAssignment(0);
        stackSlots.addSlot(vRegister, sizes[vRegister]);
        registers.clear(vRegister);
    }

    private Optional<Register> tryFindRegister(int vRegister, RegisterPreference preference) {
        Optional<Register> allocated = registers.tryGetFreeRegister(preference);
        if (allocated.isPresent()) {
            return allocated;
        } else {
            // which register should be spilled?
            List<Integer> currentRegisters = registers.getCurrentVRegisters();
            currentRegisters.add(vRegister);
            int spillTarget = selectSpillRegister(currentRegisters);
            spill(spillTarget);
            if (spillTarget == vRegister) {
                return Optional.empty();
            } else {
                allocated = registers.tryGetFreeRegister(preference);
                assert allocated.isPresent();
                return allocated;
            }
        }
    }

    private static class StackSlots {
        private EnumMap<RegisterSize, Integer> numSlotsForSize;
        private int[] slotIndex;

        public StackSlots(int nRegisters) {
            numSlotsForSize = new EnumMap<>(RegisterSize.class);
            numSlotsForSize.put(RegisterSize.BYTE, 0);
            numSlotsForSize.put(RegisterSize.WORD, 0);
            numSlotsForSize.put(RegisterSize.DOUBLE, 0);
            numSlotsForSize.put(RegisterSize.QUAD, 0);
            slotIndex = new int[nRegisters];
        }

        public void addSlot(int vRegister, RegisterSize size) {
            int index = numSlotsForSize.get(size);
            numSlotsForSize.put(size, index + 1);
            slotIndex[vRegister] = index;
        }

        public int calculateSlot(int vRegister, RegisterSize size) {
            int base = 0;
            if (size.getBytes() < 8) {
                base -= numSlotsForSize.get(RegisterSize.QUAD) * 8;
            }
            if (size.getBytes() < 4) {
                base -= numSlotsForSize.get(RegisterSize.DOUBLE) * 4;
            }
            if (size.getBytes() < 2) {
                base -= numSlotsForSize.get(RegisterSize.WORD) * 2;
            }
            return base - (slotIndex[vRegister] + 1) * size.getBytes();
        }
    }
}
