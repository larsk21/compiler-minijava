package edu.kit.compiler.semantic;

import java.util.Optional;

import edu.kit.compiler.data.AstObject;
import edu.kit.compiler.data.AstVisitor;
import edu.kit.compiler.data.DataType;
import edu.kit.compiler.data.DataType.DataTypeClass;
import edu.kit.compiler.data.Operator.*;
import edu.kit.compiler.data.ast_nodes.*;
import edu.kit.compiler.data.ast_nodes.ClassNode.*;
import edu.kit.compiler.data.ast_nodes.ExpressionNode.*;
import edu.kit.compiler.data.ast_nodes.MethodNode.*;
import edu.kit.compiler.data.ast_nodes.StatementNode.*;
import edu.kit.compiler.lexer.StringTable;
import edu.kit.compiler.semantic.NamespaceMapper.ClassNamespace;

/**
 * This AST visitor performs name analysis and type checking on method bodies.
 * 
 * Preconditions:
 * - class namespaces can be found by name (= identifier)
 * - each class namespace contains all fields and methods with name and type
 * - builtin definitions (classes, fields, methods) are available
 * 
 * Postconditions:
 * - all used variables reference their declaration
 * - every field, method, statement and expression is correctly typed
 * - no variable is declared twice inside the same scope
 * - every expression node contains a valid result type
 * - static methods contain no reference to this
 * - void is not used as a field, parameter or local variable type, in a new
 * object or new array expression
 * - hasError is set in all nodes where an error occured
 * - user defined data types of fields and methods (incl. parameters) are valid
 * - integer literal values are valid integer values (32-bit signed)
 * 
 * Not checked:
 * - all code paths return a value
 * - left side of an assignment is an l-value
 */
public class DetailedNameTypeAstVisitor implements AstVisitor<DataType> {

    public static final DataType VoidType = new DataType(DataTypeClass.Void);

    public DetailedNameTypeAstVisitor(NamespaceMapper namespaceMapper, StringTable stringTable) {
        this.namespaceMapper = namespaceMapper;
        this.stringTable = stringTable;
        this.symboltable = new SymbolTable();

        currentClassNamespace = Optional.empty();
        expectedReturnType = null;
    }

    private NamespaceMapper namespaceMapper;
    private StringTable stringTable;
    private SymbolTable symboltable;

    private Optional<ClassNamespace> currentClassNamespace;
    private DataType expectedReturnType;

    private boolean isValidDataType(DataType type) {
        switch (type.getType()) {
        case Array:
            return isValidDataType(type.getInnerType().get());
        case Boolean:
            return true;
        case Int:
            return true;
        case UserDefined:
            return namespaceMapper.containsClassNamespace(type.getIdentifier().get());
        case Void:
            return false;
        default:
            throw new IllegalArgumentException("unsupported data type");
        }
    }

    private void semanticError(AstObject object, String message, Object... args) {
        object.setHasError(true);

        // TODO: collect semantic errors in list
        throw new SemanticException(
            String.format(message, args),
            object
        );
    }

    public DataType visit(ProgramNode programNode) {
        for (ClassNode _class : programNode.getClasses()) {
            _class.accept(this);
        }

        return VoidType;
    }

    public DataType visit(ClassNode classNode) {
        currentClassNamespace = Optional.empty();
        symboltable.enterScope();

        for (ClassNodeField field : classNode.getFields()) {
            if (isValidDataType(field.getType())) {
                symboltable.insert(field);
            } else if (field.getType().getType() == DataTypeClass.Void) {
                semanticError(field, "void type is not allowed for a field");
            } else {
                semanticError(field, "unknown reference type %s", field.getType().getRepresentation(stringTable));
            }
        }

        for (MethodNode methodNode : classNode.getStaticMethods()) {
            methodNode.accept(this);
        }

        currentClassNamespace = Optional.of(namespaceMapper.getClassNamespace(classNode));

        for (MethodNode methodNode : classNode.getDynamicMethods()) {
            methodNode.accept(this);
        }

        symboltable.leaveScope();
        currentClassNamespace = Optional.empty();
        return VoidType;
    }

    private DataType visitMethodNode(MethodNode methodNode) {
        symboltable.enterScope();

        if (!isValidDataType(methodNode.getType()) && methodNode.getType().getType() != DataTypeClass.Void) {
            semanticError(methodNode, "unknown reference type %s", methodNode.getType().getRepresentation(stringTable));
        }

        for (MethodNodeParameter parameter : methodNode.getParameters()) {
            if (isValidDataType(parameter.getType())) {
                symboltable.insert(parameter);
            } else if (parameter.getType().getType() == DataTypeClass.Void) {
                semanticError(parameter, "void type is not allowed for a method parameter");
            } else {
                semanticError(parameter, "unknown reference type %s", parameter.getType().getRepresentation(stringTable));
            }
        }

        expectedReturnType = methodNode.getType();
        methodNode.getStatementBlock().accept(this);
        expectedReturnType = null;

        symboltable.leaveScope();
        return VoidType;
    }

    public DataType visit(StaticMethodNode staticMethodNode) {
        return visitMethodNode(staticMethodNode);
    }

    public DataType visit(DynamicMethodNode dynamicMethodNode) {
        return visitMethodNode(dynamicMethodNode);
    }

    public DataType visit(BlockStatementNode blockStatementNode) {
        symboltable.enterScope();

        for (StatementNode statement : blockStatementNode.getStatements()) {
            statement.accept(this);
        }

        symboltable.leaveScope();
        return VoidType;
    }

    public DataType visit(LocalVariableDeclarationStatementNode localVariableDeclarationStatementNode) {
        DataType leftSideType = localVariableDeclarationStatementNode.getType();
        if (!isValidDataType(leftSideType)) {
            if (leftSideType.getType() == DataTypeClass.Void) {
                semanticError(localVariableDeclarationStatementNode, "void type is not allowed in a local variable declaration");
            } else {
                semanticError(localVariableDeclarationStatementNode, "unknown reference type %s", leftSideType.getRepresentation(stringTable));
            }
        }

        if (symboltable.isDefinedInCurrentScope(localVariableDeclarationStatementNode.getName())) {
            semanticError(localVariableDeclarationStatementNode, "variable is already defined in current scope");
        }

        if (localVariableDeclarationStatementNode.getExpression().isPresent()) {
            DataType rightSideType = localVariableDeclarationStatementNode.getExpression().get().accept(this);

            if (!leftSideType.isCompatibleTo(rightSideType)) {
                semanticError(localVariableDeclarationStatementNode,
                    "invalid assigment, variable type is %s while expression type is %s",
                    leftSideType.getRepresentation(stringTable),
                    rightSideType.getRepresentation(stringTable)
                );
            }
        }

        symboltable.insert(localVariableDeclarationStatementNode);

        return VoidType;
    }

    public DataType visit(IfStatementNode ifStatementNode) {
        DataType conditionType = ifStatementNode.getCondition().accept(this);
        if (conditionType.getType() != DataTypeClass.Boolean) {
            semanticError(ifStatementNode, "if statement condition must be boolean");
        }

        ifStatementNode.getThenStatement().accept(this);
        if (ifStatementNode.getElseStatement().isPresent()) {
            ifStatementNode.getElseStatement().get().accept(this);
        }

        return VoidType;
    }

    public DataType visit(WhileStatementNode whileStatementNode) {
        DataType conditionType = whileStatementNode.getCondition().accept(this);
        if (conditionType.getType() != DataTypeClass.Boolean) {
            semanticError(whileStatementNode, "while statement condition must be boolean");
        }

        whileStatementNode.getStatement().accept(this);

        return VoidType;
    }

    public DataType visit(ReturnStatementNode returnStatementNode) {
        if (returnStatementNode.getResult().isPresent()) {
            DataType resultType = returnStatementNode.getResult().get().accept(this);

            if (!resultType.isCompatibleTo(expectedReturnType)) {
                semanticError(returnStatementNode,
                    "expression type %s does not match required return type %s",
                    resultType.getRepresentation(stringTable),
                    expectedReturnType.getRepresentation(stringTable)
                );
            }
        } else if (expectedReturnType.getType() != DataTypeClass.Void) {
            semanticError(returnStatementNode, "method requires a return value of type %s", expectedReturnType.getRepresentation(stringTable));
        }

        return VoidType;
    }

    public DataType visit(ExpressionStatementNode expressionStatementNode) {
        expressionStatementNode.getExpression().accept(this);

        return VoidType;
    }

    public DataType visit(BinaryExpressionNode binaryExpressionNode) {
        DataType leftSideType = binaryExpressionNode.getLeftSide().accept(this);
        DataType rightSideType = binaryExpressionNode.getRightSide().accept(this);

        BinaryOperator operator = binaryExpressionNode.getOperator();

        DataType resultType;
        switch (operator) {
            case Assignment:
                if (!leftSideType.isCompatibleTo(rightSideType)) {
                    semanticError(binaryExpressionNode, "the two sides of an assignment must have compatible types");
                }

                resultType = leftSideType;
                break;
            case Equal:
            case NotEqual:
                if (!leftSideType.isCompatibleTo(rightSideType)) {
                    semanticError(binaryExpressionNode, "the two arguments of %s must have compatible types", operator);
                }

                resultType = operator.getResultType();
                break;
            default:
                DataType expectedArgumentType = operator.getExpectedArgumentType();

                if (!leftSideType.isCompatibleTo(expectedArgumentType)) {
                    semanticError(binaryExpressionNode,
                        "wrong argument type %s, %s operator requires %s",
                        leftSideType.getRepresentation(stringTable),
                        operator,
                        expectedArgumentType.getRepresentation(stringTable)
                    );
                }
                if (!rightSideType.isCompatibleTo(expectedArgumentType)) {
                    semanticError(binaryExpressionNode,
                        "wrong argument type %s, %s operator requires %s",
                        leftSideType.getRepresentation(stringTable),
                        operator,
                        expectedArgumentType.getRepresentation(stringTable)
                    );
                }

                resultType = operator.getResultType();
                break;
        }

        binaryExpressionNode.setResultType(resultType);
        return resultType;
    }

    public DataType visit(UnaryExpressionNode unaryExpressionNode) {
        UnaryOperator operator = unaryExpressionNode.getOperator();

        DataType argumentType = unaryExpressionNode.getExpression().accept(this);
        DataType expectedArgumentType = operator.getExpectedArgumentType();

        if (!argumentType.isCompatibleTo(expectedArgumentType)) {
            semanticError(unaryExpressionNode,
                "wrong argument type %s, %s operator requires %s",
                argumentType.getRepresentation(stringTable),
                operator,
                expectedArgumentType.getRepresentation(stringTable)
            );
        }

        unaryExpressionNode.setResultType(operator.getResultType());
        return operator.getResultType();
    }

    public DataType visit(MethodInvocationExpressionNode methodInvocationExpressionNode) {
        ClassNamespace namespace;
        if (methodInvocationExpressionNode.getObject().isPresent()) {
            DataType objectType = methodInvocationExpressionNode.getObject().get().accept(this);

            if (objectType.getType() != DataTypeClass.UserDefined) {
                semanticError(methodInvocationExpressionNode, "method invocation is only allowed on reference type expressions");
            }

            namespace = namespaceMapper.getClassNamespace(objectType.getIdentifier().get());
        } else {
            if (!currentClassNamespace.isPresent()) {
                semanticError(methodInvocationExpressionNode, "method calls without object are not allowed in static methods");
            }

            namespace = currentClassNamespace.get();
        }

        if (!namespace.getDynamicMethods().containsKey(methodInvocationExpressionNode.getName())) {
            semanticError(methodInvocationExpressionNode, "unknown method");
        }
        MethodNode definition = namespace.getDynamicMethods().get(methodInvocationExpressionNode.getName());

        methodInvocationExpressionNode.setDefinition(definition);

        if (methodInvocationExpressionNode.getArguments().size() != definition.getParameters().size()) {
            semanticError(methodInvocationExpressionNode, "wrong number of arguments");
        }

        for (int i = 0; i < definition.getParameters().size(); i++) {
            DataType expectedArgumentType = definition.getParameters().get(i).getType();
            DataType actualArgumentType = methodInvocationExpressionNode.getArguments().get(i).accept(this);

            if (!actualArgumentType.isCompatibleTo(expectedArgumentType)) {
                semanticError(methodInvocationExpressionNode,
                    "argument %d of type %s does not match expected type %s",
                    i,
                    actualArgumentType.getRepresentation(stringTable),
                    expectedArgumentType.getRepresentation(stringTable)
                );
            }
        }

        methodInvocationExpressionNode.setResultType(definition.getType());
        return definition.getType();
    }

    public DataType visit(FieldAccessExpressionNode fieldAccessExpressionNode) {
        ClassNamespace namespace;
        DataType objectType = fieldAccessExpressionNode.getObject().accept(this);

        if (objectType.getType() != DataTypeClass.UserDefined) {
            semanticError(fieldAccessExpressionNode, "field access is only allowed on reference type expressions");
        }

        namespace = namespaceMapper.getClassNamespace(objectType.getIdentifier().get());

        if (!namespace.getClassSymbols().containsKey(fieldAccessExpressionNode.getName())) {
            semanticError(fieldAccessExpressionNode, "unknown class field");
        }
        ClassNodeField definition = namespace.getClassSymbols().get(fieldAccessExpressionNode.getName());

        fieldAccessExpressionNode.setDefinition(definition);

        fieldAccessExpressionNode.setResultType(definition.getType());
        return definition.getType();
    }

    public DataType visit(ArrayAccessExpressionNode arrayAccessExpressionNode) {
        DataType objectType = arrayAccessExpressionNode.getObject().accept(this);
        if (objectType.getType() != DataTypeClass.Array) {
            semanticError(arrayAccessExpressionNode, "%s is not an array type", objectType.getRepresentation(stringTable));
        }

        DataType expressionType = arrayAccessExpressionNode.getExpression().accept(this);
        if (expressionType.getType() != DataTypeClass.Int) {
            semanticError(arrayAccessExpressionNode, "array index must be of type int");
        }

        DataType resultType = objectType.getInnerType().get();

        arrayAccessExpressionNode.setResultType(resultType);
        return resultType;
    }

    public DataType visit(IdentifierExpressionNode identifierExpressionNode) {
        if (!symboltable.isDefined(identifierExpressionNode.getIdentifier())) {
            semanticError(identifierExpressionNode, "`%s` cannot be resolved", stringTable.retrieve(identifierExpressionNode.getIdentifier()));
        }
        Definition definition = symboltable.lookup(identifierExpressionNode.getIdentifier());

        identifierExpressionNode.setDefinition(definition);

        identifierExpressionNode.setResultType(definition.getType());
        return definition.getType();
    }

    public DataType visit(ThisExpressionNode thisExpressionNode) {
        if (!currentClassNamespace.isPresent()) {
            semanticError(thisExpressionNode, "`this` is not allowed in static contexts");
        }

        ClassNode classNode = currentClassNamespace.get().getClassNodeRef();
        DataType resultType = new DataType(classNode.getName());

        thisExpressionNode.setDefinition(classNode);

        thisExpressionNode.setResultType(resultType);
        return resultType;
    }

    public DataType visit(ValueExpressionNode valueExpressionNode) {
        DataType resultType;
        switch (valueExpressionNode.getValueType()) {
        case False:
            resultType = new DataType(DataTypeClass.Boolean);
            break;
        case IntegerLiteral:
            if (!valueExpressionNode.getLiteralValue().get().isIntValue()) {
                semanticError(valueExpressionNode, "%s is not a 32-bit integer value", valueExpressionNode.getLiteralValue().get());
            }

            resultType = new DataType(DataTypeClass.Int);
            break;
        case Null:
            resultType = new DataType(DataTypeClass.Any);
            break;
        case True:
            resultType = new DataType(DataTypeClass.Boolean);
            break;
        default:
            throw new IllegalArgumentException("unsupported value expression type");
        }

        valueExpressionNode.setResultType(resultType);
        return resultType;
    }

    public DataType visit(NewObjectExpressionNode newObjectExpressionNode) {
        DataType objectType = new DataType(newObjectExpressionNode.getTypeName());
        if (!isValidDataType(objectType)) {
            semanticError(newObjectExpressionNode, "unknown reference type %s", stringTable.retrieve(objectType.getIdentifier().get()));
        }

        newObjectExpressionNode.setResultType(objectType);
        return objectType;
    }

    public DataType visit(NewArrayExpressionNode newArrayExpressionNode) {
        DataType elementType = newArrayExpressionNode.getElementType();
        if (!isValidDataType(elementType)) {
            if (elementType.getType() == DataTypeClass.Void) {
                semanticError(newArrayExpressionNode, "void type is not allowed in a new array expression");
            } else {
                semanticError(newArrayExpressionNode, "unknown reference type %s", stringTable.retrieve(elementType.getIdentifier().get()));
            }
        }

        DataType expressionType = newArrayExpressionNode.getLength().accept(this);
        if (expressionType.getType() != DataTypeClass.Int) {
            semanticError(newArrayExpressionNode, "array length must be of type int");
        }

        DataType arrayType = elementType;
        for (int i = 0; i < newArrayExpressionNode.getDimensions(); i++) {
            arrayType = new DataType(arrayType);
        }

        newArrayExpressionNode.setResultType(arrayType);
        return arrayType;
    }

}
