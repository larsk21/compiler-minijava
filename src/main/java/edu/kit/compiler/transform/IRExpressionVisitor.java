package edu.kit.compiler.transform;

import java.util.function.Supplier;

import edu.kit.compiler.data.AstVisitor;
import edu.kit.compiler.data.DataType;
import edu.kit.compiler.data.Operator.BinaryOperator;
import edu.kit.compiler.data.ast_nodes.ExpressionNode;
import edu.kit.compiler.data.ast_nodes.ExpressionNode.*;
import edu.kit.compiler.data.ast_nodes.MethodNode;
import edu.kit.compiler.data.ast_nodes.MethodNode.StandardLibraryMethodNode;
import edu.kit.compiler.transform.TypeMapper.ClassEntry;
import firm.*;
import firm.bindings.binding_ircons;
import firm.nodes.*;

/**
 * return firm nodes for our ast nodes
 */
public class IRExpressionVisitor implements AstVisitor<Node> {
    private static final DataType boolType = new DataType(DataType.DataTypeClass.Boolean);
    private final TransformContext context;
    private final IRPointerVisitor pointerVisitor;


    public IRExpressionVisitor(TransformContext context) {
        this.context = context;
        this.pointerVisitor = new IRPointerVisitor(context);
    }

    public Node handleAssignment(ExpressionNode left, Node right) {
        return handleAssignment(left, () -> right);
    }

    public Node handleAssignment(ExpressionNode left, ExpressionNode right) {
        return handleAssignment(left, () -> right.accept(this));
    }

    // The Supplier is need to generate lhs and rhs in the correct order
    private Node handleAssignment(ExpressionNode left, Supplier<Node> right) {
        Type type = context.getTypeMapper().getDataType(left.getResultType());
        Node rhs;

        // ? refactor as Visitor to get rid of instance of
        if (left instanceof IdentifierExpressionNode) {
            var id = (ExpressionNode.IdentifierExpressionNode) left;
            switch (id.getDefinition().getKind()) {
                case LocalVariable, Parameter -> {
                    var variable = context.getVariableIndex(id.getIdentifier());
                    getConstruction().setVariable(variable, (rhs = right.get()));
                }
                case Field -> storeToAddress(id.accept(pointerVisitor), (rhs = right.get()), type);
                default -> throw new IllegalStateException();
            }
        } else {
            var lhs = left.accept(pointerVisitor);
            rhs = right.get();
            storeToAddress(lhs, rhs, type);
        }

        return rhs;
    }

    @Override
    public Node visit(BinaryExpressionNode binaryExpressionNode) {
        if (binaryExpressionNode.getResultType().equals(boolType)) {
            return IRBooleanExpressions.asValue(context, binaryExpressionNode);
        }
 
        // Special case for assignment to prevent lhs from being created twice
        if (binaryExpressionNode.getOperator() == BinaryOperator.Assignment) {
            return handleAssignment(binaryExpressionNode.getLeftSide(), binaryExpressionNode.getRightSide());
        }

        Node lhs = binaryExpressionNode.getLeftSide().accept(this);
        Node rhs = binaryExpressionNode.getRightSide().accept(this);
        Type t = context.getTypeMapper().getDataType(binaryExpressionNode.getLeftSide().getResultType());
        Mode m = t.getMode();

        return switch (binaryExpressionNode.getOperator()) {
            case Assignment -> throw new IllegalStateException();
            case Modulo -> {
                Node mem = getConstruction().getCurrentMem();
                Node mod = getConstruction().newMod(mem, lhs, rhs, binding_ircons.op_pin_state.op_pin_state_pinned);
                Node projRes = getConstruction().newProj(mod, m, Mod.pnRes);
                Node projMem = getConstruction().newProj(mod, Mode.getM(), Mod.pnM);

                getConstruction().setCurrentMem(projMem);
                yield  projRes;
            }
            case Division -> {
                Node mem = getConstruction().getCurrentMem();
                Node div = getConstruction().newDiv(mem, lhs, rhs, binding_ircons.op_pin_state.op_pin_state_pinned);
                Node projRes = getConstruction().newProj(div, m, Div.pnRes);
                Node projMem = getConstruction().newProj(div, Mode.getM(), Div.pnM);

                getConstruction().setCurrentMem(projMem);
                yield projRes;
            }
            case Addition -> getConstruction().newAdd(lhs, rhs);
            case Subtraction -> getConstruction().newSub(lhs, rhs);
            case Multiplication -> getConstruction().newMul(lhs, rhs);
            default -> throw new UnsupportedOperationException();
        };
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
        MethodNode method = methodInvocationExpressionNode.getDefinition();
        int argOffset = method.isStandardLibraryMethod() ? 0 : 1;
        // collect arguments in list with this pointer
        Node[] arguments = new Node[methodInvocationExpressionNode.getArguments().size() + argOffset];
        if (!method.isStandardLibraryMethod()) {
            Node objectAddress;
            if (methodInvocationExpressionNode.getObject().isEmpty()) {
                // assuming call on this
                objectAddress = context.getThisNode();
            } else {
                objectAddress = methodInvocationExpressionNode.getObject().get().accept(this);
            }
            arguments[0] = objectAddress;
        }
        for (int i = argOffset; i < arguments.length; i++) {
            Node n = methodInvocationExpressionNode.getArguments().get(i - argOffset).accept(this);
            arguments[i] = n;
        }

        Node methodAddress;
        MethodType methodType;
        if (method.isStandardLibraryMethod()) {
            var stdMethod = (StandardLibraryMethodNode)method;
            var methodInstance = StandardLibraryEntities.INSTANCE.getEntity(stdMethod.getMethod());
            methodAddress = getConstruction().newAddress(methodInstance);
            methodType = (MethodType)methodInstance.getType();
        } else {
            // lookup method in the according class
            int className;
            if (methodInvocationExpressionNode.getObject().isEmpty()) {
                className = context.getClassNode().getName();
            } else {
                className = methodInvocationExpressionNode.getObject().get().getResultType().getIdentifier().get();
            }
            ClassEntry classEntry = context.getTypeMapper().getClassEntry(className);
            methodAddress = getConstruction().newAddress(classEntry.getMethod(method.getName()));
            methodType = classEntry.getMethodType(method.getName());
        }
        Node call = getConstruction().newCall(getConstruction().getCurrentMem(), methodAddress, arguments, methodType);
        Node projMem = getConstruction().newProj(call, Mode.getM(), Call.pnM);
        getConstruction().setCurrentMem(projMem);

        if (methodType.getNRess() > 0) {
            // for non-void methods: create Node for return type
            Node tResult = getConstruction().newProj(call, Mode.getT(), Call.pnTResult);
            return getConstruction().newProj(tResult, methodType.getResType(0).getMode(), 0);
        }
        // TODO: ugly, but is there a better solution?
        return null;
    }

    @Override
    public Node visit(FieldAccessExpressionNode fieldAccessExpressionNode) {
        Node fieldAddress = fieldAccessExpressionNode.accept(pointerVisitor);
        Mode resultType = getMode(fieldAccessExpressionNode.getResultType());
        return loadFromAddress(fieldAddress, resultType);
    }

    @Override
    public Node visit(ArrayAccessExpressionNode arrayAccessExpressionNode) {
        Node arrayAddress = arrayAccessExpressionNode.accept(pointerVisitor);
        Mode resultType = getMode(arrayAccessExpressionNode.getResultType());
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
        Mode mode = getMode(identifierExpressionNode.getResultType());
        switch (identifierExpressionNode.getDefinition().getKind()) {
            case Field -> {
                Node fieldAddress = identifierExpressionNode.accept(pointerVisitor);
                return loadFromAddress(fieldAddress, mode);
            }
            case Parameter, LocalVariable -> {
                return con.getVariable(context.getVariableIndex(identifierExpressionNode.getIdentifier()), mode);
            }
            default -> throw new UnsupportedOperationException();
        }
    }

    @Override
    public Node visit(ThisExpressionNode thisExpressionNode) {
        return context.getThisNode();
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
        ClassEntry classNode = context.getTypeMapper().getClassEntry(newObjectExpressionNode.getTypeName());
        return callCalloc(1, classNode.getClassType().getSize(), classNode.getPointerType());
    }

    @Override
    public Node visit(NewArrayExpressionNode newArrayExpressionNode) {
        Type elementType = context.getTypeMapper().getDataType(newArrayExpressionNode.getElementType());
        Type arrayType = context.getTypeMapper().getDataType(newArrayExpressionNode.getResultType());
        Node arrayLength = newArrayExpressionNode.getLength().accept(this);
        return callCalloc(arrayLength, elementType.getSize(), arrayType);
    }

    private Node callCalloc(Node nmemb, int size, Type type) {
        var entity = StandardLibraryEntities.INSTANCE.getCalloc();
        Node address = getConstruction().newAddress(entity);
        MethodType methodType = (MethodType)entity.getType();

        Node sizeNode = getConstruction().newConst(size, Mode.getIs());
        Node[] arguments = new Node[]{nmemb, sizeNode};
        Node call = getConstruction().newCall(getConstruction().getCurrentMem(), address, arguments, methodType);

        Node projMem = getConstruction().newProj(call, Mode.getM(), Call.pnM);
        getConstruction().setCurrentMem(projMem);

        Node tResult = getConstruction().newProj(call, Mode.getT(), Call.pnTResult);
        return getConstruction().newProj(tResult, type.getMode(), 0);
    }

    private Node callCalloc(int nmemb, int size, Type type) {
        Node nmembNode = getConstruction().newConst(nmemb, Mode.getIs());
        return callCalloc(nmembNode, size, type);
    }

    private Construction getConstruction() {
        return context.getConstruction();
    }

    private Mode getMode(DataType type) {
        return context.getTypeMapper().getDataType(type).getMode();
    }
}
