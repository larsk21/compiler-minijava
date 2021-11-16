package edu.kit.compiler.semantic;

import edu.kit.compiler.JavaEasyCompiler;
import edu.kit.compiler.data.CompilerException;
import edu.kit.compiler.data.Positionable;

import java.util.Optional;

public class SemanticException extends CompilerException {
    private Optional<Positionable> position;

    public SemanticException(String msg, Positionable position) {
        super(msg);
        this.position = Optional.of(position);
    }

    public SemanticException(SemanticError error) {
        this(error.getMessage(), error);
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
}
