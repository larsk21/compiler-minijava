package edu.kit.compiler.register_allocation;

import edu.kit.compiler.intermediate_lang.Block;
import edu.kit.compiler.intermediate_lang.Instruction;
import lombok.Data;
import lombok.Getter;

import java.util.*;

/**
 * Analyses the lifetimes and related information for all vRegisters.
 */
public class LifetimeAnalysis {
    @Getter
    private Lifetime[] lifetimes;
    private Optional<Instruction>[] firstInstruction;
    private Optional<Instruction>[] lastInstruction;
    private int[] loopNestingDepth;
    private boolean[] isDividend;
    private List<Integer> callsPrefixSum;
    private List<Integer> divsPrefixSum;
    @Getter
    private int numInstructions;

    private LifetimeAnalysis(Lifetime[] lifetimes, Optional<Instruction>[] firstInstruction,
                             Optional<Instruction>[] lastInstruction, int[] loopNestingDepth, boolean[] isDividend,
                             List<Integer> callsPrefixSum, List<Integer> divsPrefixSum, int numInstructions) {
        this.lifetimes = lifetimes;
        this.firstInstruction = firstInstruction;
        this.lastInstruction = lastInstruction;
        this.loopNestingDepth = loopNestingDepth;
        this.isDividend = isDividend;
        this.callsPrefixSum = callsPrefixSum;
        this.divsPrefixSum = divsPrefixSum;
        this.numInstructions = numInstructions;
    }

    /**
     * Whether the register is used at all.
     */
    public boolean isAlive(int vRegister) {
        return !lifetimes[vRegister].isTrivial();
    }

    /**
     * The optional is not present if the vRegister is an argument.
     */
    public Optional<Instruction> getFirstInstruction(int vRegister) {
        assert isAlive(vRegister);
        return firstInstruction[vRegister];
    }

    /**
     * The optional is not present if the vRegister was loop-extended.
     */
    public Optional<Instruction> getLastInstruction(int vRegister) {
        assert isAlive(vRegister);
        return lastInstruction[vRegister];
    }

    public Lifetime getLifetime(int vRegister) {
        return lifetimes[vRegister];
    }

    /**
     * The maximum nesting-depth of a loop that contains the vRegister.
     */
    public int getLoopDepth(int vRegister) {
        return loopNestingDepth[vRegister];
    }

    /**
     * Whether the vRegister appears as dividend in a `div` or `mod` instruction.
     */
    public boolean isDividend(int vRegister) {
        assert isAlive(vRegister);
        return isDividend[vRegister];
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(String.format("%-10s %-15s %-30s %-30s %-11s %-10s %-10s\n",
                "vRegister", "Lifetime", "First Instr.", "Last Instr.", "Loop Depth", "Num calls", "Num divs"));
        for (int vRegister = 0; vRegister < lifetimes.length; vRegister++) {
            if (!lifetimes[vRegister].isTrivial()) {
                String firstInstr = getFirstInstruction(vRegister).map(Instruction::getText).orElse("-");
                String lastInstr = getLastInstruction(vRegister).map(Instruction::getText).orElse("-");
                builder.append(String.format("%-10d %-15s %-30s %-30s %-11d %-10d %-10d\n",
                        vRegister, lifetimes[vRegister], firstInstr, lastInstr, loopNestingDepth[vRegister],
                        numInterferingCalls(vRegister, true), numInterferingDivs(vRegister, true)));
            }
        }
        return builder.toString();
    }

    /**
     * The number of calls within the lifetime of an vRegister.
     * The first instruction, i.e. definition point of the register, is not included.
     */
    public int numInterferingCalls(int vRegister, boolean includeLastIfInput) {
        return numInterferencesFromPrefixSum(callsPrefixSum, vRegister, includeLastIfInput);
    }

    /**
     * The number of divs within the lifetime of an vRegister.
     * The first instruction, i.e. definition point of the register, is not included.
     */
    public int numInterferingDivs(int vRegister, boolean includeLastIfInput) {
        return numInterferencesFromPrefixSum(divsPrefixSum, vRegister, includeLastIfInput);
    }

    public static LifetimeAnalysis run(List<Block> ir, int numVRegisters, int nArgs) {
        Lifetime[] lifetimes = new Lifetime[numVRegisters];
        @SuppressWarnings("unchecked")
        Optional<Instruction>[] firstInstruction = new Optional[numVRegisters];
        @SuppressWarnings("unchecked")
        Optional<Instruction>[] lastInstruction = new Optional[numVRegisters];
        int[] loopNestingDepth = new int[numVRegisters];
        int[] definitionNestingDepth = new int[numVRegisters];
        boolean[] isDividend = new boolean[numVRegisters];
        List<Integer> callsPrefixSum = new ArrayList<>();
        callsPrefixSum.add(0);
        List<Integer> divsPrefixSum = new ArrayList<>();
        divsPrefixSum.add(0);
        List<StackEntry> stack = new ArrayList<>();

        for (int i = 0; i < numVRegisters; i++) {
            lifetimes[i] = new Lifetime();
            firstInstruction[i] = Optional.empty();
            lastInstruction[i] = Optional.empty();
        }

        int index = 0;
        for (Block b: ir) {
            for (int counter = 0; counter < b.getNumBackReferences(); counter++) {
                stack.add(new StackEntry(b.getBlockId()));
            }

            for (Instruction instr: b.getInstructions()) {
                switch (instr.getType()) {
                    case DIV, MOD -> {
                        appendToSum(divsPrefixSum, 1);
                        appendToSum(callsPrefixSum, 0);
                        isDividend[instr.inputRegister(0)] = true;
                        // the div begins with the dividend (from there, %rax should be avoided)
                        int dividendBegin = lifetimes[instr.inputRegister(0)].getBegin();
                        for (int i = dividendBegin + 1; i < divsPrefixSum.size(); i++) {
                            divsPrefixSum.set(i, divsPrefixSum.get(i) + 1);
                        }
                    }
                    case CALL -> {
                        appendToSum(divsPrefixSum, 0);
                        appendToSum(callsPrefixSum, 1);
                    }
                    default -> {
                        appendToSum(divsPrefixSum, 0);
                        appendToSum(callsPrefixSum, 0);
                    }
                }

                List<Integer> inputRegs = new ArrayList<>(instr.getInputRegisters());
                if (instr.getOverwriteRegister().isPresent()) {
                    inputRegs.add(instr.getOverwriteRegister().get());
                }
                for (int vRegister: inputRegs) {
                    if (vRegister < nArgs) {
                        // args are already alive at the start of the function (i.e. index -1)
                        lifetimes[vRegister] = new Lifetime(-1, index + 1, true);
                        definitionNestingDepth[vRegister] = 0;
                    } else {
                        assert !lifetimes[vRegister].isTrivial();
                        lifetimes[vRegister].extend(index, true);
                    }
                    lastInstruction[vRegister] = Optional.of(instr);
                    loopNestingDepth[vRegister] = Math.max(loopNestingDepth[vRegister], stack.size());

                    if (definitionNestingDepth[vRegister] < stack.size()) {
                        // insert vRegister to stack to be processed later when leaving the loop
                        stack.get(definitionNestingDepth[vRegister]).getVRegisters().add(vRegister);
                    }
                }

                if (instr.getTargetRegister().isPresent()) {
                    int target = instr.getTargetRegister().get();
                    if (lifetimes[target].isTrivial()) {
                        lifetimes[target] = new Lifetime(index, index + 1);
                        definitionNestingDepth[target] = stack.size();
                        firstInstruction[target] = Optional.of(instr);
                    }
                    lastInstruction[target] = Optional.of(instr);
                    loopNestingDepth[target] = Math.max(loopNestingDepth[target], stack.size());

                    if (definitionNestingDepth[target] < stack.size()) {
                        // insert vRegister to stack to be processed later when leaving the loop
                        stack.get(definitionNestingDepth[target]).getVRegisters().add(target);
                    }
                }

                Optional<Integer> backref = instr.getJumpTarget();
                if (backref.isPresent() && !stack.isEmpty() &&
                        backref.get() == stack.get(stack.size() - 1).getBlockId()) {
                    // loop-extension of register lifetimes
                    StackEntry entry = stack.remove(stack.size() - 1);
                    for (int vRegister: entry.getVRegisters()) {
                        lifetimes[vRegister].extend(index, false);
                        lastInstruction[vRegister] = Optional.empty();
                    }
                }

                index++;
            }
        }
        assert stack.isEmpty();
        return new LifetimeAnalysis(lifetimes, firstInstruction, lastInstruction,
                loopNestingDepth, isDividend, callsPrefixSum, divsPrefixSum, index);
    }

    private int numInterferencesFromPrefixSum(List<Integer> prefixSum, int vRegister,
                                              boolean includeLastIfInput) {
        assert isAlive(vRegister);

        Lifetime lt = lifetimes[vRegister];
        int begin = lt.getBegin() + 1;
        int end = lt.getEnd();
        if (!includeLastIfInput && lt.isLastInstrIsInput()) {
            end = Math.max(lt.getBegin(), end - 1);
        }
        return prefixSum.get(end) - prefixSum.get(begin);
    }

    private static void appendToSum(List<Integer> prefixSum, int val) {
        if (prefixSum.isEmpty()) {
            prefixSum.add(val);
            return;
        }

        int last = prefixSum.get(prefixSum.size() - 1);
        prefixSum.add(last + val);
    }

    @Data
    private static class StackEntry {
        private int blockId;
        private List<Integer> vRegisters;

        public StackEntry(int blockId) {
            this.blockId = blockId;
            this.vRegisters = new ArrayList<>();
        }
    }
}
