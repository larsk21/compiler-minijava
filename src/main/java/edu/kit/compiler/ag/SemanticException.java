package edu.kit.compiler.ag;

import edu.kit.compiler.JavaEasyCompiler;
import edu.kit.compiler.data.CompilerException;

import java.util.Optional;

public class SemanticException extends CompilerException {

    public SemanticException(String msg) {
        super(msg);
    }

    @Override
    public Optional<SourceLocation> getSourceLocation() {
        // TODO: Figure out location of symbol when its already defined
        return Optional.empty();
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
