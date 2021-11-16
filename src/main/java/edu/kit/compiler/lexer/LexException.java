package edu.kit.compiler.lexer;

import java.util.Optional;

import edu.kit.compiler.JavaEasyCompiler.Result;
import edu.kit.compiler.data.CompilerException;
import edu.kit.compiler.data.Positionable;
import lombok.Getter;

/**
 * Exception thrown by the Lexer in case of unexpected characters.
 */
public class LexException extends CompilerException implements Positionable {
    @Getter
    private int line;
    @Getter
    private int column;

    /**
     * Create a new LexException.
     * 
     * @param line Line position of the character that caused the exception.
     * @param column Column position of the character that caused the
     * exception.
     * @param message Message describing the exception further.
     */
    public LexException(int line, int column, String message) {
        super(message);

        this.line = line;
        this.column = column;
    }

    @Override
    public Optional<Positionable> getSourceLocation() {
        return Optional.of(this);
    }

    @Override
    public Optional<String> getCompilerStage() {
        return Optional.of("lexer");
    }

    @Override
    public Result getResult() {
        return Result.LexError;
    }
}
