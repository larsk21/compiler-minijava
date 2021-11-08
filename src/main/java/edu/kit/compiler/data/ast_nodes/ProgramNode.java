package edu.kit.compiler.data.ast_nodes;

import edu.kit.compiler.data.AstNode;
import edu.kit.compiler.data.AstVisitor;

import lombok.Getter;

public class ProgramNode extends AstNode {

    public ProgramNode(int line, int column, Iterable<ClassNode> classes, boolean hasError) {
        super(line, column, hasError);

        this.classes = classes;
    }

    @Getter
    private Iterable<ClassNode> classes;

    @Override
    public <T> T accept(AstVisitor<T> visitor) {
        return visitor.visit(this);
    }

}
