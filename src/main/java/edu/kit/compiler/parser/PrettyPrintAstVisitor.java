package edu.kit.compiler.parser;

import edu.kit.compiler.data.AstVisitor;
import edu.kit.compiler.data.DataType;
import edu.kit.compiler.data.Operator.*;
import edu.kit.compiler.data.ast_nodes.*;
import edu.kit.compiler.data.ast_nodes.ClassNode.ClassNodeField;
import edu.kit.compiler.data.ast_nodes.ExpressionNode.*;
import edu.kit.compiler.data.ast_nodes.MethodNode.*;
import edu.kit.compiler.data.ast_nodes.StatementNode.*;
import edu.kit.compiler.lexer.StringTable;

public class PrettyPrintAstVisitor implements AstVisitor {

    public PrettyPrintAstVisitor(StringTable stringTable) {
        this.stringTable = stringTable;
    }

    private StringTable stringTable;

    private String getTypeName(DataType type) {
        switch (type.getType()) {
        case Array:
            return type.getInnerType().map(
                innerType -> getTypeName(innerType) + "[]"
            ).orElseThrow(
                () -> new IllegalStateException("array type without inner type")
            );
        case Boolean:
            return "boolean";
        case Int:
            return "int";
        case UserDefined:
            return type.getIdentifier().map(
                identifier -> stringTable.retrieve(identifier)
            ).orElseThrow(
                () -> new IllegalStateException("user defined type without identifier")
            );
        case Void:
            return "void";
        default:
            throw new IllegalStateException("unsupported data type");
        }
    }

    private String getOperatorRepresentation(BinaryOperator operator) {
        switch (operator) {
        case Addition:
            return "+";
        case Assignment:
            return "=";
        case Division:
            return "/";
        case Equal:
            return "==";
        case GreaterThan:
            return ">";
        case GreaterThanOrEqual:
            return ">=";
        case LessThan:
            return "<";
        case LessThanOrEqual:
            return "<=";
        case LogicalAnd:
            return "&&";
        case LogicalOr:
            return "||";
        case Modulo:
            return "%";
        case Multiplication:
            return "*";
        case NotEqual:
            return "!=";
        case Subtraction:
            return "-";
        default:
            throw new IllegalStateException("unsupported operator");
        }
    }

    private String getOperatorRepresentation(UnaryOperator operator) {
        switch (operator) {
        case ArithmeticNegation:
            return "-";
        case LogicalNegation:
            return "!";
        default:
            throw new IllegalStateException("unsupported operator");
        }
    }

    @Override
    public void visit(ProgramNode programNode) {
        for (ClassNode _class : programNode.getClasses()) {
            _class.accept(this);

            System.out.println();
            System.out.println();
        }
    }

    @Override
    public void visit(ClassNode classNode) {
        String className = stringTable.retrieve(classNode.getName());
        System.out.println(String.format("class %s {", className));

        System.out.println();

        for (ClassNodeField field : classNode.getFields()) {
            String typeName = getTypeName(field.getType());
            String fieldName = stringTable.retrieve(field.getName());

            System.out.println(String.format("public %s %s;", typeName, fieldName));
        }

        System.out.println();

        for (StaticMethodNode staticMethod : classNode.getStaticMethods()) {
            staticMethod.accept(this);

            System.out.println();
        }

        for (DynamicMethodNode dynamicMethod : classNode.getDynamicMethods()) {
            dynamicMethod.accept(this);

            System.out.println();
        }

        System.out.println();
        System.out.println("}");
    }

    private void visitMethodNode(MethodNode methodNode) {
        System.out.print("(");

        {
            boolean first = true;
            for (MethodNodeParameter parameter : methodNode.getParameters()) {
                if (!first) {
                    System.out.print(", ");
                }

                System.out.print(String.format("%s %s", parameter.getType(), parameter.getName()));

                first = false;
            }
        }

        System.out.print(")");

        if (methodNode.getRest().getThrowsTypeIdentifier().isPresent()) {
            String exceptionTypeName = stringTable.retrieve(methodNode.getRest().getThrowsTypeIdentifier().get());

            System.out.print(String.format(" throws %s", exceptionTypeName));
        }

        System.out.println(" {");

        for (StatementNode statement : methodNode.getStatements()) {
            statement.accept(this);
        }

        System.out.println("}");
    }

    @Override
    public void visit(StaticMethodNode staticMethodNode) {
        String typeName = getTypeName(staticMethodNode.getType());
        String methodName = stringTable.retrieve(staticMethodNode.getName());

        System.out.print(String.format("public static %s %s ", typeName, methodName));

        visitMethodNode(staticMethodNode);
    }

    @Override
    public void visit(DynamicMethodNode dynamicMethodNode) {
        String typeName = getTypeName(dynamicMethodNode.getType());
        String methodName = stringTable.retrieve(dynamicMethodNode.getName());

        System.out.print(String.format("public %s %s ", typeName, methodName));

        visitMethodNode(dynamicMethodNode);
    }

    @Override
    public void visit(LocalVariableDeclarationStatementNode localVariableDeclarationStatementNode) {
        String typeName = getTypeName(localVariableDeclarationStatementNode.getType());
        String variableName = stringTable.retrieve(localVariableDeclarationStatementNode.getName());

        System.out.print(String.format("%s %s", typeName, variableName));

        if (localVariableDeclarationStatementNode.getExpression().isPresent()) {
            System.out.print(" = ");

            localVariableDeclarationStatementNode.getExpression().get().accept(this);
        }

        System.out.println(";");
    }

    @Override
    public void visit(IfStatementNode ifStatementNode) {
        System.out.print("if (");

        ifStatementNode.getCondition().accept(this);

        System.out.println(") {");

        for (StatementNode statement : ifStatementNode.getThenStatements()) {
            statement.accept(this);
        }

        {
            boolean first = true;
            for (StatementNode statement : ifStatementNode.getElseStatements()) {
                if (first) {
                    System.out.println("} else {");
                }

                statement.accept(this);

                first = false;
            }
        }

        System.out.println("}");
    }

    @Override
    public void visit(WhileStatementNode whileStatementNode) {
        System.out.print("while (");

        whileStatementNode.getCondition().accept(this);

        System.out.println(") {");

        for (StatementNode statement : whileStatementNode.getStatements()) {
            statement.accept(this);
        }

        System.out.println("}");
    }

    @Override
    public void visit(ReturnStatementNode returnStatementNode) {
        if (returnStatementNode.getResult().isPresent()) {
            System.out.print("return ");

            returnStatementNode.getResult().get().accept(this);

            System.out.println(";");
        } else {
            System.out.println("return;");
        }
    }

    @Override
    public void visit(ExpressionStatementNode expressionStatementNode) {
        expressionStatementNode.getExpression().accept(this);

        System.out.println(";");
    }

    @Override
    public void visit(BinaryExpressionNode binaryExpressionNode) {
        System.out.print("(");

        binaryExpressionNode.getLeftSide().accept(this);

        String operatorRepresentation = getOperatorRepresentation(binaryExpressionNode.getOperator());
        System.out.print(String.format(" %s ", operatorRepresentation));

        binaryExpressionNode.getRightSide().accept(this);

        System.out.print(")");
    }

    @Override
    public void visit(UnaryExpressionNode unaryExpressionNode) {
        String operatorRepresentation = getOperatorRepresentation(unaryExpressionNode.getOperator());
        System.out.print(operatorRepresentation);

        unaryExpressionNode.getExpression().accept(this);
    }

    @Override
    public void visit(MethodInvocationExpressionNode methodInvocationExpressionNode) {
        if (methodInvocationExpressionNode.getObject().isPresent()) {
            methodInvocationExpressionNode.getObject().get().accept(this);
        }

        String methodName = stringTable.retrieve(methodInvocationExpressionNode.getName());
        System.out.print(String.format(".%s(", methodName));

        {
            boolean first = true;
            for (ExpressionNode argument : methodInvocationExpressionNode.getArguments()) {
                if (!first) {
                    System.out.print(", ");
                }

                argument.accept(this);

                first = false;
            }
        }

        System.out.print(")");
    }

    @Override
    public void visit(FieldAccessExpressionNode fieldAccessExpressionNode) {
        fieldAccessExpressionNode.getObject().accept(this);

        String fieldName = stringTable.retrieve(fieldAccessExpressionNode.getName());
        System.out.print(String.format(".%s", fieldName));
    }

    @Override
    public void visit(ArrayAccessExpressionNode arrayAccessExpressionNode) {
        arrayAccessExpressionNode.getObject().accept(this);

        System.out.print("[");

        arrayAccessExpressionNode.getExpression().accept(this);

        System.out.print("]");
    }

    @Override
    public void visit(ValueExpressionNode valueExpressionNode) {
        switch (valueExpressionNode.getType()) {
        case False:
            System.out.print("false");
            break;
        case Identifier:
            System.out.print(valueExpressionNode.getIntValue().map(
                value -> stringTable.retrieve(value)
            ).orElseThrow(
                () -> new IllegalStateException("identifier primary expression without associated identifier")
            ));
            break;
        case IntegerLiteral:
            System.out.print(valueExpressionNode.getLiteralValue().map(
                value -> value.toString()
            ).orElseThrow(
                () -> new IllegalStateException("integer literal primary expression without associated integer literal")
            ));
            break;
        case Null:
            System.out.print("null");
            break;
        case This:
            System.out.print("this");
            break;
        case True:
            System.out.print("true");
            break;
        default:
            throw new IllegalStateException("Unsupported value primary expression type");
        }
    }

    @Override
    public void visit(NewObjectExpressionNode newObjectExpressionNode) {
        String typeName = stringTable.retrieve(newObjectExpressionNode.getTypeName());
        System.out.print(String.format("new %s()", typeName));
    }

    @Override
    public void visit(NewArrayExpressionNode newArrayExpressionNode) {
        String typeName = getTypeName(newArrayExpressionNode.getType());
        System.out.print(String.format("new %s[", typeName));

        newArrayExpressionNode.getLength().accept(this);

        System.out.print("]");

        for (int i = 1; i < newArrayExpressionNode.getDimensions(); i++) {
            System.out.print("[]");
        }
    }

}
