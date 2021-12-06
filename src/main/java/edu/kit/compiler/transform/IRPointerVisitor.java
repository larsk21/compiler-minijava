package edu.kit.compiler.transform;

import edu.kit.compiler.data.AstVisitor;
import edu.kit.compiler.data.ast_nodes.ExpressionNode;
import edu.kit.compiler.data.ast_nodes.ExpressionNode.ArrayAccessExpressionNode;
import edu.kit.compiler.data.ast_nodes.ExpressionNode.FieldAccessExpressionNode;
import edu.kit.compiler.data.ast_nodes.ExpressionNode.IdentifierExpressionNode;
import edu.kit.compiler.semantic.DefinitionKind;
import firm.ArrayType;
import firm.Construction;
import firm.Entity;
import firm.Type;
import firm.nodes.Node;

/**
 * Returns a node that represents a pointer to the result of the expression.
 * Only applicable for FieldAccess, ArrayAccess and Identifiers that are class members.
 */
public class IRPointerVisitor implements AstVisitor<Node> {
    private TransformContext context;

    public IRPointerVisitor(TransformContext context) {
        this.context = context;
    }

    public Node visit(FieldAccessExpressionNode expr) { 
        ExpressionNode lhs = expr.getObject();
        Node left = handleExpression(lhs);
        int className = lhs.getResultType().getIdentifier().get();
        return handleFieldAccess(left, className, expr.getName());
    }

    public Node visit(IdentifierExpressionNode expr) {
        if (expr.getDefinition().getKind() == DefinitionKind.Field) {
            int className = context.getClassNode().getName();
            return handleFieldAccess(context.getThisNode(), className, expr.getIdentifier());
        } else {
            throw new UnsupportedOperationException("not recognized kind " + expr.getDefinition().getKind());
        }
    }

    public Node visit(ArrayAccessExpressionNode expr) {
        Construction con = context.getConstruction();
        Type elementType = context.getTypeMapper().getDataType(expr.getResultType());
        Node leftPtr = handleExpression(expr.getObject());
        Node index = handleExpression(expr.getExpression());
        return con.newSel(leftPtr, index, new ArrayType(elementType, 0));
    }

    private Node handleFieldAccess(Node left, int className, int fieldName) {
        Construction con = context.getConstruction();
        Entity field = context.getTypeMapper().getClassEntry(className).getField(fieldName);
        return con.newMember(left, field);
    }

    private Node handleExpression(ExpressionNode expr) {
        return expr.accept(new IRExpressionVisitor(context));
    }
}
