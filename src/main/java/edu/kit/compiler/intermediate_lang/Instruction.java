package edu.kit.compiler.intermediate_lang;

import lombok.Getter;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Represents a command in semi-textual form. Virtual registers are prefixed with '@'.
 * Usage examples:
 *
 * @3 = @1 + @2     =>   Instruction.newOp("addl @1, @3", new int[] { 1 }, Optional.of(2), 3)
 *
 * @1 < @2          =>   Instruction.newInput("cmpl @1, @2", new int[] { 1, 2 })
 *                       Instruction.newJmp("jl .L<blockId>", <blockId>)
 *
 * *(@1 + 4) = @2   =>   Instruction.newInput("movl @2, 4(@1)", new int[] { 2, 1 })
 *
 * @1 = 0           =>   Instruction.newOp("xorl @1, @1", new int[] {}, Optional.empty(), 1)
 */
public class Instruction {
    @Getter
    private InstructionType type;

    /**
     * Textual representation of the instruction, with placeholders (@0, @1, ...)
     * for virtual registers
     */
    @Getter
    private String text;

    // ==== all virtual registers involved in the instruction ====
    // ! inputRegisters, targetRegister and overwriteRegister must be disjoint !

    @Getter
    private List<Integer> inputRegisters;

    /**
     * Virtual input register for commands where one operand is
     * input and output register at once (i.e. add, sub, ...).
     * This is not present in the textual representation.
     */
    @Getter
    private Optional<Integer> overwriteRegister;

    /**
     * Register where the output of the instruction is stored
     */
    @Getter
    private Optional<Integer> targetRegister;

    // ==== meta information that is not part of the command per se ====

    /**
     * List of instructions (within the same block) that must be
     * executed before this instruction
     *
     * TODO: do we even want to use this?
     */
    @Getter
    private List<Integer> dataDependencies;

    /**
     * The block id of the target of a jump.
     */
    @Getter
    private Optional<Integer> jumpTarget;

    /**
     * For a call instruction: name of the called method
     */
    @Getter
    private Optional<String> callReference;

    public Instruction(InstructionType type, String text, List<Integer> inputRegisters,
                       Optional<Integer> overwriteRegister, Optional<Integer> targetRegister,
                       List<Integer> dataDependencies, Optional<Integer> jumpTarget) {
        // some input validation
        assert overwriteRegister.isEmpty() || targetRegister.isPresent();
        for (int reg: inputRegisters) {
            assert overwriteRegister.isEmpty() || reg != overwriteRegister.get();
            assert targetRegister.isEmpty() || reg != targetRegister.get();
        }

        this.type = type;
        this.text = text;
        this.inputRegisters = inputRegisters;
        this.overwriteRegister = overwriteRegister;
        this.targetRegister = targetRegister;
        this.dataDependencies = dataDependencies;
        this.jumpTarget = jumpTarget;
        this.callReference = Optional.empty();

        checkValid();
    }

    public String mapRegisters(Map<Integer, String> mapping) {
        assert overwriteRegister.isEmpty() || !mapping.containsKey(overwriteRegister.get()) ||
                mapping.get(overwriteRegister.get()) == mapping.get(targetRegister.get());
        return mapRegisters(vRegister -> mapping.get(vRegister));
    }

    public String mapRegisters(Function<Integer, String> registerToString) {
        String result = text;
        List<Integer> toReplace = new ArrayList<>();
        for (int i: inputRegisters) {
            toReplace.add(i);
        }
        if (targetRegister.isPresent()) {
            toReplace.add(targetRegister.get());
        }
        // insert larger vRegister numbers first, to avoid a wrong match of a prefix of the number
        toReplace.sort(Comparator.reverseOrder());
        for (int vRegister: toReplace) {
            result = result.replace("@" + vRegister, registerToString.apply(vRegister));
        }
        return result;
    }

    @Override
    public String toString() {
        String suffix = "";
        if (overwriteRegister.isPresent()) {
            suffix = String.format(" /* overwrite: @%d */", overwriteRegister.get());
        }
        return text + suffix;
    }

    public boolean isDivOrMod() {
        return type == InstructionType.DIV || type == InstructionType.MOD;
    }

    public int inputRegister(int index) {
        return inputRegisters.get(index);
    }

    // ==== some helper methods for simplifying construction ====

    public void addDataDependency(int instruction) {
        dataDependencies.add(instruction);
    }

    private void checkValid() {
        if (this.type != InstructionType.CALL) {
            String result = mapRegisters(vRegister -> "-");
            if (result.contains("@")) {
                throw new IllegalArgumentException(
                        String.format("Instruction contains unassigned register: %s", result));
            }
        }
    }

    /**
     * general (arithmetic) operation, possibly with overwrite register
     */
    public static Instruction newOp(String text, List<Integer> inputRegisters,
                                    Optional<Integer> overwriteRegister, int targetRegister) {
        return new Instruction(InstructionType.GENERAL, text, inputRegisters,
                overwriteRegister, Optional.of(targetRegister),
                new ArrayList<>(), Optional.empty());
    }

    /**
     * some operations (e.g. that write to memory) don't have a target register
     */
    public static Instruction newInput(String text, List<Integer> inputRegisters) {
        return new Instruction(InstructionType.GENERAL, text, inputRegisters,
                Optional.empty(), Optional.empty(),
                new ArrayList<>(), Optional.empty());
    }

    /**
     * (implicit) input and data dependency of a jump is the last executed conditional
     */
    public static Instruction newJmp(String text, int targetBlockId) {
        return new Instruction(InstructionType.GENERAL, text, List.of(),
                Optional.empty(), Optional.empty(),
                new ArrayList<>(), Optional.of(targetBlockId));
    }

    /**
     * a return takes a single virtual register as return value (if not void)
     */
    public static Instruction newRet(Optional<Integer> returnRegister) {
        String text = "ret";
        List<Integer> input = List.of();
        if (returnRegister.isPresent()) {
            text += String.format(" @%d", returnRegister.get());
            input = List.of(returnRegister.get());
        }
        return new Instruction(InstructionType.RET, text, input,
                Optional.empty(), Optional.empty(),
                new ArrayList<>(), Optional.empty());
    }

    public static Instruction newDiv(int dividend, int divisor, int result) {
        String text = String.format("div @%d, @%d, @%d", dividend, divisor, result);
        return new Instruction(InstructionType.DIV, text, List.of(dividend, divisor),
                Optional.empty(), Optional.of(result),
                new ArrayList<>(), Optional.empty());
    }

    public static Instruction newMod(int dividend, int divisor, int result) {
        String text = String.format("mod @%d, @%d, @%d", dividend, divisor, result);
        return new Instruction(InstructionType.MOD, text, List.of(dividend, divisor),
                Optional.empty(), Optional.of(result),
                new ArrayList<>(), Optional.empty());
    }

    /**
     * signed move between two virtual registers
     */
    public static Instruction newSignedMov(int source, int target) {
        String text = String.format("mov @%d, @%d", source, target);
        return new Instruction(InstructionType.MOV_S, text, List.of(source),
                Optional.empty(), Optional.of(target),
                new ArrayList<>(), Optional.empty());
    }

    /**
     * unsigned move between two virtual registers
     */
    public static Instruction newUnsignedMov(int source, int target) {
        String text = String.format("mov @%d, @%d", source, target);
        return new Instruction(InstructionType.MOV_U, text, List.of(source),
                Optional.empty(), Optional.of(target),
                new ArrayList<>(), Optional.empty());
    }

    /**
     * move between two virtual registers of equal size
     */
    public static Instruction newMov(int source, int target) {
        return newSignedMov(source, target);
    }

    public static Instruction newCall(List<Integer> args, Optional<Integer> result, String callReference) {
        String text = String.format("call \"%s\"", callReference);
        for (int arg: args) {
            text += ", @" + arg;
        }
        if (result.isPresent()) {
            text += " -> @" + result.get();
        }
        Instruction call = new Instruction(InstructionType.CALL, text, args,
                Optional.empty(), result,
                new ArrayList<>(), Optional.empty());
        call.callReference = Optional.of(callReference);
        return call;
    }
}
