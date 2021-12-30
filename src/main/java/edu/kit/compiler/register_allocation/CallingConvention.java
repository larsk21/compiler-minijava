package edu.kit.compiler.register_allocation;

import edu.kit.compiler.intermediate_lang.Register;
import lombok.Getter;

import java.util.EnumSet;
import java.util.Optional;

public class CallingConvention {
    public static final CallingConvention X86_64 = new CallingConvention(
            EnumSet.of(Register.RDI, Register.RSI, Register.RDX, Register.RCX, Register.R8,
                    Register.R9, Register.R10, Register.R11, Register.RAX),
            new Register[] {Register.RDI, Register.RSI, Register.RDX, Register.RCX, Register.R8, Register.R9},
            Register.RAX
    );

    @Getter
    private EnumSet<Register> callerSaved;
    private Register[] argRegisters;
    @Getter
    private Register returnRegister;

    // non-private constructor allows to create test calling conventions
    CallingConvention(EnumSet<Register> callerSaved, Register[] argRegisters, Register returnRegister) {
        this.callerSaved = callerSaved;
        this.argRegisters = argRegisters;
        this.returnRegister = returnRegister;
        for (Register r: argRegisters) {
            assert callerSaved.contains(r);
        }
        assert callerSaved.contains(returnRegister);
    }

    public boolean isCallerSaved(Register r) {
        return callerSaved.contains(r);
    }

    public Optional<Register> getArgRegister(int argPos) {
        if (argPos < argRegisters.length) {
            return Optional.of(argRegisters[argPos]);
        }
        return Optional.empty();
    }
}
