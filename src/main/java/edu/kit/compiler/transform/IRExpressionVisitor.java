package edu.kit.compiler.transform;

import edu.kit.compiler.data.AstVisitor;
import edu.kit.compiler.data.DataType;
import edu.kit.compiler.data.ast_nodes.ExpressionNode;
import edu.kit.compiler.data.ast_nodes.ExpressionNode.*;
import edu.kit.compiler.data.ast_nodes.MethodNode;
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

    final AstVisitor<Node> lValueVisitor = new AstVisitor<>() {
        @Override
        public Node visit(ExpressionNode.FieldAccessExpressionNode fieldAccessExpressionNode) {
            return fieldAccessExpressionNode.getObject().accept(pointerVisitor);
        }

        @Override
        public Node visit(ExpressionNode.ArrayAccessExpressionNode arrayAccessExpressionNode) {
            return arrayAccessExpressionNode.accept(pointerVisitor);
        }

        @Override
        public Node visit(ExpressionNode.IdentifierExpressionNode identifierExpressionNode) {
            return null;
        }

        @Override
        public Node visit(ThisExpressionNode thisExpressionNode) {
            return context.createThisNode();
        }
    };

    @Override
    public Node visit(BinaryExpressionNode binaryExpressionNode) {
        Node lhs = binaryExpressionNode.getLeftSide().accept(lValueVisitor);
        Node rhs = binaryExpressionNode.getRightSide().accept(this);
        Type t = context.getTypeMapper().getDataType(binaryExpressionNode.getLeftSide().getResultType());
        Mode m = t.getMode();

        switch (binaryExpressionNode.getOperator()) {
            // handle stuff with our boolean visitor
            case Equal, LessThanOrEqual,
                    LogicalAnd, GreaterThanOrEqual, GreaterThan,
                    LogicalOr, NotEqual, LessThan -> {
                return IRBooleanExpressions.asValue(context, binaryExpressionNode);
            }
            case Assignment -> {
                if (binaryExpressionNode.getLeftSide() instanceof IdentifierExpressionNode) {
                    ExpressionNode.IdentifierExpressionNode id = (ExpressionNode.IdentifierExpressionNode) binaryExpressionNode.getLeftSide();
                    switch (id.getDefinition().getKind()) {
                        case LocalVariable -> {
                            getConstruction().setVariable(context.getVariableIndex(id.getIdentifier()), rhs);
                        }
                        case Parameter -> {
                            getConstruction().setVariable(context.getVariableIndex(id.getIdentifier()), rhs);
                        }
                        case Field -> {
                            Node ptr = id.accept(pointerVisitor);
                            storeToAddress(ptr, rhs, t);
                        }
                    }
                } else {
                    storeToAddress(lhs, rhs, t);
                }
                return rhs;
            }
            case Modulo -> {
                Node mem = getConstruction().getCurrentMem();
                Node mod = getConstruction().newMod(mem, lhs, rhs, binding_ircons.op_pin_state.op_pin_state_pinned);
                Node projRes = getConstruction().newProj(mod, m, Mod.pnRes);
                Node projMem = getConstruction().newProj(mod, Mode.getM(), Mod.pnM);

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

                getConstruction().setCurrentMem(projMem);
                return projRes;
            }
            case Subtraction -> {
                return getConstruction().newSub(lhs, rhs);
            }
            case Multiplication -> {
                return getConstruction().newMul(lhs, rhs);
            }
            default -> throw new UnsupportedOperationException("not supported " + binaryExpressionNode.getOperator().toString());
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
        Type t = context.getTypeMapper().getDataType(methodInvocationExpressionNode.getResultType());
        Mode m = t.getMode();
        Node objectAddress;
        if (methodInvocationExpressionNode.getObject().isEmpty()) {
            // assuming call on this
            objectAddress = context.createThisNode();
        } else {
            objectAddress = methodInvocationExpressionNode.getObject().get().accept(this);
        }
        // collect arguments in list with this pointer
        Node[] arguments = new Node[methodInvocationExpressionNode.getArguments().size() + 1];
        arguments[0] = objectAddress;
        for (int i = 1; i < methodInvocationExpressionNode.getArguments().size() + 1; i++) {
            Node n = methodInvocationExpressionNode.getArguments().get(i - 1).accept(this);
            arguments[i] = n;
        }

        MethodNode method = methodInvocationExpressionNode.getDefinition();
        int className;
        if (methodInvocationExpressionNode.getObject().isEmpty()) {
            className = context.getClassNode().getName();
        } else {
            className = methodInvocationExpressionNode.getObject().get().getResultType().getIdentifier().get();
        }
        Entity methodEntity = context.getTypeMapper().getClassEntry(className).getMethod(method.getName());
        Node methodAddress = getConstruction().newAddress(methodEntity);

        t = context.getTypeMapper().getClassEntry(className).getMethodParamTypes().get(method.getName());
        Node call = getConstruction().newCall(mem, methodAddress, arguments, t);
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

    private void storeToAddress(Node address, Node value, Type t) {
        Node mem = getConstruction().getCurrentMem();
        Node store = getConstruction().newStore(mem, address, value, t);

        Node newMem = getConstruction().newProj(store, Mode.getM(), Load.pnM);
        getConstruction().setCurrentMem(newMem);
    }

    @Override
    public Node visit(IdentifierExpressionNode identifierExpressionNode) {
        Construction con = context.getConstruction();
        switch (identifierExpressionNode.getDefinition().getKind()) {
            case Field -> {
                Node fieldAddress = identifierExpressionNode.accept(pointerVisitor);
                Mode resultType = context.getTypeMapper().getDataType(identifierExpressionNode.getResultType()).getMode();
                return loadFromAddress(fieldAddress, resultType);
            }
            case Parameter -> {
                return context.createParamNode(identifierExpressionNode.getIdentifier());
            }
            case LocalVariable -> {
                Mode mode = getMode(identifierExpressionNode.getResultType());
                return con.getVariable(context.getVariableIndex(identifierExpressionNode.getIdentifier()), mode);
            }
            default -> {
                throw new UnsupportedOperationException();
            }
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
        return getConstruction().newConst(0, Mode.getP());
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
        Node[] params = new Node[]{nmemb, sizeNode};
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
