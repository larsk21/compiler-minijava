package edu.kit.compiler;

/**
 * Represents the result of a command execution.
 */
public enum Result {
    Ok(0),
    CliInputError(1),
    FileInputError(1),
    LexError(1),
    ParseError(1),
    SemanticError(1),
    StandardLibraryError(1);

    /**
     * @param code The exit code associated with this Result
     */
    Result(int code) {
        this.code = code;
    }

    private final int code;

    /**
     * @return The exit code associated with this Result.
     */
    public int getCode() {
        return code;
    }
}
