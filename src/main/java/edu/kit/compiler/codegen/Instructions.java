package edu.kit.compiler.codegen;

import java.util.LinkedList;
import java.util.Optional;

import edu.kit.compiler.intermediate_lang.Instruction;
import edu.kit.compiler.intermediate_lang.RegisterSize;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Instructions {
    public static Instruction newUnary(String command, RegisterSize size,
            Operand.Target operand, Optional<Integer> targetRegister) {

        if (targetRegister.isPresent()) {
            return newUnary(command, size, operand, targetRegister.get());
        } else {
            return Instruction.newInput(
                    Util.formatCmd(command, size, operand),
                    operand.getSourceRegisters());
        }
    }

    public static Instruction newUnary(String command, RegisterSize size,
            Operand.Target operand, int targetRegister) {
        var inputRegisters = new LinkedList<>(operand.getSourceRegisters());
        var overwriteRegister = operand.getTargetRegister();

        if (overwriteRegister.isPresent()) {
            inputRegisters.removeIf(overwriteRegister.get()::equals);
        }

        return Instruction.newOp(
                Util.formatCmd(command, size, targetRegister),
                inputRegisters, overwriteRegister, targetRegister);
    }

    public static Instruction newBinary(String command, RegisterSize size,
            Operand.Target target, Operand.Source source,
            Optional<Integer> targetRegister) {
        if (targetRegister.isPresent()) {
            return newBinary(command, size, target, source, targetRegister.get());
        } else {
            var inputRegisters = new LinkedList<>(target.getSourceRegisters());
            inputRegisters.addAll(source.getSourceRegisters());
            return Instruction.newInput(
                    Util.formatCmd(command, size, source, target),
                    inputRegisters);
        }
    }

    public static Instruction newBinary(String command, RegisterSize size,
            Operand.Target target, Operand.Source source,
            int targetRegister) {
        var overwriteRegister = target.getTargetRegister();
        var inputRegisters = new LinkedList<>(target.getSourceRegisters());
        inputRegisters.addAll(source.getSourceRegisters());

        if (overwriteRegister.isPresent()) {
            inputRegisters.removeIf(overwriteRegister.get()::equals);
        }

        return Instruction.newOp(
                Util.formatCmd(command, size, source, targetRegister),
                inputRegisters, overwriteRegister, targetRegister);
    }
}
