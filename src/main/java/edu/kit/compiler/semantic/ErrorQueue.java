package edu.kit.compiler.semantic;

import edu.kit.compiler.logger.Logger;
import lombok.Getter;
import lombok.NonNull;

public class ErrorQueue {
    @Getter
    private boolean hasError;
    @NonNull
    private Logger logger;

    public ErrorQueue(Logger logger) {
        this.hasError = false;
        this.logger = logger.withName("semantic check");
    }

    public void receive(SemanticError error) {
        hasError = true;
        logger.error(error.getLine(), error.getColumn(), error.getMessage());
    }
}
