package edu.kit.compiler.transform;

import edu.kit.compiler.Result;
import edu.kit.compiler.data.CompilerException;
import edu.kit.compiler.data.Positionable;

import java.util.Optional;

public class TransformException extends CompilerException {

    public TransformException(String msg) {
        super(msg);
    }

    @Override
    public Optional<Positionable> getPosition() {
        return Optional.empty();
    }

    @Override
    public Optional<String> getCompilerStage() {
        return Optional.of("transform");
    }

    @Override
    public Result getResult() {
        return Result.TransformError;
    }
}
