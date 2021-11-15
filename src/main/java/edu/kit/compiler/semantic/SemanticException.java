package edu.kit.compiler.semantic;

import edu.kit.compiler.JavaEasyCompiler;
import edu.kit.compiler.data.CompilerException;

import java.util.Optional;

public class SemanticException extends CompilerException {

    private final SourceLocation sourceLocation;

    /**
     * This constructor should be avoided. Implementation needs to switch to the one with source location once we
     * get the error location in our AST.
     * @param msg
     */
    @Deprecated
    public SemanticException(String msg) {
        super(msg);
        sourceLocation = new SourceLocation(0, 0);
    }

    public SemanticException(String msg, SourceLocation sourceLocation) {
        super(msg);
        this.sourceLocation = sourceLocation;
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
