package edu.kit.compiler.data;

import java.io.IOException;
import java.util.Optional;

import edu.kit.compiler.Result;

public class FileInputException extends CompilerException {
    public FileInputException(IOException exception) {
        super("unable to read file: " + exception.getMessage());
    }

    @Override
    public Optional<Positionable> getPosition() {
        return Optional.empty();
    }

    @Override
    public Optional<String> getCompilerStage() {
        return Optional.empty();
    }

    @Override
    public Result getResult() {
        return Result.FileInputError;
    }
    
}
