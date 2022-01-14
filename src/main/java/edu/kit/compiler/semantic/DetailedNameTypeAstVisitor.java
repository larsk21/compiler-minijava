package edu.kit.compiler.semantic;

import java.util.Optional;
import java.util.function.BiConsumer;

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
 * - all used variables reference their declaration *
 * - every field, method, statement and expression is correctly typed
 * - no variable is declared twice inside the same method
 * - every expression node contains a valid result type *
 * - static methods contain no reference to this
 * - void is not used as a field, parameter or local variable type, in a new
 * object or new array expression
 * - the error flag is set in all nodes where an error occured
 * - user defined data types of fields and methods (incl. parameters) are valid
 * - integer literal values are valid integer values (32-bit signed)
 * - calls to the standard library are identified
 * 
 * * unless the error flag is set for the node
 * 
 * Not checked:
 * - all code paths return a value
 * - left side of an assignment is an l-value
 */
public class DetailedNameTypeAstVisitor implements AstVisitor<Optional<DataType>> {

    public DetailedNameTypeAstVisitor(NamespaceMapper namespaceMapper, StringTable stringTable, ErrorHandler errorHandler) {
        this.namespaceMapper = namespaceMapper;
        this.stringTable = stringTable;
        this.symboltable = new SymbolTable();
        this.standardLibrary = StandardLibrary.create(stringTable);

        this.errorHandler = errorHandler;

        currentClassNamespace = Optional.empty();
        expectedReturnType = Optional.empty();
    }

    private NamespaceMapper namespaceMapper;
    private StringTable stringTable;
    private SymbolTable symboltable;
    private StandardLibrary standardLibrary;

    private ErrorHandler errorHandler;

    private Optional<ClassNamespace> currentClassNamespace;
    private Optional<DataType> expectedReturnType;

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

    private <T, U> void ifBothPresent(Optional<T> a_, Optional<U> b_, BiConsumer<T, U> task) {
        a_.ifPresent(a -> b_.ifPresent(b -> task.accept(a, b)));
    }

    private <T> Optional<T> semanticError(AstObject object, String message, Object... args) {
        object.setHasError(true);
        errorHandler.receive(new SemanticError(object, String.format(message, args)));

        return Optional.empty();
    }

    public Optional<DataType> visit(ProgramNode programNode) {
        for (ClassNode _class : programNode.getClasses()) {
            _class.accept(this);
        }

        return Optional.empty();
    }

    public Optional<DataType> visit(ClassNode classNode) {
        symboltable.enterScope();

        for (ClassNodeField field : classNode.getFields()) {
            if (isValidDataType(field.getType())) {
                if (!field.isHasError()) {
                    symboltable.insert(field);
                }
            } else {
                if (field.getType().getType() == DataTypeClass.Void) {
                    semanticError(field, "void type is not allowed for a field");
                } else {
                    semanticError(field, "unknown reference type %s", field.getType().getRepresentation(stringTable));
                }
            }
        }

        currentClassNamespace = Optional.empty();

        for (MethodNode methodNode : classNode.getStaticMethods()) {
            methodNode.accept(this);
        }

        currentClassNamespace = Optional.of(namespaceMapper.getClassNamespace(classNode));

        for (MethodNode methodNode : classNode.getDynamicMethods()) {
            methodNode.accept(this);
        }

        currentClassNamespace = Optional.empty();

        symboltable.leaveScope();
        return Optional.empty();
    }

    private Optional<DataType> visitMethodNode(MethodNode methodNode) {
        symboltable.enterScope();

        if (isValidDataType(methodNode.getType()) || methodNode.getType().getType() == DataTypeClass.Void) {
            expectedReturnType = Optional.of(methodNode.getType());
        } else {
            semanticError(methodNode, "unknown reference type %s", methodNode.getType().getRepresentation(stringTable));
            expectedReturnType = Optional.empty();
        }

        for (MethodNodeParameter parameter : methodNode.getParameters()) {
            if (isValidDataType(parameter.getType())) {
                if (!parameter.isHasError()) {
                    symboltable.insert(parameter);
                }
            } else {
                if (parameter.getType().getType() == DataTypeClass.Void) {
                    semanticError(parameter, "void type is not allowed for a method parameter");
                } else {
                    semanticError(parameter, "unknown reference type %s", parameter.getType().getRepresentation(stringTable));
                }
            }
        }

        methodNode.getStatementBlock().accept(this);

        expectedReturnType = Optional.empty();
        symboltable.leaveScope();
        return Optional.empty();
    }

    public Optional<DataType> visit(StaticMethodNode staticMethodNode) {
        return visitMethodNode(staticMethodNode);
    }

    public Optional<DataType> visit(DynamicMethodNode dynamicMethodNode) {
        return visitMethodNode(dynamicMethodNode);
    }

    public Optional<DataType> visit(BlockStatementNode blockStatementNode) {
        symboltable.enterScope();

        for (StatementNode statement : blockStatementNode.getStatements()) {
            statement.accept(this);
        }

        symboltable.leaveScope();
        return Optional.empty();
    }

    public Optional<DataType> visit(LocalVariableDeclarationStatementNode localVariableDeclarationStatementNode) {
        Optional<DataType> leftSideType_ = Optional.of(localVariableDeclarationStatementNode.getType());
        leftSideType_ = leftSideType_.flatMap(leftSideType -> {
            if (isValidDataType(leftSideType)) {
                return Optional.of(leftSideType);
            } else {
                if (leftSideType.getType() == DataTypeClass.Void) {
                    return semanticError(localVariableDeclarationStatementNode, "void type is not allowed in a local variable declaration");
                } else {
                    return semanticError(localVariableDeclarationStatementNode, "unknown reference type %s", leftSideType.getRepresentation(stringTable));
                }
            }
        });

        if (symboltable.isDefined(localVariableDeclarationStatementNode.getName())) {
            Definition definition = symboltable.lookup(localVariableDeclarationStatementNode.getName());

            if (definition.getKind() == DefinitionKind.Parameter || definition.getKind() == DefinitionKind.LocalVariable) {
                semanticError(localVariableDeclarationStatementNode, "variable is already defined in current scope");
            } else {
                symboltable.insert(localVariableDeclarationStatementNode);
            }
        } else {
            symboltable.insert(localVariableDeclarationStatementNode);
        }

        if (localVariableDeclarationStatementNode.getExpression().isPresent()) {
            Optional<DataType> rightSideType_ = localVariableDeclarationStatementNode.getExpression().get().accept(this);

            ifBothPresent(leftSideType_, rightSideType_, (leftSideType, rightSideType) -> {
                if (!leftSideType.isCompatibleTo(rightSideType)) {
                    semanticError(localVariableDeclarationStatementNode,
                        "invalid assigment, variable type is %s while expression type is %s",
                        leftSideType.getRepresentation(stringTable),
                        rightSideType.getRepresentation(stringTable)
                    );
                }
            });
        }

        return Optional.empty();
    }

    public Optional<DataType> visit(IfStatementNode ifStatementNode) {
        Optional<DataType> conditionType_ = ifStatementNode.getCondition().accept(this);
        conditionType_.ifPresent(conditionType -> {
            if (conditionType.getType() != DataTypeClass.Boolean) {
                semanticError(ifStatementNode, "if statement condition must be boolean");
            }
        });

        ifStatementNode.getThenStatement().accept(this);
        if (ifStatementNode.getElseStatement().isPresent()) {
            ifStatementNode.getElseStatement().get().accept(this);
        }

        return Optional.empty();
    }

    public Optional<DataType> visit(WhileStatementNode whileStatementNode) {
        Optional<DataType> conditionType_ = whileStatementNode.getCondition().accept(this);
        conditionType_.ifPresent(conditionType -> {
            if (conditionType.getType() != DataTypeClass.Boolean) {
                semanticError(whileStatementNode, "while statement condition must be boolean");
            }
        });

        whileStatementNode.getStatement().accept(this);

        return Optional.empty();
    }

    public Optional<DataType> visit(ReturnStatementNode returnStatementNode) {
        if (returnStatementNode.getResult().isPresent()) {
            Optional<DataType> resultType_ = returnStatementNode.getResult().get().accept(this);
            resultType_.ifPresent(resultType -> {
                if (expectedReturnType.isPresent() && !resultType.isCompatibleTo(expectedReturnType.get())) {
                    semanticError(returnStatementNode,
                        "expression type %s does not match required return type %s",
                        resultType.getRepresentation(stringTable),
                        expectedReturnType.get().getRepresentation(stringTable)
                    );
                }
            });
        } else if (expectedReturnType.isPresent() && expectedReturnType.get().getType() != DataTypeClass.Void) {
            semanticError(returnStatementNode, "method requires a return value of type %s", expectedReturnType.get().getRepresentation(stringTable));
        }

        return Optional.empty();
    }

    public Optional<DataType> visit(ExpressionStatementNode expressionStatementNode) {
        expressionStatementNode.getExpression().accept(this);

        return Optional.empty();
    }

    private Optional<DataType> applyResultType(ExpressionNode expressionNode, Optional<DataType> resultType_) {
        resultType_.ifPresentOrElse(resultType -> {
            expressionNode.setResultType(resultType);
        }, () -> {
            expressionNode.setHasError(true);
        });
        return resultType_;
    }

    private <ReferenceExpressionNode extends ExpressionNode & Reference> void applyDefinition(ReferenceExpressionNode referenceNode, Optional<? extends Definition> definition_) {
        definition_.ifPresentOrElse(definition -> {
            referenceNode.setDefinition(definition);
        }, () -> {
            referenceNode.setHasError(true);
        });
    }

    public Optional<DataType> visit(BinaryExpressionNode binaryExpressionNode) {
        Optional<DataType> leftSideType_ = binaryExpressionNode.getLeftSide().accept(this);
        Optional<DataType> rightSideType_ = binaryExpressionNode.getRightSide().accept(this);

        BinaryOperator operator = binaryExpressionNode.getOperator();

        Optional<DataType> resultType_;
        switch (operator) {
            case Assignment:
                ifBothPresent(leftSideType_, rightSideType_, (leftSideType, rightSideType) -> {
                    if (!leftSideType.isCompatibleTo(rightSideType)) {
                        semanticError(binaryExpressionNode, "the two sides of an assignment must have compatible types");
                    }
                });

                resultType_ = leftSideType_;
                break;
            case Equal:
            case NotEqual:
                ifBothPresent(leftSideType_, rightSideType_, (leftSideType, rightSideType) -> {
                    if (!leftSideType.isCompatibleTo(rightSideType)) {
                        semanticError(binaryExpressionNode, "the two arguments of %s must have compatible types", operator);
                    }
                });

                resultType_ = Optional.of(operator.getResultType());
                break;
            default:
                DataType expectedArgumentType = operator.getExpectedArgumentType();

                leftSideType_.ifPresent(leftSideType -> {
                    if (!leftSideType.isCompatibleTo(expectedArgumentType)) {
                        semanticError(binaryExpressionNode,
                            "wrong argument type %s, %s operator requires %s",
                            leftSideType.getRepresentation(stringTable),
                            operator,
                            expectedArgumentType.getRepresentation(stringTable)
                        );
                    }
                });
                rightSideType_.ifPresent(rightSideType -> {
                    if (!rightSideType.isCompatibleTo(expectedArgumentType)) {
                        semanticError(binaryExpressionNode,
                            "wrong argument type %s, %s operator requires %s",
                            rightSideType.getRepresentation(stringTable),
                            operator,
                            expectedArgumentType.getRepresentation(stringTable)
                        );
                    }
                });

                resultType_ = Optional.of(operator.getResultType());
                break;
        }

        return applyResultType(binaryExpressionNode, resultType_);
    }

    public Optional<DataType> visit(UnaryExpressionNode unaryExpressionNode) {
        UnaryOperator operator = unaryExpressionNode.getOperator();

        Optional<DataType> argumentType_ = unaryExpressionNode.getExpression().accept(this);
        DataType expectedArgumentType = operator.getExpectedArgumentType();

        argumentType_.ifPresent(argumentType -> {
            if (!argumentType.isCompatibleTo(expectedArgumentType)) {
                semanticError(unaryExpressionNode,
                    "wrong argument type %s, %s operator requires %s",
                    argumentType.getRepresentation(stringTable),
                    operator,
                    expectedArgumentType.getRepresentation(stringTable)
                );
            }
        });

        return applyResultType(unaryExpressionNode, Optional.of(operator.getResultType()));
    }

    private Optional<MethodNode> visitStandardLibraryMethodInvocationExpressionNode(MethodInvocationExpressionNode methodInvocationExpressionNode) {
        // standard library is not shadowed
        if (namespaceMapper.containsClassNamespace(standardLibrary.getSystemName())) {
            return Optional.empty();
        } else if (symboltable.isDefined(standardLibrary.getSystemName())) {
            return Optional.empty();
        }

        // method is called on some object (we expect System.out / System.in)
        if (!methodInvocationExpressionNode.getObject().isPresent()) {
            return Optional.empty();
        }
        ExpressionNode methodInvocationObject = methodInvocationExpressionNode.getObject().get();

        // method call object is field access (we expect System.out / System.in)
        if (!(methodInvocationObject instanceof FieldAccessExpressionNode)) {
            return Optional.empty();
        }
        FieldAccessExpressionNode fieldAccess = (FieldAccessExpressionNode)methodInvocationObject;

        // field access object is identifier (we expect "System")
        if (!(fieldAccess.getObject() instanceof IdentifierExpressionNode)) {
            return Optional.empty();
        }
        IdentifierExpressionNode identifier = (IdentifierExpressionNode)fieldAccess.getObject();

        // identifier is "System"
        if (identifier.getIdentifier() != standardLibrary.getSystemName()) {
            return Optional.empty();
        }

        // field is "out" or "in"
        if (fieldAccess.getName() == standardLibrary.getSystemInName()) {
            return Optional.ofNullable(
                standardLibrary.getSystemInMethods().get(methodInvocationExpressionNode.getName())
            );
        } else if (fieldAccess.getName() == standardLibrary.getSystemOutName()) {
            return Optional.ofNullable(
                standardLibrary.getSystemOutMethods().get(methodInvocationExpressionNode.getName())
            );
        } else {
            return Optional.empty();
        }
    }

    public Optional<DataType> visit(MethodInvocationExpressionNode methodInvocationExpressionNode) {
        Optional<MethodNode> systemDefinition = visitStandardLibraryMethodInvocationExpressionNode(methodInvocationExpressionNode);

        Optional<MethodNode> definition_;
        if (systemDefinition.isPresent()) {
            methodInvocationExpressionNode.removeObject();

            definition_ = systemDefinition;
        } else {
            Optional<ClassNamespace> namespace_;
            if (methodInvocationExpressionNode.getObject().isPresent()) {
                Optional<DataType> objectType_ = methodInvocationExpressionNode.getObject().get().accept(this);

                namespace_ = objectType_.flatMap(objectType -> {
                    if (objectType.getType() == DataTypeClass.UserDefined) {
                        return Optional.of(namespaceMapper.getClassNamespace(objectType.getIdentifier().get()));
                    } else {
                        return semanticError(methodInvocationExpressionNode, "method invocation is only allowed on reference type expressions");
                    }
                });
            } else {
                if (!currentClassNamespace.isPresent()) {
                    semanticError(methodInvocationExpressionNode, "method calls without object are not allowed in static methods");
                }

                namespace_ = currentClassNamespace;
            }

            definition_ = namespace_.flatMap(namespace -> {
                if (namespace.getDynamicMethods().containsKey(methodInvocationExpressionNode.getName())) {
                    return Optional.of(namespace.getDynamicMethods().get(methodInvocationExpressionNode.getName()));
                } else {
                    return semanticError(methodInvocationExpressionNode, "unknown method");
                }
            });
        }

        definition_.ifPresentOrElse(definition -> {
            methodInvocationExpressionNode.setDefinition(definition);
        }, () -> {
            methodInvocationExpressionNode.setHasError(true);
        });

        definition_.ifPresent(definition -> {
            if (methodInvocationExpressionNode.getArguments().size() != definition.getParameters().size()) {
                semanticError(methodInvocationExpressionNode, "wrong number of arguments");
            }
        });

        for (int i = 0; i < methodInvocationExpressionNode.getArguments().size(); i++) {
            final int i_ = i;

            Optional<DataType> actualArgumentType_ = methodInvocationExpressionNode.getArguments().get(i).accept(this);
            Optional<DataType> expectedArgumentType_ = definition_.flatMap(definition -> {
                if (i_ < definition.getParameters().size()) {
                    return Optional.of(definition.getParameters().get(i_).getType());
                } else {
                    return Optional.empty();
                }
            });

            ifBothPresent(actualArgumentType_, expectedArgumentType_, (actualArgumentType, expectedArgumentType) -> {
                if (!actualArgumentType.isCompatibleTo(expectedArgumentType)) {
                    semanticError(methodInvocationExpressionNode,
                        "argument %d of type %s does not match expected type %s",
                        i_,
                        actualArgumentType.getRepresentation(stringTable),
                        expectedArgumentType.getRepresentation(stringTable)
                    );
                }
            });
        }

        Optional<DataType> resultType_ = definition_.map(definition -> definition.getType());
        return applyResultType(methodInvocationExpressionNode, resultType_);
    }

    public Optional<DataType> visit(FieldAccessExpressionNode fieldAccessExpressionNode) {
        Optional<DataType> objectType_ = fieldAccessExpressionNode.getObject().accept(this);

        Optional<ClassNamespace> namespace_ = objectType_.flatMap(objectType -> {
            if (objectType.getType() == DataTypeClass.UserDefined) {
                return Optional.of(namespaceMapper.getClassNamespace(objectType.getIdentifier().get()));
            } else {
                return semanticError(fieldAccessExpressionNode, "field access is only allowed on reference type expressions");
            }
        });

        Optional<ClassNodeField> definition_ = namespace_.flatMap(namespace -> {
            if (namespace.getClassSymbols().containsKey(fieldAccessExpressionNode.getName())) {
                return Optional.of(namespace.getClassSymbols().get(fieldAccessExpressionNode.getName()));
            } else {
                return semanticError(fieldAccessExpressionNode, "unknown class field");
            }
        });

        applyDefinition(fieldAccessExpressionNode, definition_);

        Optional<DataType> resultType_ = definition_.map(definition -> definition.getType());
        return applyResultType(fieldAccessExpressionNode, resultType_);
    }

    public Optional<DataType> visit(ArrayAccessExpressionNode arrayAccessExpressionNode) {
        Optional<DataType> objectType_ = arrayAccessExpressionNode.getObject().accept(this);
        objectType_ = objectType_.flatMap(objectType -> {
            if (objectType.getType() == DataTypeClass.Array) {
                return Optional.of(objectType);
            } else {
                return semanticError(arrayAccessExpressionNode, "%s is not an array type", objectType.getRepresentation(stringTable));
            }
        });

        Optional<DataType> expressionType_ = arrayAccessExpressionNode.getExpression().accept(this);
        expressionType_.ifPresent(expressionType -> {
            if (expressionType.getType() != DataTypeClass.Int) {
                semanticError(arrayAccessExpressionNode, "array index must be of type int");
            }
        });

        Optional<DataType> resultType_ = objectType_.map(objectType -> objectType.getInnerType().get());
        return applyResultType(arrayAccessExpressionNode, resultType_);
    }

    public Optional<DataType> visit(IdentifierExpressionNode identifierExpressionNode) {
        Optional<Definition> definition_;
        if (symboltable.isDefined(identifierExpressionNode.getIdentifier())) {
            definition_ = Optional.of(symboltable.lookup(identifierExpressionNode.getIdentifier()));
        } else {
            semanticError(identifierExpressionNode, "`%s` cannot be resolved", stringTable.retrieve(identifierExpressionNode.getIdentifier()));
            definition_ = Optional.empty();
        }

        applyDefinition(identifierExpressionNode, definition_);

        Optional<DataType> resultType_ = definition_.flatMap(definition -> {
            if (!currentClassNamespace.isPresent() && definition.getKind() == DefinitionKind.Field) {
                return semanticError(identifierExpressionNode, "field access is not allowed in static contexts");
            } else {
                return Optional.of(definition.getType());
            }
        });
        return applyResultType(identifierExpressionNode, resultType_);
    }

    public Optional<DataType> visit(ThisExpressionNode thisExpressionNode) {
        if (currentClassNamespace.isPresent()) {
            ClassNode classNode = currentClassNamespace.get().getClassNodeRef();
            DataType resultType = new DataType(classNode.getName());

            thisExpressionNode.setDefinition(classNode);

            return applyResultType(thisExpressionNode, Optional.of(resultType));
        } else {
            return semanticError(thisExpressionNode, "`this` is not allowed in static contexts");
        }
    }

    public Optional<DataType> visit(ValueExpressionNode valueExpressionNode) {
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

        return applyResultType(valueExpressionNode, Optional.of(resultType));
    }

    public Optional<DataType> visit(NewObjectExpressionNode newObjectExpressionNode) {
        Optional<DataType> objectType_ = Optional.of(new DataType(newObjectExpressionNode.getTypeName()));
        Optional<DataType> resultType_ = objectType_.flatMap(objectType -> {
            if (isValidDataType(objectType)) {
                return Optional.of(objectType);
            } else {
                return semanticError(newObjectExpressionNode, "unknown reference type %s", stringTable.retrieve(objectType.getIdentifier().get()));
            }
        });

        return applyResultType(newObjectExpressionNode, resultType_);
    }

    public Optional<DataType> visit(NewArrayExpressionNode newArrayExpressionNode) {
        Optional<DataType> elementType_ = Optional.of(newArrayExpressionNode.getElementType());
        elementType_ = elementType_.flatMap(elementType -> {
            if (isValidDataType(elementType)) {
                return Optional.of(elementType);
            } else {
                if (elementType.getType() == DataTypeClass.Void) {
                    return semanticError(newArrayExpressionNode, "void type is not allowed in a new array expression");
                } else {
                    return semanticError(newArrayExpressionNode, "unknown reference type %s", stringTable.retrieve(elementType.getIdentifier().get()));
                }
            }
        });

        Optional<DataType> expressionType_ = newArrayExpressionNode.getLength().accept(this);
        expressionType_.ifPresent(expressionType -> {
            if (expressionType.getType() != DataTypeClass.Int) {
                semanticError(newArrayExpressionNode, "array length must be of type int");
            }
        });

        Optional<DataType> resultType_ = elementType_.map(elementType -> {
            DataType arrayType = elementType;
            for (int i = 0; i < newArrayExpressionNode.getDimensions(); i++) {
                arrayType = new DataType(arrayType);
            }
            return arrayType;
        });

        return applyResultType(newArrayExpressionNode, resultType_);
    }

}
