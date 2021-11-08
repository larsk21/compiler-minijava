package edu.kit.compiler.data.ast_nodes;

import java.util.Optional;

import edu.kit.compiler.data.AstNode;
import edu.kit.compiler.data.AstVisitor;
import edu.kit.compiler.data.DataType;

import lombok.AllArgsConstructor;
import lombok.Getter;

public abstract class MethodNode extends AstNode {

    public MethodNode(
        int line, int column,
        DataType type, int name, Iterable<MethodNodeParameter> parameters, MethodNodeRest rest,
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
    private MethodNodeRest rest;

    @Getter
    private Iterable<StatementNode> statements;

    @AllArgsConstructor
    public static class MethodNodeParameter {

        @Getter
        private DataType type;
        @Getter
        private int name;

    }

    @AllArgsConstructor
    public static class MethodNodeRest {

        @Getter
        private Optional<Integer> throwsTypeIdentifier;

    }

    public static class StaticMethodNode extends MethodNode {

        public StaticMethodNode(
            int line, int column,
            DataType type, int name, Iterable<MethodNodeParameter> parameters, MethodNodeRest rest,
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
            DataType type, int name, Iterable<MethodNodeParameter> parameters, MethodNodeRest rest,
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
