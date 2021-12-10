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

    // The Supplier is needed to generate lhs and rhs in the correct order
    private Node handleAssignment(ExpressionNode left, Supplier<Node> right) {
        Type type = context.getTypeMapper().getDataType(left.getResultType());
        Node node;

        // ? maybe refactor as Visitor to get rid of instance of
        if (left instanceof IdentifierExpressionNode) {
            var id = (ExpressionNode.IdentifierExpressionNode) left;
            switch (id.getDefinition().getKind()) {
                case LocalVariable, Parameter -> {
                    var variable = context.getVariableIndex(id.getIdentifier());
                    getConstruction().setVariable(variable, (node = right.get()));
                }
                case Field -> storeToAddress(id.accept(pointerVisitor), (node = right.get()), type);
                default -> throw new IllegalStateException();
            }
        } else {
            storeToAddress(left.accept(pointerVisitor), (node = right.get()), type);
        }

        return node;
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
        Mode mode = context.getTypeMapper().getMode(binaryExpressionNode.getLeftSide().getResultType());

        return switch (binaryExpressionNode.getOperator()) {
            case Assignment -> throw new IllegalStateException();
            case Modulo -> {
                Node mem = getConstruction().getCurrentMem();
                Node mod = getConstruction().newMod(mem, lhs, rhs, binding_ircons.op_pin_state.op_pin_state_pinned);
                Node projRes = getConstruction().newProj(mod, mode, Mod.pnRes);
                Node projMem = getConstruction().newProj(mod, Mode.getM(), Mod.pnM);

                getConstruction().setCurrentMem(projMem);
                yield  projRes;
            }
            case Division -> {
                Node mem = getConstruction().getCurrentMem();
                Node lhs64 = getConstruction().newConv(lhs, Mode.getLs());
                Node rhs64 = getConstruction().newConv(rhs, Mode.getLs());
                Node div = getConstruction().newDiv(mem, lhs64, rhs64, binding_ircons.op_pin_state.op_pin_state_pinned);
                Node projRes64 = getConstruction().newProj(div, Mode.getLs(), Div.pnRes);
                Node projRes = getConstruction().newConv(projRes64, Mode.getIs());
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

        TypedEntity<MethodType> methodEntry;
        if (method.isStandardLibraryMethod()) {
            var stdMethod = (StandardLibraryMethodNode)method;
            methodEntry = StandardLibraryEntities.INSTANCE.getEntity(stdMethod.getMethod());
        } else {
            // lookup method in the according class
            int className;
            if (methodInvocationExpressionNode.getObject().isEmpty()) {
                className = context.getClassNode().getName();
            } else {
                className = methodInvocationExpressionNode.getObject().get().getResultType().getIdentifier().get();
            }

            methodEntry = context.getTypeMapper().getClassEntry(className).getMethod(method);
        }

        var methodAddress = getConstruction().newAddress(methodEntry.getEntity());
        var methodType = methodEntry.getType();

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
            case Null -> getConstruction().newConst(0, Mode.getP());
            case False, True -> IRBooleanExpressions.asValue(context, valueExpressionNode);
            case IntegerLiteral -> {
                var value = valueExpressionNode.getLiteralValue().get().intValue();
                yield getConstruction().newConst(value, Mode.getIs());
            }
        };
    }

    @Override
    public Node visit(NewObjectExpressionNode newObjectExpressionNode) {
        ClassEntry classNode = context.getTypeMapper().getClassEntry(newObjectExpressionNode.getTypeName());
        return callCalloc(1, classNode.getClassType().getSize(), classNode.getPointerType());
    }

    @Override
    public Node visit(NewArrayExpressionNode newArrayExpressionNode) {
        DataType innerType = newArrayExpressionNode.getResultType().getInnerType().get();
        Type arrayType = context.getTypeMapper().getDataType(newArrayExpressionNode.getResultType());
        Type elementType = context.getTypeMapper().getDataType(innerType);
        Node arrayLength = newArrayExpressionNode.getLength().accept(this);
        return callCalloc(arrayLength, elementType.getSize(), arrayType);
    }

    private Node callCalloc(Node nmemb, int size, Type type) {
        var entry = StandardLibraryEntities.INSTANCE.getCalloc();
        Node address = getConstruction().newAddress(entry.getEntity());
        MethodType methodType = entry.getType();

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
        return context.getTypeMapper().getMode(type);
    }
}
