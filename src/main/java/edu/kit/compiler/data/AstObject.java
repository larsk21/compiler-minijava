package edu.kit.compiler.data;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * Represents an object (not necessarily a node) that appears in the AST.
 */
@AllArgsConstructor
public class AstObject {

    /**
     * Characteristic line position of this object in the source file.
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
    @Setter
    private boolean hasError;

}
