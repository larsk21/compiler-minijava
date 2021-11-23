package edu.kit.compiler.data;

import java.util.Optional;

import edu.kit.compiler.Result;

/**
 * Base for all custom exceptions thrown in any phase of the compiler.
 */
public abstract class CompilerException extends RuntimeException {
    public CompilerException() {
        super();
    }
    
    public CompilerException(String message) {
        super(message);
    }

    /**
     * Return the optional source location of the character that caused the exception.
     */
    public abstract Optional<Positionable> getPosition();

    /**
     * Return name of the compiler stage in which the exception occurred.
     * This method is required for logging purposes.
     */
    public abstract Optional<String> getCompilerStage();

    /**
     * Return the result (and therefore exit code) that should result from this exception.
     */
    public abstract Result getResult();

    /**
     * Whether log output for the exception should be surpressed.
     */
    public boolean messageIsSurpressed() {
        return false;
    }
}
