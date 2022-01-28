package edu.kit.compiler.register_allocation;

import edu.kit.compiler.intermediate_lang.*;
import lombok.Data;
import lombok.Getter;

import java.util.*;

/**
 * Allocate registers with a linear scan algorithm.
 *
 * In addition to a basic linear scan, this implementation analyzes
 * the lifetimes and usages of virtual register to calculate a preference
 * for the assignment of the register (e.g. avoiding caller-saved registers
 * if the lifetime contains a call or preferring %rax for a vRegister that
 * represents the result of a function call).
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
        ScanState state = new ScanState(analysis, assignment, sizes, nArgs);

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
                        if (instr.getOverwriteRegister().isPresent()) {
                            int overwrite = instr.getOverwriteRegister().get();
                            if (analysis.getLifetime(overwrite).isLastInstructionAndInput(i)) {
                                state.clear(overwrite);
                            }
                            // target register must be disjoint from input registers
                            allocateTargetRegister(state, i, instr);
                            int nTemps = countRequiredTmps(assignment, instr);
                            state.assertCapacity(nTemps, Optional.empty());
                            state.leaveInstruction(i);
                        } else {
                            // conservatively, always allocate a tmp for the target
                            int nTemps = countRequiredTmps(assignment, instr) + 1;
                            state.assertCapacity(nTemps, Optional.empty());
                            state.leaveInstruction(i);
                            allocateTargetRegister(state, i, instr);
                        }
                    }
                    case DIV, MOD -> {
                        int dividend = instr.inputRegister(0);
                        int divisor = instr.inputRegister(1);
                        boolean freeRAX = !analysis.getLifetime(dividend).isLastInstructionAndInput(i) ||
                                assignment[dividend].isSpilled() || assignment[dividend].getRegister().get() != Register.RAX;
                        if (freeRAX) {
                            state.assertFree(Register.RAX);
                        }
                        state.assertFree(Register.RDX);
                        if (assignment[divisor].isSpilled()) {
                            state.assertCapacity(freeRAX ? 3 : 2, Optional.of(Register.RAX));
                        }
                        state.leaveInstruction(i);
                        allocateTargetRegister(state, i, instr);
                    }
                    case MOV_S, MOV_U -> {
                        int source = instr.inputRegister(0);
                        int target = instr.getTargetRegister().get();
                        boolean isSignedUpcast = sizes[target].getBytes() > sizes[source].getBytes()
                                && instr.getType() == InstructionType.MOV_S;
                        if ((!assignment[source].isInRegister() || isSignedUpcast) &&
                                !assignment[target].isInRegister()) {
                            state.assertCapacity(1, Optional.empty());
                        }
                        state.leaveInstruction(i);
                        allocateTargetRegister(state, i, instr);
                    }
                    case CALL, RET -> {
                        if (instr.getInputRegisters().size() > CCONV.numArgRegisters()) {
                            state.assertCapacity(1, Optional.empty());
                        }
                        state.leaveInstruction(i);
                        allocateTargetRegister(state, i, instr);
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

    private void allocateTargetRegister(ScanState state, int index, Instruction instr) {
        if (instr.getTargetRegister().isPresent()) {
            LifetimeAnalysis analysis = state.getAnalysis();
            int target = instr.getTargetRegister().get();
            if (analysis.getLifetime(target).getBegin() == index) {
                // allocate the new register
                RegisterPreference preference = calculatePreference(target, state.getAssignment(), analysis);
                state.allocateRegister(target, preference);
            }
        }
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
                preference = withPrefIfNotAvoided(preference, source);
            } else if (first.getType() == InstructionType.GENERAL) {
                // check for overwrite
                Optional<Integer> overwrite = first.getOverwriteRegister();
                if (overwrite.isPresent()) {
                    Optional<Register> ovRegister = assignment[overwrite.get()].getRegister();
                    preference = withPrefIfNotAvoided(preference, ovRegister);
                }
            }
        } else {
            // argument
            assert analysis.getLifetime(vRegister).getBegin() < 0;

            Optional<Register> argRegister = CCONV.getArgRegister(vRegister);
            preference = withPrefIfNotAvoided(preference, argRegister);
        }
        if (analysis.getLastInstruction(vRegister).isPresent() &&
                analysis.getLifetime(vRegister).isLastInstrIsInput()) {
            Instruction last = analysis.getLastInstruction(vRegister).get();
            if (last.getType() == InstructionType.CALL) {
                // argument of called function
                int argIndex = last.getInputRegisters().indexOf(vRegister);
                assert argIndex >= 0;

                Optional<Register> argRegister = CCONV.getArgRegister(argIndex);
                preference = withPrefIfNotAvoided(preference, argRegister);
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
                preference = withPrefIfNotAvoided(preference, target);
            }
        }

        return preference;
    }

    private static RegisterPreference withPrefIfNotAvoided(RegisterPreference preference, Optional<Register> r) {
        if (r.isPresent()) {
            return withPrefIfNotAvoided(preference, r.get());
        } else {
            return preference;
        }
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

/**
 * Tracks the necessary state for the linear scan. Specifically, this includes
 * register assignments, lifetimes and the state of the hardware registers.
 *
 * Also handles register assignments and spilling.
 */
class ScanState {
    @Getter
    private LifetimeAnalysis analysis;
    @Getter
    private RegisterAssignment[] assignment;
    private RegisterSize[] sizes;
    private RegisterTracker registers;
    private List<Integer> lifetimeEnds;
    private StackSlots stackSlots;
    private int nArgs;

    public ScanState(LifetimeAnalysis analysis, RegisterAssignment[] assignment, RegisterSize[] sizes, int nArgs) {
        assert assignment.length == sizes.length;
        this.analysis = analysis;
        this.assignment = assignment;
        this.sizes = sizes;
        this.registers = new RegisterTracker();
        this.stackSlots = new StackSlots(assignment.length);
        this.nArgs = nArgs;

        lifetimeEnds = new ArrayList<>();
        for (int i = 0; i < assignment.length; i++) {
            if (analysis.isAlive(i)) {
                lifetimeEnds.add(i);
            }
        }

        lifetimeEnds.sort((j, k) -> {
            if (j == k) {
                return 0;
            }
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
            } else if (l1.isLastInstrIsInput() && l2.isLastInstrIsInput()) {
                // sort lifetimes that end with an overwrite to the front
                var overwrite = analysis.getLastInstruction(j).get().getOverwriteRegister();
                if (overwrite.isPresent() && overwrite.get().equals(k)) {
                    return -1;
                } else if (overwrite.isPresent() && overwrite.get().equals(j)) {
                    return 1;
                }
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
        assert !assignment[vRegister].isAssigned();

        Optional<Register> allocated = tryFindRegister(vRegister, preference);
        if (allocated.isPresent()) {
            registers.set(allocated.get(), vRegister);
            assignment[vRegister] = new RegisterAssignment(allocated.get());
        }
        return allocated;
    }

    public void assertCapacity(int capacity, Optional<Register> excluded) {
        while (registers.numFree() < capacity) {
            spill(selectSpillRegister(registers.getCurrentVRegisters(excluded)));
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
        int vRegister = lifetimeEnds.remove(lifetimeEnds.size() - 1);
        stackSlots.clearSlot(vRegister, sizes[vRegister]);
        Optional<Register> r = assignment[vRegister].getRegister();
        if (r.isPresent()) {
            registers.clear(r.get());
        }
    }

    public void clear(int vRegister) {
        assert lifetimeEnds.get(lifetimeEnds.size() - 1) == vRegister;
        clearNext();
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
        var preference = calculatePreferredStackSlots(vRegister);
        stackSlots.addSlot(vRegister, sizes[vRegister], preference);
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

    /**
     * Tries to merge the slot with a previous lifetime or function argument, if possible.
     */
    private List<SlotAssignment> calculatePreferredStackSlots(int vRegister) {
        List<SlotAssignment> preference = new ArrayList<>();

        // is there a register that would be specifically good?
        if (analysis.getFirstInstruction(vRegister).isPresent()) {
            Instruction first = analysis.getFirstInstruction(vRegister).get();
            assert first.getTargetRegister().get() == vRegister;

            if (first.isMov()) {
                stackSlots.getSlot(first.inputRegister(0)).ifPresent(preference::add);
            } else if (first.getType() == InstructionType.GENERAL) {
                // check for overwrite
                Optional<Integer> overwrite = first.getOverwriteRegister();
                if (overwrite.isPresent()) {
                    stackSlots.getSlot(overwrite.get()).ifPresent(preference::add);
                }
            }
        } else {
            // argument
            assert analysis.getLifetime(vRegister).getBegin() < 0;
            preference.add(new SlotAssignment(ApplyAssignment.argOffsetOnStack(nArgs, vRegister), true));
        }
        if (analysis.getLastInstruction(vRegister).isPresent() &&
                analysis.getLifetime(vRegister).isLastInstrIsInput()) {
            Instruction last = analysis.getLastInstruction(vRegister).get();
            if (last.isMov()) {
                stackSlots.getSlot(last.getTargetRegister().get()).ifPresent(preference::add);
            }
        }
        return preference;
    }

    @Data
    private static class SlotAssignment {
        private final int indexOrSlot;
        private final boolean specialSlot;
    }

    private static class StackSlots {
        private EnumMap<RegisterSize, List<Boolean>> slotsForSize;
        private SlotAssignment[] slotAssignments;

        public StackSlots(int nRegisters) {
            slotsForSize = new EnumMap<>(RegisterSize.class);
            slotsForSize.put(RegisterSize.BYTE, new ArrayList<>());
            slotsForSize.put(RegisterSize.WORD, new ArrayList<>());
            slotsForSize.put(RegisterSize.DOUBLE, new ArrayList<>());
            slotsForSize.put(RegisterSize.QUAD, new ArrayList<>());
            slotAssignments = new SlotAssignment[nRegisters];
        }

        public void addSlot(int vRegister, RegisterSize size, List<SlotAssignment> preference) {
            List<Boolean> slots = slotsForSize.get(size);
            for (SlotAssignment ass: preference) {
                if (ass.isSpecialSlot()) {
                    slotAssignments[vRegister] = ass;
                    return;
                } else if (!slots.get(ass.getIndexOrSlot())) {
                    slotAssignments[vRegister] = ass;
                    slots.set(ass.getIndexOrSlot(), true);
                    return;
                }
            }

            // search for slot that can be reused
            for (int i = 0; i < slots.size(); i++) {
                if (!slots.get(i)) {
                    slotAssignments[vRegister] = new SlotAssignment(i, false);
                    slots.set(i, true);
                    return;
                }
            }
            slotAssignments[vRegister] = new SlotAssignment(slots.size(), false);
            slots.add(true);
        }

        public Optional<SlotAssignment> getSlot(int vRegister) {
            return Optional.ofNullable(slotAssignments[vRegister]);
        }

        public void clearSlot(int vRegister, RegisterSize size) {
            if (slotAssignments[vRegister] != null && !slotAssignments[vRegister].isSpecialSlot()) {
                List<Boolean> slots = slotsForSize.get(size);
                slots.set(slotAssignments[vRegister].getIndexOrSlot(), false);
            }
        }

        public int calculateSlot(int vRegister, RegisterSize size) {
            SlotAssignment slot = slotAssignments[vRegister];
            if (slot.isSpecialSlot()) {
                return slot.getIndexOrSlot();
            }

            int base = 0;
            if (size.getBytes() < 8) {
                base -= slotsForSize.get(RegisterSize.QUAD).size() * 8;
            }
            if (size.getBytes() < 4) {
                base -= slotsForSize.get(RegisterSize.DOUBLE).size() * 4;
            }
            if (size.getBytes() < 2) {
                base -= slotsForSize.get(RegisterSize.WORD).size() * 2;
            }
            return base - (slot.getIndexOrSlot() + 1) * size.getBytes();
        }
    }
}
