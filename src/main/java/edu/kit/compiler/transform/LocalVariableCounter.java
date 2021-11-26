package edu.kit.compiler.transform;

import java.util.HashMap;
import java.util.Map;

import edu.kit.compiler.data.AstVisitor;
import edu.kit.compiler.data.ast_nodes.MethodNode;
import edu.kit.compiler.data.ast_nodes.MethodNode.DynamicMethodNode;
import edu.kit.compiler.data.ast_nodes.MethodNode.StaticMethodNode;
import edu.kit.compiler.data.ast_nodes.StatementNode.BlockStatementNode;
import edu.kit.compiler.data.ast_nodes.StatementNode.ExpressionStatementNode;
import edu.kit.compiler.data.ast_nodes.StatementNode.IfStatementNode;
import edu.kit.compiler.data.ast_nodes.StatementNode.LocalVariableDeclarationStatementNode;
import edu.kit.compiler.data.ast_nodes.StatementNode.ReturnStatementNode;
import edu.kit.compiler.data.ast_nodes.StatementNode.WhileStatementNode;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;


@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LocalVariableCounter {

    /**
     * Computes a unique index for each local variable of the given method.
     * Variables are numbered starting at zero in the order of their first
     * appearance.
     * 
     * @param method the method for which to compute the mapping
     * @return a map from variables names to indices
     */
    public static Map<Integer, Integer> apply(MethodNode method) {
        var visitor = new Visitor();
        method.accept(visitor);
        return visitor.localVariables;
    }

    private static class Visitor implements AstVisitor<Void> {
        private final Map<Integer, Integer> localVariables = new HashMap<>();
        private int variableCount = 0;

        @Override
        public Void visit(StaticMethodNode method) {
            return visitMethod(method);
        }

        @Override
        public Void visit(DynamicMethodNode method) {
            return visitMethod(method);
        }

        private Void visitMethod(MethodNode method) {
            return method.getStatementBlock().accept(this);
        }

        @Override
        public Void visit(BlockStatementNode block) {
            for (var statement : block.getStatements()) {
                statement.accept(this);
            }

            return (Void)null;
        }

        @Override
        public Void visit(LocalVariableDeclarationStatementNode statement) {
            localVariables.computeIfAbsent(statement.getName(), id -> variableCount++);
            return (Void)null;
        }

        @Override
        public Void visit(IfStatementNode statement) {
            statement.getThenStatement().accept(this);
            if (statement.getElseStatement().isPresent()) {
                statement.getElseStatement().get().accept(this);
            }

            return (Void)null;
        }

        @Override
        public Void visit(WhileStatementNode statement) {
            statement.getStatement().accept(this);
            return (Void)null;
        }

        @Override
        public Void visit(ReturnStatementNode statement) {
            return (Void)null;
        }

        @Override
        public Void visit(ExpressionStatementNode statement) {
            return (Void)null;
        }
    }
}

