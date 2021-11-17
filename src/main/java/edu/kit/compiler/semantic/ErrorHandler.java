package edu.kit.compiler.semantic;

import edu.kit.compiler.logger.Logger;
import lombok.NonNull;

public class ErrorHandler {
    private boolean hasError;
    @NonNull
    private Logger logger;

    public ErrorHandler(Logger logger) {
        this.hasError = false;
        this.logger = logger.withName("semantic check");
    }

    public void receive(SemanticError error) {
        hasError = true;
        logger.error(error.getLine(), error.getColumn(), error.getMessage());
    }

    public boolean hasError() {
        return hasError;
    }
}
