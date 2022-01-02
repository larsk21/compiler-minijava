package edu.kit.compiler.register_allocation;

import edu.kit.compiler.intermediate_lang.Register;
import lombok.Getter;

import java.util.EnumSet;
import java.util.List;

public class AssignmentResult {
    @Getter
    List<String> instructions;
    @Getter
    EnumSet<Register> usedRegisters;

    public AssignmentResult(List<String> instructions, EnumSet<Register> usedRegisters) {
        this.instructions = instructions;
        this.usedRegisters = usedRegisters;
    }
}
