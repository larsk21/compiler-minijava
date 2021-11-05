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
public interface AstVisitor {

    void visit(ProgramNode programNode);
    void visit(ClassNode classNode);
    void visit(StaticMethodNode staticMethodNode);
    void visit(DynamicMethodNode dynamicMethodNode);
    void visit(LocalVariableDeclarationStatementNode localVariableDeclarationStatementNode);
    void visit(IfStatementNode ifStatementNode);
    void visit(WhileStatementNode whileStatementNode);
    void visit(ReturnStatementNode returnStatementNode);
    void visit(ExpressionStatementNode expressionStatementNode);
    void visit(BinaryExpressionNode binaryExpressionNode);
    void visit(UnaryExpressionNode unaryExpressionNode);
    void visit(MethodInvocationExpressionNode methodInvocationExpressionNode);
    void visit(FieldAccessExpressionNode fieldAccessExpressionNode);
    void visit(ArrayAccessExpressionNode arrayAccessExpressionNode);
    void visit(ValueExpressionNode valueExpressionNode);
    void visit(NewObjectExpressionNode newObjectExpressionNode);
    void visit(NewArrayExpressionNode newArrayExpressionNode);

}
