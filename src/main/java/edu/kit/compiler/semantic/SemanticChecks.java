package edu.kit.compiler.semantic;

import java.util.Optional;

import edu.kit.compiler.data.AstVisitor;
import edu.kit.compiler.data.Operator.BinaryOperator;
import edu.kit.compiler.data.ast_nodes.ClassNode;
import edu.kit.compiler.data.ast_nodes.ExpressionNode;
import edu.kit.compiler.data.ast_nodes.MethodNode;
import edu.kit.compiler.data.ast_nodes.ProgramNode;
import edu.kit.compiler.data.ast_nodes.StatementNode;
import edu.kit.compiler.data.ast_nodes.ExpressionNode.ArrayAccessExpressionNode;
import edu.kit.compiler.data.ast_nodes.ExpressionNode.BinaryExpressionNode;
import edu.kit.compiler.data.ast_nodes.ExpressionNode.FieldAccessExpressionNode;
import edu.kit.compiler.data.ast_nodes.ExpressionNode.IdentifierExpressionNode;
import edu.kit.compiler.data.ast_nodes.ExpressionNode.MethodInvocationExpressionNode;
import edu.kit.compiler.data.ast_nodes.ExpressionNode.NewArrayExpressionNode;
import edu.kit.compiler.data.ast_nodes.ExpressionNode.NewObjectExpressionNode;
import edu.kit.compiler.data.ast_nodes.ExpressionNode.ThisExpressionNode;
import edu.kit.compiler.data.ast_nodes.ExpressionNode.UnaryExpressionNode;
import edu.kit.compiler.data.ast_nodes.ExpressionNode.ValueExpressionNode;
import edu.kit.compiler.data.ast_nodes.StatementNode.BlockStatementNode;
import edu.kit.compiler.data.ast_nodes.StatementNode.ExpressionStatementNode;
import edu.kit.compiler.data.ast_nodes.StatementNode.IfStatementNode;
import edu.kit.compiler.data.ast_nodes.StatementNode.LocalVariableDeclarationStatementNode;
import edu.kit.compiler.data.ast_nodes.StatementNode.ReturnStatementNode;
import edu.kit.compiler.data.ast_nodes.StatementNode.WhileStatementNode;

public class SemanticChecks {
    public static void applyChecks(ProgramNode ast, ErrorHandler errorHandler, ClassNode stringClass) {
        for (ClassNode currentClass: ast.getClasses()) {
            for (MethodNode method: currentClass.getDynamicMethods()) {
                MethodCheckVisitor visitor = new MethodCheckVisitor(false, errorHandler, stringClass);
                method.getStatementBlock().accept(visitor);
            }
            for (MethodNode method: currentClass.getStaticMethods()) {
                MethodCheckVisitor visitor = new MethodCheckVisitor(true, errorHandler, stringClass);
                method.getStatementBlock().accept(visitor);
            }
        }
    }
}

/**
 * Performs multiple different checks (see SemanticChecks).
 * 
 * Returns whether the analysed part of the AST contains a return statement for every branch.
 */
class MethodCheckVisitor implements AstVisitor<Boolean> {
    /**
     * The predefined class "String".
     */
    private ClassNode stringClass;

    /**
     * Whether the analyzed method is "main" or not.
     */
    private boolean isMain;
    private ErrorHandler errorHandler;

    public MethodCheckVisitor(boolean isMain, ErrorHandler errorHandler, ClassNode stringClass) {
        this.isMain = isMain;
        this.errorHandler = errorHandler;
        this.stringClass = stringClass;
    }

    public Boolean visit(BlockStatementNode block) {
        boolean alwaysReturns = false;
        for (StatementNode stmt: block.getStatements()) {
            alwaysReturns |= stmt.accept(this);
        }
        return alwaysReturns;
    }

    public Boolean visit(IfStatementNode ifNode) {
        ifNode.getCondition().accept(this);
        boolean ifReturns = ifNode.getThenStatement().accept(this);

        boolean elseReturns = false;
        Optional<StatementNode> elseStmt = ifNode.getElseStatement();
        if (elseStmt.isPresent()) {
            elseReturns = elseStmt.get().accept(this);
        }
        return ifReturns && elseReturns;
    }

    public Boolean visit(WhileStatementNode whileNode) {
        whileNode.getCondition().accept(this);
        whileNode.getStatement().accept(this);

        // it is always possible that the loop is not executed
        return false;
    }

    public Boolean visit(ReturnStatementNode returnNode) {
        Optional<ExpressionNode> expr = returnNode.getResult();
        if (expr.isPresent()) {
            expr.get().accept(this);
        }
        return true;
    }

    public Boolean visit(LocalVariableDeclarationStatementNode lvdStmt) {
        Optional<ExpressionNode> expr = lvdStmt.getExpression();
        if (expr.isPresent()) {
            expr.get().accept(this);
        }
        return false;
    }

    public Boolean visit(ExpressionStatementNode stmt) {
        if (!isAssignmentOrMethodInvocation(stmt.getExpression())) {
            // only assignments or method invocations are valid expression statements
            errorHandler.receive(new SemanticError(stmt,
                "Statement needs to be either assignment or method invocation."));
        }

        stmt.getExpression().accept(this);
        return false;
    }

    public Boolean visit(BinaryExpressionNode expr) {
        if (expr.getOperator() == BinaryOperator.Assignment) {
            // check that the left hand side is an lvalue
            if (!isLValue(expr.getLeftSide())) {
                errorHandler.receive(new SemanticError(expr,
                    "Left side of assignment must be a variable, field or array element."));
            }
        }

        expr.getLeftSide().accept(this);
        expr.getRightSide().accept(this);
        return false;
    }

    public Boolean visit(UnaryExpressionNode expr) {
        expr.getExpression().accept(this);
        return false;
    }

    public Boolean visit(MethodInvocationExpressionNode expr) {
        Optional<ExpressionNode> object = expr.getObject();
        if (object.isPresent()) {
            object.get().accept(this);
        }
        for (ExpressionNode node: expr.getArguments()) {
            node.accept(this);
        }
        return false;
    }

    public Boolean visit(FieldAccessExpressionNode expr) {
        expr.getObject().accept(this);
        return false;
    }

    public Boolean visit(ArrayAccessExpressionNode expr) {
        expr.getObject().accept(this);
        expr.getExpression().accept(this);
        return false;
    }

    public Boolean visit(IdentifierExpressionNode expr) {
        if (isMain && expr.getDefinition().getKind() == DefinitionKind.Parameter) {
            errorHandler.receive(new SemanticError(expr,
                "accessing method parameters not allowed in main"));
        }
        return false;
    }

    public Boolean visit(ValueExpressionNode expr) {
        // nothing to check here
        return false;
    }

    public Boolean visit(ThisExpressionNode expr) {
        if (this.isMain) {
            errorHandler.receive(new SemanticError(expr,
                 "this-pointer not allowed in main"));
        }
        return false;
    }

    public Boolean visit(NewObjectExpressionNode expr) {
        return false;
    }

    public Boolean visit(NewArrayExpressionNode expr) {
        return false;
    }

    private boolean isLValue(ExpressionNode expr) {
        AstVisitor<Boolean> visitor = new AstVisitor<>() {
            public Boolean visit(BinaryExpressionNode n) { return false; }
            public Boolean visit(UnaryExpressionNode n) { return false; }
            public Boolean visit(MethodInvocationExpressionNode n) { return false; }
            public Boolean visit(FieldAccessExpressionNode n) { return true; }
            public Boolean visit(ArrayAccessExpressionNode n) { return true; }
            public Boolean visit(IdentifierExpressionNode n) { return true; }
            public Boolean visit(ValueExpressionNode n) { return false; }
            public Boolean visit(ThisExpressionNode n) { return false; }
            public Boolean visit(NewObjectExpressionNode n) { return false; }
            public Boolean visit(NewArrayExpressionNode n) { return false; }
        };
        return expr.accept(visitor);
    }

    private boolean isAssignmentOrMethodInvocation(ExpressionNode expr) {
        AstVisitor<Boolean> visitor = new AstVisitor<>() {
            public Boolean visit(BinaryExpressionNode n) {
                return n.getOperator() == BinaryOperator.Assignment;
            }
            public Boolean visit(MethodInvocationExpressionNode n) { return true; }

            public Boolean visit(UnaryExpressionNode n) { return false; }
            public Boolean visit(FieldAccessExpressionNode n) { return false; }
            public Boolean visit(ArrayAccessExpressionNode n) { return false; }
            public Boolean visit(IdentifierExpressionNode n) { return false; }
            public Boolean visit(ValueExpressionNode n) { return false; }
            public Boolean visit(ThisExpressionNode n) { return false; }
            public Boolean visit(NewObjectExpressionNode n) { return false; }
            public Boolean visit(NewArrayExpressionNode n) { return false; }
        };
        return expr.accept(visitor);
    }
}
