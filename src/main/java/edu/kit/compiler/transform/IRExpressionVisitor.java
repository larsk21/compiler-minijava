package edu.kit.compiler.transform;

import edu.kit.compiler.data.AstVisitor;
import edu.kit.compiler.data.DataType;
import edu.kit.compiler.data.ast_nodes.ExpressionNode;
import edu.kit.compiler.data.ast_nodes.StatementNode;
import firm.Construction;
import firm.Mode;
import firm.TargetValue;
import firm.nodes.Node;

import javax.swing.plaf.basic.BasicTreeUI;

import static edu.kit.compiler.data.ast_nodes.ExpressionNode.ValueExpressionType.IntegerLiteral;
import static edu.kit.compiler.data.ast_nodes.ExpressionNode.ValueExpressionType.Null;

/**
 * return firm nodes for our ast nodes
 */
public class IRExpressionVisitor implements AstVisitor<Node> {

    private final TypeMapper typeMapper;
    private final TransformContext transformContext;

    public IRExpressionVisitor(TypeMapper typeMapper, TransformContext transformContext) {
        this.typeMapper = typeMapper;
        this.transformContext = transformContext;
    }

    @Override
    public Node visit(StatementNode.LocalVariableDeclarationStatementNode statementNode) {
        Construction con = getConstruction();
        Node assignedVal;
        if (statementNode.getExpression().isPresent()) {
            assignedVal = statementNode.getExpression().get().accept(this);
        } else {
            // zero-initialize the variable
            // Note: for debugging, me might want to make no assignment
            assignedVal = con.newConst(0, getMode(statementNode.getType()));
        }
        con.setVariable(transformContext.getVariableIndex(statementNode.getName()), assignedVal);
        con.getGraph().keepAlive(assignedVal);
        return (Node) null;
    }

    @Override
    public Node visit(StatementNode.WhileStatementNode statementNode) {
        ExpressionNode ex = statementNode.getCondition();
        Node n = ex.accept(this);
        statementNode.getStatement().accept(this);

        return (Node) null;
    }

    @Override
    public Node visit(StatementNode.BlockStatementNode blockStatementNode) {
        // visit all sub statements here
        for (var statement : blockStatementNode.getStatements()) {
            statement.accept(this);
        }
        return (Node) null;
    }

    @Override
    public Node visit(StatementNode.IfStatementNode statementNode) {
        Node n = statementNode.getCondition().accept(this);
        return (Node) null;
    }

    @Override
    public Node visit(StatementNode.ExpressionStatementNode statementNode) {
        Node n = statementNode.getExpression().accept(this);
        return (Node) null;
    }

    @Override
    public Node visit(StatementNode.ReturnStatementNode statementNode) {
        statementNode.getResult().ifPresent(ex -> {
            ex.accept(this);
        });
        return (Node) null;
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
        return null;
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
            }
            case False: {
                throw new UnsupportedOperationException();
            }
            case True: {
                throw new UnsupportedOperationException();
            }
            case IntegerLiteral: {
                n = handleIntegerExpressionNode(valueExpressionNode);
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
        return transformContext.getConstruction();
    }

    private Mode getMode(DataType type) {
        return transformContext.getTypeMapper().getDataType(type).getMode();
    }
}
