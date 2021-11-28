package edu.kit.compiler.transform;

import edu.kit.compiler.data.AstVisitor;
import edu.kit.compiler.data.DataType;
import edu.kit.compiler.data.ast_nodes.ExpressionNode;
import edu.kit.compiler.semantic.DefinitionKind;
import firm.Construction;
import firm.Mode;
import firm.TargetValue;
import firm.nodes.Node;

import static edu.kit.compiler.data.ast_nodes.ExpressionNode.ValueExpressionType.IntegerLiteral;
import static edu.kit.compiler.data.ast_nodes.ExpressionNode.ValueExpressionType.Null;

/**
 * return firm nodes for our ast nodes
 */
public class IRExpressionVisitor implements AstVisitor<Node> {
    private final TransformContext context;

    public IRExpressionVisitor(TransformContext context) {
        this.context = context;
    }

    @Override
    public Node visit(ExpressionNode.BinaryExpressionNode binaryExpressionNode) {
        return null;
    }

    @Override
    public Node visit(ExpressionNode.UnaryExpressionNode unaryExpressionNode) {
        return null;
    }

    @Override
    public Node visit(ExpressionNode.MethodInvocationExpressionNode methodInvocationExpressionNode) {
        return null;
    }

    @Override
    public Node visit(ExpressionNode.FieldAccessExpressionNode fieldAccessExpressionNode) {
        return null;
    }

    @Override
    public Node visit(ExpressionNode.ArrayAccessExpressionNode arrayAccessExpressionNode) {
        return null;
    }

    @Override
    public Node visit(ExpressionNode.IdentifierExpressionNode identifierExpressionNode) {
        Construction con = context.getConstruction();
        if (identifierExpressionNode.getDefinition().getKind() == DefinitionKind.LocalVariable) {
            Mode mode = getMode(identifierExpressionNode.getResultType());
            return con.getVariable(context.getVariableIndex(identifierExpressionNode.getIdentifier()), mode);
        } else if (identifierExpressionNode.getDefinition().getKind() == DefinitionKind.Parameter) {
            return context.createParamNode(identifierExpressionNode.getIdentifier());
        } else {
            throw new IllegalArgumentException();
        }
    }

    @Override
    public Node visit(ExpressionNode.ThisExpressionNode thisExpressionNode) {
        return null;
    }

    @Override
    public Node visit(ExpressionNode.ValueExpressionNode valueExpressionNode) {
        // determine type of value expression and value and set the expression
        // handle accordingly

        // the new node that should be craeted here
        Node n = null;
        switch (valueExpressionNode.getValueType()) {
            case Null: {
                n = handleNullExpressionNode(valueExpressionNode);
                break;
            }
            case False:
            case True: {
                throw new UnsupportedOperationException();
            }
            case IntegerLiteral: {
                n = handleIntegerExpressionNode(valueExpressionNode);
                break;
            }
        }

        // add node to current construction
        return n;
    }

    private Node handleIntegerExpressionNode(ExpressionNode.ValueExpressionNode valueExpressionNode) {
        if (valueExpressionNode.getValueType() != IntegerLiteral) {
            throw new IllegalArgumentException("can only evaluate integer expressions");
        }

        if (valueExpressionNode.getLiteralValue().isEmpty()) {
            throw new IllegalArgumentException("cannot get empty integer literal");
        }

        TargetValue tar_val = new TargetValue(valueExpressionNode.getLiteralValue().get().intValue(), Mode.getIs());
        return getConstruction().newConst(tar_val);
    }

    private Node handleNullExpressionNode(ExpressionNode.ValueExpressionNode valueExpressionNode) {
        if (valueExpressionNode.getValueType() != Null) {
            throw new IllegalArgumentException("can only evaluate null literal");
        }
        return getConstruction().newConst(Mode.getIs().getNull());
    }

    @Override
    public Node visit(ExpressionNode.NewObjectExpressionNode newObjectExpressionNode) {
        return null;
    }

    @Override
    public Node visit(ExpressionNode.NewArrayExpressionNode newArrayExpressionNode) {
        return null;
    }

    private Construction getConstruction() {
        return context.getConstruction();
    }

    private Mode getMode(DataType type) {
        return context.getTypeMapper().getDataType(type).getMode();
    }
}
