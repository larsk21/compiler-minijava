package edu.kit.compiler.semantic;

import edu.kit.compiler.data.Positionable;
import lombok.Getter;

public class SemanticError implements Positionable {
    public SemanticError(int line, int column, String message) {
        this.message = message;
        this.line = line;
        this.column = column;
    }

    public SemanticError(Positionable position, String message) {
        this(position.getLine(), position.getColumn(), message);
    }

    @Getter
    private String message;
    @Getter
    private int line;
    @Getter
    private int column;
}
