package edu.kit.compiler.transform;

import com.sun.jna.Pointer;
import edu.kit.compiler.data.AstVisitor;
import edu.kit.compiler.data.DataType;
import edu.kit.compiler.data.ast_nodes.ExpressionNode;
import edu.kit.compiler.data.ast_nodes.ExpressionNode.*;
import edu.kit.compiler.semantic.DefinitionKind;
import firm.*;
import firm.bindings.binding_ircons;
import firm.nodes.*;

/**
 * return firm nodes for our ast nodes
 */
public class IRExpressionVisitor implements AstVisitor<Node> {
    private final TransformContext context;
    private final IRPointerVisitor pointerVisitor;


    public IRExpressionVisitor(TransformContext context) {
        this.context = context;
        this.pointerVisitor = new IRPointerVisitor(context);
    }

    @Override
    public Node visit(BinaryExpressionNode binaryExpressionNode) {
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
                Node projMem = getConstruction().newProj(mod, Mode.getM(), Mod.pnM);

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
                Node projMem = getConstruction().newProj(div, Mode.getM(), Div.pnM);

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
    public Node visit(UnaryExpressionNode unaryExpressionNode) {
        switch (unaryExpressionNode.getOperator()) {
            case LogicalNegation -> {
                return IRBooleanExpressions.asValue(context, unaryExpressionNode);
            }
            case ArithmeticNegation -> {
                ExpressionNode innerNode = unaryExpressionNode.getExpression();
                Node res = innerNode.accept(this);
                return getConstruction().newMinus(res);
            }
            default -> throw new UnsupportedOperationException("not supported " + unaryExpressionNode.getOperator());
        }
    }

    @Override
    public Node visit(MethodInvocationExpressionNode methodInvocationExpressionNode) {
        Node mem = getConstruction().getCurrentMem();

        Mode m = context.getTypeMapper().getDataType(methodInvocationExpressionNode.getResultType()).getMode();
        if (methodInvocationExpressionNode.getObject().isEmpty()) {
            throw new IllegalArgumentException("object was null on method call");
        }
        Node objectAddress = methodInvocationExpressionNode.getObject().get().accept(pointerVisitor);
        // collect arguments in list with this pointer
        Node[] arguments = new Node[methodInvocationExpressionNode.getArguments().size() + 1];
        arguments[0] = objectAddress;
        for (int i = 1; i < methodInvocationExpressionNode.getArguments().size(); i++) {
            Node n = methodInvocationExpressionNode.getArguments().get(i).accept(this);
            arguments[i] = n;
        }
        int method = methodInvocationExpressionNode.getName();
        Node methodAddress = getConstruction().newAddress(IRVisitor.getMethodContexts().get(method).getMethodEntity());

        Node call = getConstruction().newCall(mem, methodAddress, arguments, m.getType());
        Node tResult = getConstruction().newProj(call, Mode.getT(), Call.pnTResult);
        Node res = getConstruction().newProj(tResult, m, 0);
        Node projMem = getConstruction().newProj(call, Mode.getM(), Call.pnM);
        getConstruction().setCurrentMem(projMem);

        return res;
    }

    @Override
    public Node visit(FieldAccessExpressionNode fieldAccessExpressionNode) {
        Node fieldAddress = fieldAccessExpressionNode.accept(pointerVisitor);
        Mode resultType = context.getTypeMapper().getDataType(fieldAccessExpressionNode.getResultType()).getMode();
        return loadFromAddress(fieldAddress, resultType);
    }

    @Override
    public Node visit(ArrayAccessExpressionNode arrayAccessExpressionNode) {
        Node arrayAddress = arrayAccessExpressionNode.accept(pointerVisitor);
        Mode resultType = context.getTypeMapper().getDataType(arrayAccessExpressionNode.getResultType()).getMode();
        return loadFromAddress(arrayAddress, resultType);

    }

    private Node loadFromAddress(Node address, Mode type) {
        Node mem = getConstruction().getCurrentMem();
        Node load = getConstruction().newLoad(mem, address, type);

        Node resMem = getConstruction().newProj(load, Mode.getM(), Load.pnM);
        Node res = getConstruction().newProj(load, type, Load.pnRes);
        getConstruction().setCurrentMem(resMem);
        return res;
    }

    @Override
    public Node visit(IdentifierExpressionNode identifierExpressionNode) {
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
    public Node visit(ThisExpressionNode thisExpressionNode) {
        return context.createThisNode();
    }

    @Override
    public Node visit(ValueExpressionNode valueExpressionNode) {
        // determine type of value expression and value and set the expression
        return switch (valueExpressionNode.getValueType()) {
            case Null -> handleNullExpressionNode();
            case False, True -> IRBooleanExpressions.asValue(context, valueExpressionNode);
            case IntegerLiteral -> handleIntegerExpressionNode(valueExpressionNode);
        };
    }

    private Node handleIntegerExpressionNode(ValueExpressionNode valueExpressionNode) {
        TargetValue tar_val = new TargetValue(valueExpressionNode.getLiteralValue().get().intValue(), Mode.getIs());
        return getConstruction().newConst(tar_val);
    }

    private Node handleNullExpressionNode() {
        return getConstruction().newAddress(new Entity(Pointer.NULL));
    }

    @Override
    public Node visit(NewObjectExpressionNode newObjectExpressionNode) {
        TypeMapper.ClassEntry classNode = context.getTypeMapper().getClassEntry(newObjectExpressionNode.getTypeName());
        int size = classNode.getSize();
        return callCalloc(1, size, classNode.getClassType());
    }

    @Override
    public Node visit(NewArrayExpressionNode newArrayExpressionNode) {
        Type dataType = context.getTypeMapper().getDataType(newArrayExpressionNode.getElementType());
        Node arrayLength = newArrayExpressionNode.getLength().accept(this);
        return callCalloc(arrayLength, dataType.getSize(), dataType);
    }

    private Node callCalloc(Node nmemb, int size, Type t) {
        Node mem = getConstruction().getCurrentMem();
        Node callocAddress = Lower.getCallocNode(getConstruction());

        Node sizeNode = getConstruction().newConst(size, Mode.getIs());
        Node[] params = new Node[] {nmemb, sizeNode};
        Node call = getConstruction().newCall(mem, callocAddress, params, t);

        Node callMem = getConstruction().newProj(call, Mode.getM(), Call.pnM);
        getConstruction().setCurrentMem(callMem);

        Node tResult = getConstruction().newProj(call, Mode.getT(), Call.pnTResult);
        return getConstruction().newProj(tResult, t.getMode(), 0);
    }

    private Node callCalloc(int nmemb, int size, Type t) {
        Node nmembNode = getConstruction().newConst(nmemb, Mode.getIs());
        return callCalloc(nmembNode, size, t);
    }

    private Construction getConstruction() {
        return context.getConstruction();
    }

    private Mode getMode(DataType type) {
        return context.getTypeMapper().getDataType(type).getMode();
    }
}
