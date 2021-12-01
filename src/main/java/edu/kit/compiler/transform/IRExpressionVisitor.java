package edu.kit.compiler.transform;

import edu.kit.compiler.data.AstVisitor;
import edu.kit.compiler.data.DataType;
import edu.kit.compiler.data.ast_nodes.ExpressionNode;
import edu.kit.compiler.semantic.DefinitionKind;
import firm.Construction;
import firm.Mode;
import firm.TargetValue;
import firm.bindings.binding_ircons;
import firm.nodes.Call;
import firm.nodes.Div;
import firm.nodes.Mod;
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
        Node lhs = binaryExpressionNode.getLeftSide().accept(this);
        Node rhs = binaryExpressionNode.getRightSide().accept(this);

        Mode m = context.getTypeMapper().getDataType(binaryExpressionNode.getLeftSide().getResultType()).getMode();

        switch (binaryExpressionNode.getOperator()) {
            // handle stuff with our boolean visitor
            case Assignment, Equal, LessThanOrEqual,
                    LogicalAnd, GreaterThanOrEqual, GreaterThan,
                    LogicalOr, NotEqual, LessThan -> {
                return IRBooleanExpressions.asValue(context, binaryExpressionNode);
            }
            case Modulo -> {
                Node mem = getConstruction().getCurrentMem();
                Node mod = getConstruction().newMod(mem, lhs, rhs, binding_ircons.op_pin_state.op_pin_state_pinned);
                Node projRes = getConstruction().newProj(mod, m, Mod.pnRes);
                Node projMem = getConstruction().newProj(mod, m, Mod.pnM);

                // TODO: map control flow exceptions too?
                getConstruction().setCurrentMem(projMem);
                return projRes;
            }
            case Addition -> {
                return getConstruction().newAdd(lhs, rhs);
            }
            case Division -> {
                Node mem = getConstruction().getCurrentMem();
                Node div = getConstruction().newDiv(mem, lhs, rhs, binding_ircons.op_pin_state.op_pin_state_pinned);
                Node projRes = getConstruction().newProj(div, m, Div.pnRes);
                Node projMem = getConstruction().newProj(div, m, Div.pnM);

                // TODO: map control flow exceptions too?
                getConstruction().setCurrentMem(projMem);
                return projRes;
            }
            case Subtraction -> {
                return getConstruction().newSub(lhs, rhs);
            }
            case Multiplication -> {
                return getConstruction().newMul(lhs, rhs);
            }
            default -> {
                throw new UnsupportedOperationException("not supported " + binaryExpressionNode.getOperator().toString());
            }
        }
    }

    @Override
    public Node visit(ExpressionNode.UnaryExpressionNode unaryExpressionNode) {
        switch (unaryExpressionNode.getOperator()) {
            case LogicalNegation -> {
                return IRBooleanExpressions.asValue(context, unaryExpressionNode);
            }
            case ArithmeticNegation -> {
                ExpressionNode innerNode = unaryExpressionNode.getExpression();
                Node res = innerNode.accept(this);
                // negate res
                return getConstruction().newMinus(res);
            }
            default -> {
                throw new UnsupportedOperationException("not supported " + unaryExpressionNode.getOperator());
            }
        }
    }

    @Override
    public Node visit(ExpressionNode.MethodInvocationExpressionNode methodInvocationExpressionNode) {
        Node mem = getConstruction().getCurrentMem();
        TypeMapper.ClassEntry currentClass = context.getTypeMapper().getClassEntry(context.getClassNode());

        Mode m = context.getTypeMapper().getDataType(methodInvocationExpressionNode.getResultType()).getMode();

        Node addr = getConstruction().newAddress(currentClass.getMethod(methodInvocationExpressionNode.getDefinition()));

        // collect arguments in list
        Node[] arguments = new Node[methodInvocationExpressionNode.getArguments().size()];
        for (int i = 0; i < methodInvocationExpressionNode.getArguments().size(); i++) {
            Node n = methodInvocationExpressionNode.getArguments().get(i).accept(this);
            arguments[i] = n;
        }

        Node call = getConstruction().newCall(mem, addr, arguments, m.getType());
        Node tResult = getConstruction().newProj(call, m, Call.pnTResult);
        Node res = getConstruction().newProj(tResult, m, 0);
        Node projMem = getConstruction().newProj(call, m, Call.pnM);
        getConstruction().setCurrentMem(projMem);

        return res;
    }

    @Override
    public Node visit(ExpressionNode.FieldAccessExpressionNode fieldAccessExpressionNode) {
        Node mem = getConstruction().getCurrentMem();

        Node n = fieldAccessExpressionNode.getObject().accept(this);
        return (Node) null;
    }

    @Override
    public Node visit(ExpressionNode.ArrayAccessExpressionNode arrayAccessExpressionNode) {
        return getConstruction().newConst(Mode.getIs().getNull());
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
        return getConstruction().newConst(Mode.getIs().getNull());
    }

    @Override
    public Node visit(ExpressionNode.ValueExpressionNode valueExpressionNode) {
        // determine type of value expression and value and set the expression
        // handle accordingly

        // the new node that should be craeted here
        Node n = null;
        switch (valueExpressionNode.getValueType()) {
            case Null -> {
                n = handleNullExpressionNode(valueExpressionNode);
            }
            case False, True -> {
                throw new UnsupportedOperationException();
            }
            case IntegerLiteral -> {
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
        return context.getConstruction();
    }

    private Mode getMode(DataType type) {
        return context.getTypeMapper().getDataType(type).getMode();
    }
}
