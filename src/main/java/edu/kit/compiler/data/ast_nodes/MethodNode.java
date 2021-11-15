package edu.kit.compiler.data.ast_nodes;

import edu.kit.compiler.data.AstNode;
import edu.kit.compiler.data.AstObject;
import edu.kit.compiler.data.AstVisitor;
import edu.kit.compiler.data.DataType;
import edu.kit.compiler.semantic.Definition;
import lombok.Getter;

import java.util.Optional;

public abstract class MethodNode extends AstNode implements Definition {

    public MethodNode(
            int line, int column,
            DataType type, int name, Iterable<MethodNodeParameter> parameters, Optional<MethodNodeRest> rest,
            Iterable<StatementNode> statements,
            boolean hasError
    ) {
        super(line, column, hasError);

        this.type = type;
        this.name = name;
        this.parameters = parameters;
        this.rest = rest;

        this.statements = statements;
    }

    @Getter
    private DataType type;
    @Getter
    private int name;
    @Getter
    private Iterable<MethodNodeParameter> parameters;
    @Getter
    private Optional<MethodNodeRest> rest;

    @Getter
    private Iterable<StatementNode> statements;

    /**
     * Definitions of method node parameters can overshadow outer variables as well.
     */
    public static class MethodNodeParameter extends AstObject implements Definition {

        public MethodNodeParameter(int line, int column, DataType type, int name, boolean hasError) {
            super(line, column, hasError);

            this.type = type;
            this.name = name;
        }

        @Getter
        private DataType type;
        @Getter
        private int name;

    }

    public static class MethodNodeRest extends AstObject {

        public MethodNodeRest(int line, int column, Integer throwsTypeIdentifier, boolean hasError) {
            super(line, column, hasError);

            this.throwsTypeIdentifier = throwsTypeIdentifier;
        }

        @Getter
        private Integer throwsTypeIdentifier;

    }

    public static class StaticMethodNode extends MethodNode {

        public StaticMethodNode(
                int line, int column,
                DataType type, int name, Iterable<MethodNodeParameter> parameters, Optional<MethodNodeRest> rest,
                Iterable<StatementNode> statements,
                boolean hasError
        ) {
            super(line, column, type, name, parameters, rest, statements, hasError);
        }

        @Override
        public <T> T accept(AstVisitor<T> visitor) {
            return visitor.visit(this);
        }

    }

    public static class DynamicMethodNode extends MethodNode {

        public DynamicMethodNode(
                int line, int column,
                DataType type, int name, Iterable<MethodNodeParameter> parameters, Optional<MethodNodeRest> rest,
                Iterable<StatementNode> statements,
                boolean hasError
        ) {
            super(line, column, type, name, parameters, rest, statements, hasError);
        }

        @Override
        public <T> T accept(AstVisitor<T> visitor) {
            return visitor.visit(this);
        }

    }

}
