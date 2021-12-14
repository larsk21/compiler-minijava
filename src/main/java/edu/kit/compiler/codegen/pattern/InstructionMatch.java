package edu.kit.compiler.codegen.pattern;

import java.util.List;
import java.util.Optional;

import edu.kit.compiler.intermediate_lang.Instruction;

public interface InstructionMatch extends Match {

    public abstract List<Instruction> getInstructions();

    public abstract Optional<Integer> getTargetRegister();

    public static InstructionMatch none() {
        return new None();
    }
    
    public static final class None extends Match.None implements InstructionMatch {
        @Override
        public List<Instruction> getInstructions() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Integer> getTargetRegister() {
            return Optional.empty();
        }
    }

    public static abstract class Some extends Match.Some implements InstructionMatch {
    }
}
