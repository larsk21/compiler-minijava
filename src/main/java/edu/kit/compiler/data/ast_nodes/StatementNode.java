package edu.kit.compiler.data.ast_nodes;

import edu.kit.compiler.data.AstNode;
import edu.kit.compiler.data.AstVisitor;
import edu.kit.compiler.data.DataType;
import edu.kit.compiler.semantic.Definition;
import edu.kit.compiler.semantic.DefinitionKind;
import lombok.Getter;

import java.util.Optional;

public abstract class StatementNode extends AstNode {

    public StatementNode(int line, int column, boolean hasError) {
        super(line, column, hasError);
    }

    public static class BlockStatementNode extends StatementNode {

        public BlockStatementNode(int line, int column, Iterable<StatementNode> statements, boolean hasError) {
            super(line, column, hasError);

            this.statements = statements;
        }

        @Getter
        private Iterable<StatementNode> statements;

        @Override
        public <T> T accept(AstVisitor<T> visitor) {
            return visitor.visit(this);
        }

    }

    public static class LocalVariableDeclarationStatementNode extends StatementNode implements Definition {

        public LocalVariableDeclarationStatementNode(
            int line, int column,
            DataType type, int name, Optional<ExpressionNode> expression,
            boolean hasError
        ) {
            super(line, column, hasError);

            this.type = type;
            this.name = name;
            this.expression = expression;
        }

        @Getter
        private DataType type;
        @Getter
        private int name;
        @Getter
        private Optional<ExpressionNode> expression;

        @Override
        public <T> T accept(AstVisitor<T> visitor) {
            return visitor.visit(this);
        }

        @Override
        public DefinitionKind getKind() {
            return DefinitionKind.LocalVariable;
        }
    }

    public static class IfStatementNode extends StatementNode {

        public IfStatementNode(
            int line, int column,
            ExpressionNode condition,
            StatementNode thenStatement, Optional<StatementNode> elseStatement,
            boolean hasError
        ) {
            super(line, column, hasError);

            this.condition = condition;

            this.thenStatement = thenStatement;
            this.elseStatement = elseStatement;
        }

        @Getter
        private ExpressionNode condition;

        @Getter
        private StatementNode thenStatement;
        @Getter
        private Optional<StatementNode> elseStatement;

        @Override
        public <T> T accept(AstVisitor<T> visitor) {
            return visitor.visit(this);
        }

    }

    public static class WhileStatementNode extends StatementNode {

        public WhileStatementNode(
            int line, int column,
            ExpressionNode condition, StatementNode statement,
            boolean hasError
        ) {
            super(line, column, hasError);

            this.condition = condition;

            this.statement = statement;
        }

        @Getter
        private ExpressionNode condition;

        @Getter
        private StatementNode statement;

        @Override
        public <T> T accept(AstVisitor<T> visitor) {
            return visitor.visit(this);
        }

    }

    public static class ReturnStatementNode extends StatementNode {

        public ReturnStatementNode(
            int line, int column,
            Optional<ExpressionNode> result,
            boolean hasError
        ) {
            super(line, column, hasError);

            this.result = result;
        }

        @Getter
        private Optional<ExpressionNode> result;

        @Override
        public <T> T accept(AstVisitor<T> visitor) {
            return visitor.visit(this);
        }

    }

    public static class ExpressionStatementNode extends StatementNode {

        public ExpressionStatementNode(
            int line, int column,
            ExpressionNode expression,
            boolean hasError
        ) {
            super(line, column, hasError);

            this.expression = expression;
        }

        @Getter
        private ExpressionNode expression;

        @Override
        public <T> T accept(AstVisitor<T> visitor) {
            return visitor.visit(this);
        }

    }

}
