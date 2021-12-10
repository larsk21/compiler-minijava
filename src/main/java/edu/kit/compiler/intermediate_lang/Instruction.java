package edu.kit.compiler.intermediate_lang;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Represents a command in semi-textual form. Virtual registers are prefixed with '@'.
 * Usage examples:
 *
 * @3 = @1 + @2     =>   Instruction.newOp("addl @1, @3", new int[] { 1 }, Optional.of(2), 3)
 *
 * @1 < @2          =>   Instruction.newInput("cmpl @1, @2", new int[] { 1, 2 })
 *                       Instruction.newJmp("jl ???", ???)   TODO: define ???
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
    private int[] inputRegisters;

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
     * TODO: is an integer appropriate to identify an instruction in the intermediate language?
     */
    @Getter
    private List<Integer> dataDependencies;
    // TODO: how to represent jump targets? reference to the according block?
    @Getter
    private Optional<Integer> jumpTarget;

    /**
     * For a call instruction: name of the called method
     */
    @Getter
    private Optional<String> callReference;

    public Instruction(InstructionType type, String text, int[] inputRegisters,
                       Optional<Integer> overwriteRegister, Optional<Integer> targetRegister,
                       List<Integer> dataDependencies, Optional<Integer> jumpTarget) {
        // some input validation
        assert overwriteRegister.isEmpty() || targetRegister.isPresent();
        for (int reg: inputRegisters) {
            assert overwriteRegister.isEmpty() || reg != overwriteRegister.get();
            assert targetRegister.isEmpty() || reg != targetRegister.get();
        }
        // TODO: check whether all used registers are in text (and vice-versa?)

        this.type = type;
        this.text = text;
        this.inputRegisters = inputRegisters;
        this.overwriteRegister = overwriteRegister;
        this.targetRegister = targetRegister;
        this.dataDependencies = dataDependencies;
        this.jumpTarget = jumpTarget;
    }

    public String mapRegisters(Map<Integer, String> mapping) {
        assert overwriteRegister.isEmpty() || !mapping.containsKey(overwriteRegister.get()) ||
                mapping.get(overwriteRegister.get()) == mapping.get(targetRegister.get());
        // TODO ...
        return "";
    }

    @Override
    public String toString() {
        String suffix = "";
        if (overwriteRegister.isPresent()) {
            suffix = String.format(" /* overwrite: @%d */", overwriteRegister.get());
        }
        return text + suffix;
        // TODO: output data dependencies and jump target?
    }

    // ==== some helper methods for simplifying construction ====

    public void addDataDependency(int instruction) {
        dataDependencies.add(instruction);
    }

    /**
     * general (arithmetic) operation, possibly with overwrite register
     */
    public static Instruction newOp(String text, int[] inputRegisters,
                                    Optional<Integer> overwriteRegister, int targetRegister) {
        return new Instruction(InstructionType.GENERAL, text, inputRegisters,
                overwriteRegister, Optional.of(targetRegister),
                new ArrayList<>(), Optional.empty());
    }

    /**
     * some operations (e.g. that write to memory) don't have a target register
     */
    public static Instruction newInput(String text, int[] inputRegisters) {
        return new Instruction(InstructionType.GENERAL, text, inputRegisters,
                Optional.empty(), Optional.empty(),
                new ArrayList<>(), Optional.empty());
    }

    /**
     * (implicit) input and data dependency of a jump is the last executed conditional
     */
    public static Instruction newJmp(String text, int condInstruction) {
        return new Instruction(InstructionType.GENERAL, text, new int[] {},
                Optional.empty(), Optional.empty(),
                new ArrayList<>(condInstruction), Optional.empty());
    }

    // TODO: newDiv, newMod, newCall, newRet
}
