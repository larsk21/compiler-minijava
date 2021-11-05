package edu.kit.compiler.data;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Represents a node in the abstract syntax tree (AST).
 */
@AllArgsConstructor
public abstract class AstNode {

    /**
     * Characteristic line position of this node in the source file.
     */
    @Getter
    private int line;
    /**
     * Characteristic column position of this node in the source file.
     */
    @Getter
    private int column;

    /**
     * Whether this node contains an error.
     */
    @Getter
    private boolean hasError;

    /**
     * Accept a visitor (see visitor pattern).
     */
    public abstract void accept(AstVisitor visitor);

}
