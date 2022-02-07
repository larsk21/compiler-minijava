package edu.kit.compiler.parser;

import edu.kit.compiler.data.AstVisitor;
import edu.kit.compiler.data.ast_nodes.*;
import edu.kit.compiler.data.ast_nodes.ExpressionNode.*;
import edu.kit.compiler.data.ast_nodes.MethodNode.*;
import edu.kit.compiler.data.ast_nodes.ClassNode.*;
import edu.kit.compiler.data.ast_nodes.StatementNode.*;
import edu.kit.compiler.lexer.StringTable;

public class PrintAstVisitor implements AstVisitor<Void> {

    private static final Void nothing = null;

    public PrintAstVisitor(StringTable stringTable) {
        this.stringTable = stringTable;

        this.indentation = 0;
    }

    private final StringTable stringTable;

    private int indentation;

    private void println(String format, Object... args) {
        for (int i = 0; i < indentation; i++) {
            System.out.print("    ");
        }

        System.out.printf(format, args);
        System.out.println();
    }

    @Override
    public Void visit(ProgramNode programNode) {
        println("<program>");

        indentation++;
        for (ClassNode _class : programNode.getClasses()) {
            _class.accept(this);
        }
        indentation--;

        return nothing;
    }

    @Override
    public Void visit(ClassNode classNode) {
        println("<class '%s'>", stringTable.retrieve(classNode.getName()));

        indentation++;
        for (ClassNodeField field : classNode.getFields()) {
            println("<field '%s'>", stringTable.retrieve(field.getName()));
        }
        for (MethodNode method : classNode.getStaticMethods()) {
            method.accept(this);
        }
        for (MethodNode method : classNode.getDynamicMethods()) {
            method.accept(this);
        }
        indentation--;

        return nothing;
    }

    private Void visitMethodNode(MethodNode methodNode, String type) {
        println("<%s method '%s'>", type, stringTable.retrieve(methodNode.getName()));

        indentation++;
        for (MethodNodeParameter param : methodNode.getParameters()) {
            println("<param '%s' '%s'>", param.getType().getRepresentation(stringTable), stringTable.retrieve(param.getName()));
        }
        if (methodNode.getRest().isPresent()) {
            println("<throws '%s'>", stringTable.retrieve(methodNode.getRest().get().getThrowsTypeIdentifier()));
        }
        indentation--;

        methodNode.getStatementBlock().accept(this);

        return nothing;
    }

    @Override
    public Void visit(StaticMethodNode staticMethodNode) {
        return visitMethodNode(staticMethodNode, "static");
    }

    @Override
    public Void visit(DynamicMethodNode dynamicMethodNode) {
        return visitMethodNode(dynamicMethodNode, "dynamic");
    }

    @Override
    public Void visit(BlockStatementNode blockStatementNode) {
        indentation++;
        for (StatementNode statement : blockStatementNode.getStatements()) {
            statement.accept(this);
        }
        indentation--;

        return nothing;
    }

    @Override
    public Void visit(LocalVariableDeclarationStatementNode localVariableDeclarationStatementNode) {
        println("<local var '%s' '%s'>",
            localVariableDeclarationStatementNode.getType().getRepresentation(stringTable),
            stringTable.retrieve(localVariableDeclarationStatementNode.getName())
        );

        if (localVariableDeclarationStatementNode.getExpression().isPresent()) {
            indentation++;
            localVariableDeclarationStatementNode.getExpression().get().accept(this);
            indentation--;
        }

        return nothing;
    }

    @Override
    public Void visit(IfStatementNode ifStatementNode) {
        println("<if>");

        indentation++;
        ifStatementNode.getCondition().accept(this);
        indentation--;

        ifStatementNode.getThenStatement().accept(this);
        if (ifStatementNode.getElseStatement().isPresent()) {
            ifStatementNode.getElseStatement().get().accept(this);
        }

        return nothing;
    }

    @Override
    public Void visit(WhileStatementNode whileStatementNode) {
        println("<if>");

        indentation++;
        whileStatementNode.getCondition().accept(this);
        indentation--;

        whileStatementNode.getStatement().accept(this);

        return nothing;
    }

    @Override
    public Void visit(ReturnStatementNode returnStatementNode) {
        println("<return>");

        if (returnStatementNode.getResult().isPresent()) {
            indentation++;
            returnStatementNode.getResult().get().accept(this);
            indentation--;
        }

        return nothing;
    }

    @Override
    public Void visit(ExpressionStatementNode expressionStatementNode) {
        println("<expr statement>");

        indentation++;
        expressionStatementNode.getExpression().accept(this);
        indentation--;

        return nothing;
    }

    @Override
    public Void visit(BinaryExpressionNode binaryExpressionNode) {
        println("<binary expr '%s'>", binaryExpressionNode.getOperator());

        indentation++;
        binaryExpressionNode.getLeftSide().accept(this);
        binaryExpressionNode.getRightSide().accept(this);
        indentation--;

        return nothing;
    }

    @Override
    public Void visit(UnaryExpressionNode unaryExpressionNode) {
        println("<unary expr '%s'>", unaryExpressionNode.getOperator());

        indentation++;
        unaryExpressionNode.getExpression().accept(this);
        indentation--;

        return nothing;
    }

    @Override
    public Void visit(MethodInvocationExpressionNode methodInvocationExpressionNode) {
        println("<method call '%s'>", stringTable.retrieve(methodInvocationExpressionNode.getName()));

        indentation++;
        if (methodInvocationExpressionNode.getObject().isPresent()) {
            methodInvocationExpressionNode.getObject().get().accept(this);
        } else {
            println("<this>");
        }
        for (ExpressionNode argument : methodInvocationExpressionNode.getArguments()) {
            argument.accept(this);
        }
        indentation--;

        return nothing;
    }

    @Override
    public Void visit(FieldAccessExpressionNode fieldAccessExpressionNode) {
        println("<field access '%s'>", stringTable.retrieve(fieldAccessExpressionNode.getName()));

        indentation++;
        fieldAccessExpressionNode.getObject().accept(this);
        indentation--;

        return nothing;
    }

    @Override
    public Void visit(ArrayAccessExpressionNode arrayAccessExpressionNode) {
        println("<array access>");

        indentation++;
        arrayAccessExpressionNode.getExpression().accept(this);
        arrayAccessExpressionNode.getObject().accept(this);
        indentation--;

        return nothing;
    }

    @Override
    public Void visit(IdentifierExpressionNode identifierExpressionNode) {
        println("<ident '%s'>", stringTable.retrieve(identifierExpressionNode.getIdentifier()));

        return nothing;
    }

    @Override
    public Void visit(ThisExpressionNode thisExpressionNode) {
        println("<this>");

        return nothing;
    }

    @Override
    public Void visit(ValueExpressionNode valueExpressionNode) {
        println("<value '%s'>",
            valueExpressionNode.getValueType() == ValueExpressionType.IntegerLiteral ?
            valueExpressionNode.getLiteralValue().get() : valueExpressionNode.getValueType().name()
        );

        return nothing;
    }

    @Override
    public Void visit(NewObjectExpressionNode newObjectExpressionNode) {
        println("<new '%s'>", stringTable.retrieve(newObjectExpressionNode.getTypeName()));

        return nothing;
    }

    @Override
    public Void visit(NewArrayExpressionNode newArrayExpressionNode) {
        println("<new array '%s'>", newArrayExpressionNode.getElementType().getRepresentation(stringTable));

        indentation++;
        newArrayExpressionNode.getLength().accept(this);
        indentation--;

        return nothing;
    }

}
