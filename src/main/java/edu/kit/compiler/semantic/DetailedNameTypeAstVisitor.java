package edu.kit.compiler.semantic;

import java.util.Optional;

import edu.kit.compiler.data.AstVisitor;
import edu.kit.compiler.data.DataType;
import edu.kit.compiler.data.CompilerException.SourceLocation;
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
 * - user defined data types of fields and methods (incl. parameters) are valid
 * - builtin definitions (classes, fields, methods) are available
 * 
 * Postconditions:
 * - all used variables reference their declaration
 * - every method, statement and expression is correctly typed
 * - no variable is declared twice inside the same scope
 * - every expression node contains a valid result type
 * - static methods contain no reference to this
 * - void is not used as local variable type, in a new object or a new array
 * expression
 * 
 * Not checked:
 * - all code paths return a value
 * - left side of an assignment is an l-value
 */
public class DetailedNameTypeAstVisitor implements AstVisitor<DataType> {

    public static final DataType VoidType = new DataType(DataTypeClass.Void);

    public DetailedNameTypeAstVisitor(NamespaceMapper namespaceMapper, StringTable stringTable) {
        this.namespaceMapper = namespaceMapper;
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
            symboltable.insert(field);
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

        for (MethodNodeParameter parameter : methodNode.getParameters()) {
            symboltable.insert(parameter);
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
                throw new SemanticException(
                    "void type is not allowed in a local variable declaration",
                    new SourceLocation(localVariableDeclarationStatementNode.getLine(), localVariableDeclarationStatementNode.getColumn())
                );
            } else {
                throw new SemanticException(String.format(
                    "unknown reference type %s",
                    leftSideType.getRepresentation(stringTable)
                ), new SourceLocation(localVariableDeclarationStatementNode.getLine(), localVariableDeclarationStatementNode.getColumn()));
            }
        }

        if (symboltable.isDefinedInCurrentScope(localVariableDeclarationStatementNode.getName())) {
            throw new SemanticException(
                "variable is already defined in current scope",
                new SourceLocation(localVariableDeclarationStatementNode.getLine(), localVariableDeclarationStatementNode.getColumn())
            );
        }

        if (localVariableDeclarationStatementNode.getExpression().isPresent()) {
            DataType rightSideType = localVariableDeclarationStatementNode.getExpression().get().accept(this);

            if (!leftSideType.isCompatibleTo(rightSideType)) {
                throw new SemanticException(String.format(
                    "invalid assigment, variable type is %s while expression type is %s",
                    leftSideType.getRepresentation(stringTable),
                    rightSideType.getRepresentation(stringTable)
                ), new SourceLocation(localVariableDeclarationStatementNode.getLine(), localVariableDeclarationStatementNode.getColumn()));
            }
        }

        symboltable.insert(localVariableDeclarationStatementNode);

        return VoidType;
    }

    public DataType visit(IfStatementNode ifStatementNode) {
        DataType conditionType = ifStatementNode.getCondition().accept(this);
        if (conditionType.getType() != DataTypeClass.Boolean) {
            throw new SemanticException(
                "if statement condition must be boolean",
                new SourceLocation(ifStatementNode.getLine(), ifStatementNode.getColumn())
            );
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
            throw new SemanticException(
                "while statement condition must be boolean",
                new SourceLocation(whileStatementNode.getLine(), whileStatementNode.getColumn())
            );
        }

        whileStatementNode.getStatement().accept(this);

        return VoidType;
    }

    public DataType visit(ReturnStatementNode returnStatementNode) {
        if (returnStatementNode.getResult().isPresent()) {
            DataType resultType = returnStatementNode.getResult().get().accept(this);

            if (!resultType.isCompatibleTo(expectedReturnType)) {
                throw new SemanticException(String.format(
                    "expression type %s does not match required return type %s",
                    resultType.getRepresentation(stringTable),
                    expectedReturnType.getRepresentation(stringTable)
                ), new SourceLocation(returnStatementNode.getLine(), returnStatementNode.getColumn()));
            }
        } else if (expectedReturnType.getType() != DataTypeClass.Void) {
                throw new SemanticException(String.format(
                    "method requires a return value of type %s",
                    expectedReturnType.getRepresentation(stringTable)
                ), new SourceLocation(returnStatementNode.getLine(), returnStatementNode.getColumn()));
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
                    throw new SemanticException(
                        "the two sides of an assignment must have compatible types",
                        new SourceLocation(binaryExpressionNode.getLine(), binaryExpressionNode.getColumn())
                    );
                }

                resultType = leftSideType;
                break;
            case Equal:
            case NotEqual:
                if (!leftSideType.isCompatibleTo(rightSideType)) {
                    throw new SemanticException(String.format(
                        "the two arguments of %s must have compatible types",
                        operator
                    ), new SourceLocation(binaryExpressionNode.getLine(), binaryExpressionNode.getColumn()));
                }

                resultType = operator.getResultType();
                break;
            default:
                DataType expectedArgumentType = operator.getExpectedArgumentType();

                if (!leftSideType.isCompatibleTo(expectedArgumentType)) {
                    throw new SemanticException(String.format(
                        "wrong argument type %s, %s operator requires %s",
                        leftSideType.getRepresentation(stringTable),
                        operator,
                        expectedArgumentType.getRepresentation(stringTable)
                    ), new SourceLocation(binaryExpressionNode.getLeftSide().getLine(), binaryExpressionNode.getLeftSide().getColumn()));
                }
                if (!rightSideType.isCompatibleTo(expectedArgumentType)) {
                    throw new SemanticException(String.format(
                        "wrong argument type %s, %s operator requires %s",
                        leftSideType.getRepresentation(stringTable),
                        operator,
                        expectedArgumentType.getRepresentation(stringTable)
                    ), new SourceLocation(binaryExpressionNode.getRightSide().getLine(), binaryExpressionNode.getRightSide().getColumn()));
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
            throw new SemanticException(String.format(
                "wrong argument type %s, %s operator requires %s",
                argumentType.getRepresentation(stringTable),
                operator,
                expectedArgumentType.getRepresentation(stringTable)
            ), new SourceLocation(unaryExpressionNode.getExpression().getLine(), unaryExpressionNode.getExpression().getColumn()));
        }

        unaryExpressionNode.setResultType(operator.getResultType());
        return operator.getResultType();
    }

    public DataType visit(MethodInvocationExpressionNode methodInvocationExpressionNode) {
        ClassNamespace namespace;
        if (methodInvocationExpressionNode.getObject().isPresent()) {
            DataType objectType = methodInvocationExpressionNode.getObject().get().accept(this);

            if (objectType.getType() != DataTypeClass.UserDefined) {
                throw new SemanticException(
                    "method invocation is only allowed on reference type expressions",
                    new SourceLocation(methodInvocationExpressionNode.getLine(), methodInvocationExpressionNode.getColumn())
                );
            }

            namespace = namespaceMapper.getClassNamespace(objectType.getIdentifier().get());
        } else {
            if (!currentClassNamespace.isPresent()) {
                throw new SemanticException(
                    "method calls without object are not allowed in static methods",
                    new SourceLocation(methodInvocationExpressionNode.getLine(), methodInvocationExpressionNode.getColumn())
                );
            }

            namespace = currentClassNamespace.get();
        }

        if (!namespace.getDynamicMethods().containsKey(methodInvocationExpressionNode.getName())) {
            throw new SemanticException(
                "unknown method",
                new SourceLocation(methodInvocationExpressionNode.getLine(), methodInvocationExpressionNode.getColumn())
            );
        }
        MethodNode definition = namespace.getDynamicMethods().get(methodInvocationExpressionNode.getName());

        methodInvocationExpressionNode.setDefinition(definition);

        if (methodInvocationExpressionNode.getArguments().size() != definition.getParameters().size()) {
            throw new SemanticException(
                "wrong number of arguments",
                new SourceLocation(methodInvocationExpressionNode.getLine(), methodInvocationExpressionNode.getColumn())
            );
        }

        for (int i = 0; i < definition.getParameters().size(); i++) {
            DataType expectedArgumentType = definition.getParameters().get(i).getType();
            DataType actualArgumentType = methodInvocationExpressionNode.getArguments().get(i).accept(this);

            if (!actualArgumentType.isCompatibleTo(expectedArgumentType)) {
                throw new SemanticException(String.format(
                    "argument %d of type %s does not match expected type %s",
                    i,
                    actualArgumentType.getRepresentation(stringTable),
                    expectedArgumentType.getRepresentation(stringTable)
                ), new SourceLocation(methodInvocationExpressionNode.getLine(), methodInvocationExpressionNode.getColumn()));
            }
        }

        methodInvocationExpressionNode.setResultType(definition.getType());
        return definition.getType();
    }

    public DataType visit(FieldAccessExpressionNode fieldAccessExpressionNode) {
        ClassNamespace namespace;
        DataType objectType = fieldAccessExpressionNode.getObject().accept(this);

        if (objectType.getType() != DataTypeClass.UserDefined) {
            throw new SemanticException(
                "field access is only allowed on reference type expressions",
                new SourceLocation(fieldAccessExpressionNode.getLine(), fieldAccessExpressionNode.getColumn())
            );
        }

        namespace = namespaceMapper.getClassNamespace(objectType.getIdentifier().get());

        if (!namespace.getClassSymbols().containsKey(fieldAccessExpressionNode.getName())) {
            throw new SemanticException(
                "unknown class field",
                new SourceLocation(fieldAccessExpressionNode.getLine(), fieldAccessExpressionNode.getColumn())
            );
        }
        ClassNodeField definition = namespace.getClassSymbols().get(fieldAccessExpressionNode.getName());

        fieldAccessExpressionNode.setDefinition(definition);

        fieldAccessExpressionNode.setResultType(definition.getType());
        return definition.getType();
    }

    public DataType visit(ArrayAccessExpressionNode arrayAccessExpressionNode) {
        DataType objectType = arrayAccessExpressionNode.getObject().accept(this);
        if (objectType.getType() != DataTypeClass.Array) {
            throw new SemanticException(String.format(
                "%s is not an array type",
                objectType.getRepresentation(stringTable)
            ), new SourceLocation(arrayAccessExpressionNode.getLine(), arrayAccessExpressionNode.getColumn()));
        }

        DataType expressionType = arrayAccessExpressionNode.getExpression().accept(this);
        if (expressionType.getType() != DataTypeClass.Int) {
            throw new SemanticException(
                "array index must be of type int",
                new SourceLocation(arrayAccessExpressionNode.getLine(), arrayAccessExpressionNode.getColumn())
            );
        }

        DataType resultType = objectType.getInnerType().get();

        arrayAccessExpressionNode.setResultType(resultType);
        return resultType;
    }

    public DataType visit(IdentifierExpressionNode identifierExpressionNode) {
        if (!symboltable.isDefined(identifierExpressionNode.getIdentifier())) {
            throw new SemanticException(String.format(
                "`%s` cannot be resolved",
                stringTable.retrieve(identifierExpressionNode.getIdentifier())
            ), new SourceLocation(identifierExpressionNode.getLine(), identifierExpressionNode.getColumn()));
        }
        Definition definition = symboltable.lookup(identifierExpressionNode.getIdentifier());

        identifierExpressionNode.setDefinition(definition);

        identifierExpressionNode.setResultType(definition.getType());
        return definition.getType();
    }

    public DataType visit(ThisExpressionNode thisExpressionNode) {
        if (!currentClassNamespace.isPresent()) {
            throw new SemanticException(
                "`this` is not allowed in static contexts",
                new SourceLocation(thisExpressionNode.getLine(), thisExpressionNode.getColumn())
            );
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
            if (objectType.getType() == DataTypeClass.Void) {
                throw new SemanticException(
                    "void type is not allowed in a new object expression",
                    new SourceLocation(newObjectExpressionNode.getLine(), newObjectExpressionNode.getColumn())
                );
            } else {
                throw new SemanticException(String.format(
                    "unknown reference type %s",
                    stringTable.retrieve(objectType.getIdentifier().get())
                ), new SourceLocation(newObjectExpressionNode.getLine(), newObjectExpressionNode.getColumn()));
            }
        }

        newObjectExpressionNode.setResultType(objectType);
        return objectType;
    }

    public DataType visit(NewArrayExpressionNode newArrayExpressionNode) {
        DataType elementType = newArrayExpressionNode.getElementType();
        if (!isValidDataType(elementType)) {
            if (elementType.getType() == DataTypeClass.Void) {
                throw new SemanticException(
                    "void type is not allowed in a new array expression",
                    new SourceLocation(newArrayExpressionNode.getLine(), newArrayExpressionNode.getColumn())
                );
            } else {
                throw new SemanticException(String.format(
                    "unknown reference type %s",
                    stringTable.retrieve(elementType.getIdentifier().get())
                ), new SourceLocation(newArrayExpressionNode.getLine(), newArrayExpressionNode.getColumn()));
            }
        }

        DataType expressionType = newArrayExpressionNode.getLength().accept(this);
        if (expressionType.getType() == DataTypeClass.Int) {
            throw new SemanticException(
                "array length must be of type int",
                new SourceLocation(newArrayExpressionNode.getLine(), newArrayExpressionNode.getColumn())
            );
        }

        DataType arrayType = elementType;
        for (int i = 0; i < newArrayExpressionNode.getDimensions(); i++) {
            arrayType = new DataType(arrayType);
        }

        newArrayExpressionNode.setResultType(arrayType);
        return arrayType;
    }

}
