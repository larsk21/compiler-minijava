package edu.kit.compiler.semantic;

import edu.kit.compiler.JavaEasyCompiler;
import edu.kit.compiler.data.CompilerException;

import java.util.Optional;

public class SemanticException extends CompilerException {

    private final SourceLocation sourceLocation;

    public SemanticException(String msg, SourceLocation sourceLocation) {
        super(msg);
        this.sourceLocation = sourceLocation;
    }

    @Override
    public Optional<SourceLocation> getSourceLocation() {
        return Optional.of(sourceLocation);
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
