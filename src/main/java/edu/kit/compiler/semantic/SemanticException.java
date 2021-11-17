package edu.kit.compiler.semantic;

import edu.kit.compiler.JavaEasyCompiler;
import edu.kit.compiler.data.CompilerException;
import edu.kit.compiler.data.Positionable;

import java.util.Optional;

public class SemanticException extends CompilerException {
    private Optional<Positionable> position;
    private boolean quiet;

    public SemanticException(String msg, Positionable position) {
        super(msg);
        this.position = Optional.of(position);
        this.quiet = false;
    }

    public SemanticException(SemanticError error) {
        this(error.getMessage(), error);
        this.quiet = false;
    }

    private SemanticException() {
        super("");
        this.position = Optional.empty();
        this.quiet = true;
    }

    public static SemanticException quietException() {
        return new SemanticException();
    }

    @Override
    public Optional<Positionable> getPosition() {
        return position;
    }

    @Override
    public Optional<String> getCompilerStage() {
        return Optional.of("semantic analysis");
    }

    @Override
    public JavaEasyCompiler.Result getResult() {
        return JavaEasyCompiler.Result.SemanticError;
    }

    public boolean surpressMessage() {
        return quiet;
    }
}
