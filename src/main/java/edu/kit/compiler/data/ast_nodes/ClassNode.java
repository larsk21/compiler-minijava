package edu.kit.compiler.data.ast_nodes;

import edu.kit.compiler.data.AstNode;
import edu.kit.compiler.data.AstVisitor;
import edu.kit.compiler.data.DataType;
import edu.kit.compiler.data.ast_nodes.MethodNode.DynamicMethodNode;
import edu.kit.compiler.data.ast_nodes.MethodNode.StaticMethodNode;

import lombok.AllArgsConstructor;
import lombok.Getter;

public class ClassNode extends AstNode {

    public ClassNode(
        int line, int column,
        int name, Iterable<ClassNodeField> fields,
        Iterable<StaticMethodNode> staticMethods, Iterable<DynamicMethodNode> dynamicMethods,
        boolean hasError
    ) {
        super(line, column, hasError);

        this.name = name;
        this.fields = fields;

        this.staticMethods = staticMethods;
        this.dynamicMethods = dynamicMethods;
    }

    @Getter
    private int name;
    @Getter
    private Iterable<ClassNodeField> fields;

    @Getter
    private Iterable<StaticMethodNode> staticMethods;
    @Getter
    private Iterable<DynamicMethodNode> dynamicMethods;

    @Override
    public void accept(AstVisitor visitor) {
        visitor.visit(this);
    }

    @AllArgsConstructor
    public static class ClassNodeField {

        @Getter
        private DataType type;
        @Getter
        private int name;

    }

}
