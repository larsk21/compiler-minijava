package edu.kit.compiler.data;

/**
 * Represents a node in the abstract syntax tree (AST).
 */
public abstract class AstNode extends AstObject {

    /**
     * Create a new AST node with position and error indication.
     */
    public AstNode(int line, int column, boolean hasError) {
        super(line, column, hasError);
    }

    /**
     * Accept a visitor. 
     * 
     * The usual code for the implementation looks like:
     * `visitor.visit(this);`
     * 
     * @param <T> type of the visitor's result value
     * @param visitor AST visitor
     * @return result from the visitor
     */
    public abstract <T> T accept(AstVisitor<T> visitor);

}
