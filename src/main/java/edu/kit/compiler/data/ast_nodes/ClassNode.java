package edu.kit.compiler.data.ast_nodes;

import edu.kit.compiler.data.AstNode;
import edu.kit.compiler.data.AstObject;
import edu.kit.compiler.data.AstVisitor;
import edu.kit.compiler.data.DataType;
import edu.kit.compiler.data.ast_nodes.MethodNode.DynamicMethodNode;
import edu.kit.compiler.data.ast_nodes.MethodNode.StaticMethodNode;
import edu.kit.compiler.semantic.Definition;
import edu.kit.compiler.semantic.DefinitionKind;
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
    public <T> T accept(AstVisitor<T> visitor) {
        return visitor.visit(this);
    }

    public static class ClassNodeField extends AstObject implements Definition {

        public ClassNodeField(int line, int column, DataType type, int name, boolean hasError) {
            super(line, column, hasError);

            this.type = type;
            this.name = name;
        }

        @Getter
        private DataType type;
        @Getter
        private int name;

        @Override
        public DefinitionKind getKind() {
            return DefinitionKind.Field;
        }
    }

}
