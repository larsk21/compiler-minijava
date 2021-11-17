package edu.kit.compiler.data;

import edu.kit.compiler.data.ast_nodes.*;
import edu.kit.compiler.data.ast_nodes.ExpressionNode.*;
import edu.kit.compiler.data.ast_nodes.MethodNode.*;
import edu.kit.compiler.data.ast_nodes.StatementNode.*;

/**
 * Represents a visitor of an abstract syntax tree (AST).
 * 
 * For more information: see visitor pattern.
 */
public interface AstVisitor<T> {

    default T visit(ProgramNode programNode) { throw new UnsupportedOperationException(); }
    default T visit(ClassNode classNode) { throw new UnsupportedOperationException(); }
    default T visit(StaticMethodNode staticMethodNode) { throw new UnsupportedOperationException(); }
    default T visit(DynamicMethodNode dynamicMethodNode) { throw new UnsupportedOperationException(); }
    default T visit(StandardLibraryMethodNode standardLibraryMethodNode) { throw new UnsupportedOperationException(); }
    default T visit(BlockStatementNode blockStatementNode) { throw new UnsupportedOperationException(); }
    default T visit(LocalVariableDeclarationStatementNode localVariableDeclarationStatementNode) { throw new UnsupportedOperationException(); }
    default T visit(IfStatementNode ifStatementNode) { throw new UnsupportedOperationException(); }
    default T visit(WhileStatementNode whileStatementNode) { throw new UnsupportedOperationException(); }
    default T visit(ReturnStatementNode returnStatementNode) { throw new UnsupportedOperationException(); }
    default T visit(ExpressionStatementNode expressionStatementNode) { throw new UnsupportedOperationException(); }
    default T visit(BinaryExpressionNode binaryExpressionNode) { throw new UnsupportedOperationException(); }
    default T visit(UnaryExpressionNode unaryExpressionNode) { throw new UnsupportedOperationException(); }
    default T visit(MethodInvocationExpressionNode methodInvocationExpressionNode) { throw new UnsupportedOperationException(); }
    default T visit(FieldAccessExpressionNode fieldAccessExpressionNode) { throw new UnsupportedOperationException(); }
    default T visit(ArrayAccessExpressionNode arrayAccessExpressionNode) { throw new UnsupportedOperationException(); }
    default T visit(IdentifierExpressionNode identifierExpressionNode) { throw new UnsupportedOperationException(); }
    default T visit(ThisExpressionNode thisExpressionNode) { throw new UnsupportedOperationException(); }
    default T visit(ValueExpressionNode valueExpressionNode) { throw new UnsupportedOperationException(); }
    default T visit(NewObjectExpressionNode newObjectExpressionNode) { throw new UnsupportedOperationException(); }
    default T visit(NewArrayExpressionNode newArrayExpressionNode) { throw new UnsupportedOperationException(); }

}
