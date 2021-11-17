package edu.kit.compiler.data.ast_nodes;

import edu.kit.compiler.data.AstNode;
import edu.kit.compiler.data.AstObject;
import edu.kit.compiler.data.AstVisitor;
import edu.kit.compiler.data.DataType;
import edu.kit.compiler.data.ast_nodes.StatementNode.BlockStatementNode;
import edu.kit.compiler.semantic.Definition;
import edu.kit.compiler.semantic.DefinitionKind;
import lombok.Getter;

import java.util.List;
import java.util.Optional;

public abstract class MethodNode extends AstNode {

    public MethodNode(
        int line, int column,
        DataType type, int name, List<MethodNodeParameter> parameters, Optional<MethodNodeRest> rest,
        BlockStatementNode statementBlock,
        boolean hasError
    ) {
        super(line, column, hasError);

        this.type = type;
        this.name = name;
        this.parameters = parameters;
        this.rest = rest;

        this.statementBlock = statementBlock;
    }

    @Getter
    private DataType type;
    @Getter
    private int name;
    @Getter
    private List<MethodNodeParameter> parameters;
    @Getter
    private Optional<MethodNodeRest> rest;

    @Getter
    private BlockStatementNode statementBlock;

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


        @Override
        public DefinitionKind getKind() {
            return DefinitionKind.Parameter;
        }
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
            DataType type, int name, List<MethodNodeParameter> parameters, Optional<MethodNodeRest> rest,
            BlockStatementNode statementBlock,
            boolean hasError
        ) {
            super(line, column, type, name, parameters, rest, statementBlock, hasError);
        }

        @Override
        public <T> T accept(AstVisitor<T> visitor) {
            return visitor.visit(this);
        }

    }

    public static class DynamicMethodNode extends MethodNode {

        public DynamicMethodNode(
            int line, int column,
            DataType type, int name, List<MethodNodeParameter> parameters, Optional<MethodNodeRest> rest,
            BlockStatementNode statementBlock,
            boolean hasError
        ) {
            super(line, column, type, name, parameters, rest, statementBlock, hasError);
        }

        @Override
        public <T> T accept(AstVisitor<T> visitor) {
            return visitor.visit(this);
        }

    }

}
