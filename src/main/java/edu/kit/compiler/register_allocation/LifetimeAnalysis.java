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
    private Instruction[] firstInstruction;
    private Instruction[] lastInstruction;
    private int[] loopNestingDepth;
    private List<Integer> callsPrefixSum;
    private List<Integer> divsPrefixSum;

    private LifetimeAnalysis(Lifetime[] lifetimes, Instruction[] firstInstruction, Instruction[] lastInstruction,
                             int[] loopNestingDepth, List<Integer> callsPrefixSum, List<Integer> divsPrefixSum) {
        this.lifetimes = lifetimes;
        this.firstInstruction = firstInstruction;
        this.lastInstruction = lastInstruction;
        this.loopNestingDepth = loopNestingDepth;
        this.callsPrefixSum = callsPrefixSum;
        this.divsPrefixSum = divsPrefixSum;
    }

    public boolean isAlive(int vRegister) {
        return !lifetimes[vRegister].isTrivial();
    }

    public Instruction getFirstInstruction(int vRegister) {
        return firstInstruction[vRegister];
    }

    public Instruction getLastInstruction(int vRegister) {
        return lastInstruction[vRegister];
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(String.format("%-10s %-15s %-30s %-30s %-11s %-10s %-10s\n",
                "vRegister", "Lifetime", "First Instr.", "Last Instr.", "Loop Depth", "Num calls", "Num divs"));
        for (int vRegister = 0; vRegister < lifetimes.length; vRegister++) {
            builder.append(String.format("%-10d %-15s %-30s %-30s %-11d %-10d %-10d\n",
                    vRegister, lifetimes[vRegister], firstInstruction[vRegister].getText(),
                    lastInstruction[vRegister].getText(), loopNestingDepth[vRegister],
                    0, 0));

        }
        return builder.toString();
    }

    public static LifetimeAnalysis run(List<Block> ir, int numVRegisters, int nArgs) {
        Lifetime[] lifetimes = new Lifetime[numVRegisters];
        Instruction[] firstInstruction = new Instruction[numVRegisters];
        Instruction[] lastInstruction = new Instruction[numVRegisters];
        int[] loopNestingDepth = new int[numVRegisters];
        int[] definitionNestingDepth = new int[numVRegisters];
        List<Integer> callsPrefixSum = new ArrayList<>();
        List<Integer> divsPrefixSum = new ArrayList<>();
        List<StackEntry> stack = new ArrayList<>();

        for (int i = 0; i < numVRegisters; i++) {
            lifetimes[i] = new Lifetime();
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
                        // args are already alife at the start of the function (i.e. index -1)
                        lifetimes[vRegister] = new Lifetime(-1, index + 1, true);
                        definitionNestingDepth[vRegister] = 0;
                    } else {
                        assert !lifetimes[vRegister].isTrivial();
                        lifetimes[vRegister].extend(index, true);
                    }
                    lastInstruction[vRegister] = instr;
                    loopNestingDepth[vRegister] = Math.max(loopNestingDepth[vRegister], stack.size());

                    if (definitionNestingDepth[vRegister] < stack.size()) {
                        // insert vRegister to stack to be processed late when leaving the loop
                        stack.get(definitionNestingDepth[vRegister]).getVRegisters().add(vRegister);
                    } else {
                        assert definitionNestingDepth[vRegister] == stack.size();
                    }
                }

                if (instr.getTargetRegister().isPresent()) {
                    int target = instr.getTargetRegister().get();
                    if (lifetimes[target].isTrivial()) {
                        lifetimes[target] = new Lifetime(index, index + 1);
                        definitionNestingDepth[target] = stack.size();
                        firstInstruction[target] = instr;
                    }
                    lastInstruction[target] = instr;
                    loopNestingDepth[target] = Math.max(loopNestingDepth[target], stack.size());

                    if (definitionNestingDepth[target] < stack.size()) {
                        // insert vRegister to stack to be processed late when leaving the loop
                        stack.get(definitionNestingDepth[target]).getVRegisters().add(target);
                    } else {
                        assert definitionNestingDepth[target] == stack.size();
                    }
                }

                Optional<Integer> backref = instr.getJumpTarget();
                if (backref.isPresent() && backref.get() == stack.get(stack.size() - 1).getBlockId()) {
                    // loop-extension of register lifetimes
                    StackEntry entry = stack.remove(stack.size() - 1);
                    for (int vRegister: entry.getVRegisters()) {
                        lifetimes[vRegister].extend(index, false);
                    }
                }

                index++;
            }
        }
        assert stack.isEmpty();
        return new LifetimeAnalysis(lifetimes, firstInstruction, lastInstruction,
                loopNestingDepth, callsPrefixSum, divsPrefixSum);
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
