package edu.kit.compiler.semantic;

import edu.kit.compiler.JavaEasyCompiler;
import edu.kit.compiler.data.CompilerException;
import edu.kit.compiler.data.Positionable;

import java.util.Optional;

public class SemanticException extends CompilerException {
    private Optional<Positionable> sourceLocation;

    public SemanticException(String msg, Positionable sourceLocation) {
        super(msg);
        this.sourceLocation = Optional.of(sourceLocation);
    }

    public SemanticException(SemanticError error) {
        this(error.getMessage(), error);
    }

    @Override
    public Optional<Positionable> getSourceLocation() {
        return sourceLocation;
    }

    @Override
    public Optional<String> getCompilerStage() {
        return Optional.of("semantic analysis");
    }

    @Override
    public JavaEasyCompiler.Result getResult() {
        return JavaEasyCompiler.Result.SemanticError;
    }
}
