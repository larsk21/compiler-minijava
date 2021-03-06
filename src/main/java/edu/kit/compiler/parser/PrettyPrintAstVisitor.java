package edu.kit.compiler.parser;

import edu.kit.compiler.data.AstVisitor;
import edu.kit.compiler.data.ast_nodes.*;
import edu.kit.compiler.data.ast_nodes.ClassNode.ClassNodeField;
import edu.kit.compiler.data.ast_nodes.ExpressionNode.*;
import edu.kit.compiler.data.ast_nodes.MethodNode.DynamicMethodNode;
import edu.kit.compiler.data.ast_nodes.MethodNode.MethodNodeParameter;
import edu.kit.compiler.data.ast_nodes.MethodNode.StaticMethodNode;
import edu.kit.compiler.data.ast_nodes.StatementNode.*;
import edu.kit.compiler.io.CommonUtil;
import edu.kit.compiler.lexer.StringTable;

import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PrettyPrintAstVisitor implements AstVisitor<Void> {

    private static final Void nothing = null;

    public PrettyPrintAstVisitor(StringTable stringTable) {
        this.stringTable = stringTable;

        this.indentation = 0;
        this.firstInLine = true;
        this.topLevelExpression = true;
    }

    private final StringTable stringTable;

    private int indentation;
    private boolean firstInLine;
    private boolean topLevelExpression;

    private void print(String format, Object... args) {
        if (firstInLine) {
            for (int i = 0; i < indentation; i++) {
                System.out.print('\t');
            }

            firstInLine = false;
        }

        System.out.printf(format, args);
    }

    private void println() {
        System.out.println();

        firstInLine = true;
    }

    private void println(String format, Object... args) {
        print(format, args);
        println();
    }

    @SafeVarargs
    private <T> List<T> sortAlphabetically(Function<T, Integer> name, Iterable<? extends T>... iterables) {
        Stream<T> stream = Stream.empty();

        for (Iterable<? extends T> iterable : iterables) {
            stream = Stream.concat(stream, CommonUtil.stream(iterable));
        }

        return stream.sorted(new Comparator<T>() {

            @Override
            public int compare(T arg0, T arg1) {
                String name0 = stringTable.retrieve(name.apply(arg0));
                String name1 = stringTable.retrieve(name.apply(arg1));

                return name0.compareTo(name1);
            }

        }).collect(Collectors.toList());
    }

    @Override
    public Void visit(ProgramNode programNode) {
        for (ClassNode _class : sortAlphabetically(
            _class -> _class.getName(),
            programNode.getClasses()
        )) {
            _class.accept(this);

            println();
        }

        return nothing;
    }

    @Override
    public Void visit(ClassNode classNode) {
        String className = stringTable.retrieve(classNode.getName());
        print("class %s", className);

        List<ClassNodeField> fields = sortAlphabetically(
            field -> field.getName(),
            classNode.getFields()
        );
        List<MethodNode> methods = sortAlphabetically(
            method -> method.getName(),
            classNode.getStaticMethods(),
            classNode.getDynamicMethods()
        );

        if (fields.isEmpty() && methods.isEmpty()) {
            print(" { }");
        } else {
            println(" {");

            indentation++;

            for (MethodNode method : methods) {
                method.accept(this);
                println();
            }

            for (ClassNodeField field : fields) {
                String typeName = field.getType().getRepresentation(stringTable);
                String fieldName = stringTable.retrieve(field.getName());

                println("public %s %s;", typeName, fieldName);
            }

            indentation--;

            print("}");
        }

        return nothing;
    }

    private void visitMethodNode(MethodNode methodNode) {
        print("(");

        {
            boolean first = true;
            for (MethodNodeParameter parameter : methodNode.getParameters()) {
                if (!first) {
                    print(", ");
                }

                String typeName = parameter.getType().getRepresentation(stringTable);
                String parameterName = stringTable.retrieve(parameter.getName());

                print("%s %s", typeName, parameterName);

                first = false;
            }
        }

        print(")");

        if (methodNode.getRest().isPresent()) {
            String exceptionTypeName = stringTable.retrieve(methodNode.getRest().get().getThrowsTypeIdentifier());

            print(" throws %s", exceptionTypeName);
        }

        print(" ");

        methodNode.getStatementBlock().accept(this);
    }

    @Override
    public Void visit(StaticMethodNode staticMethodNode) {
        String typeName = staticMethodNode.getType().getRepresentation(stringTable);
        String methodName = stringTable.retrieve(staticMethodNode.getName());

        print("public static %s %s", typeName, methodName);

        visitMethodNode(staticMethodNode);

        return nothing;
    }

    @Override
    public Void visit(DynamicMethodNode dynamicMethodNode) {
        String typeName = dynamicMethodNode.getType().getRepresentation(stringTable);
        String methodName = stringTable.retrieve(dynamicMethodNode.getName());

        print("public %s %s", typeName, methodName);

        visitMethodNode(dynamicMethodNode);

        return nothing;
    }

    @Override
    public Void visit(BlockStatementNode blockStatementNode) {
        List<StatementNode> statements = CommonUtil.toList(blockStatementNode.getStatements());

        if (statements.isEmpty()) {
            print("{ }");
        } else {
            println("{");

            indentation++;

            for (StatementNode statement : statements) {
                statement.accept(this);

                println();
            }

            indentation--;

            print("}");
        }

        return nothing;
    }

    @Override
    public Void visit(LocalVariableDeclarationStatementNode localVariableDeclarationStatementNode) {
        String typeName = localVariableDeclarationStatementNode.getType().getRepresentation(stringTable);
        String variableName = stringTable.retrieve(localVariableDeclarationStatementNode.getName());

        print("%s %s", typeName, variableName);

        if (localVariableDeclarationStatementNode.getExpression().isPresent()) {
            print(" = ");

            localVariableDeclarationStatementNode.getExpression().get().accept(this);
        }

        print(";");

        return nothing;
    }

    @Override
    public Void visit(IfStatementNode ifStatementNode) {
        print("if (");

        ifStatementNode.getCondition().accept(this);

        print(")");

        if (ifStatementNode.getThenStatement() instanceof BlockStatementNode) {
            print(" ");

            ifStatementNode.getThenStatement().accept(this);

            if (ifStatementNode.getElseStatement().isPresent()) {
                print(" ");
            }
        } else {
            println();

            indentation++;

            ifStatementNode.getThenStatement().accept(this);

            indentation--;

            if (ifStatementNode.getElseStatement().isPresent()) {
                println();
            }
        }

        if (ifStatementNode.getElseStatement().isPresent()) {
            print("else");

            if (
                ifStatementNode.getElseStatement().get() instanceof BlockStatementNode ||
                ifStatementNode.getElseStatement().get() instanceof IfStatementNode
            ) {
                print(" ");

                ifStatementNode.getElseStatement().get().accept(this);
            } else {
                println();

                indentation++;

                ifStatementNode.getElseStatement().get().accept(this);

                indentation--;
            }
        }

        return nothing;
    }

    @Override
    public Void visit(WhileStatementNode whileStatementNode) {
        print("while (");

        whileStatementNode.getCondition().accept(this);

        print(")");

        if (whileStatementNode.getStatement() instanceof BlockStatementNode) {
            print(" ");

            whileStatementNode.getStatement().accept(this);
        } else {
            println();

            indentation++;

            whileStatementNode.getStatement().accept(this);

            indentation--;
        }

        return nothing;
    }

    @Override
    public Void visit(ReturnStatementNode returnStatementNode) {
        if (returnStatementNode.getResult().isPresent()) {
            print("return ");

            returnStatementNode.getResult().get().accept(this);

            print(";");
        } else {
            print("return;");
        }

        return nothing;
    }

    @Override
    public Void visit(ExpressionStatementNode expressionStatementNode) {
        expressionStatementNode.getExpression().accept(this);

        print(";");

        return nothing;
    }

    @Override
    public Void visit(BinaryExpressionNode binaryExpressionNode) {
        if (!topLevelExpression) {
            print("(");
        }

        boolean _topLevelExpression = topLevelExpression;
        topLevelExpression = false;

        binaryExpressionNode.getLeftSide().accept(this);

        print(" %s ", binaryExpressionNode.getOperator().toString());

        binaryExpressionNode.getRightSide().accept(this);

        topLevelExpression = _topLevelExpression;

        if (!topLevelExpression) {
            print(")");
        }

        return nothing;
    }

    @Override
    public Void visit(UnaryExpressionNode unaryExpressionNode) {
        if (!topLevelExpression) {
            print("(");
        }

        boolean _topLevelExpression = topLevelExpression;
        topLevelExpression = false;

        print(unaryExpressionNode.getOperator().toString());

        unaryExpressionNode.getExpression().accept(this);

        topLevelExpression = _topLevelExpression;

        if (!topLevelExpression) {
            print(")");
        }

        return nothing;
    }

    @Override
    public Void visit(MethodInvocationExpressionNode methodInvocationExpressionNode) {
        if (!topLevelExpression) {
            print("(");
        }

        boolean _topLevelExpression = topLevelExpression;
        topLevelExpression = false;

        if (methodInvocationExpressionNode.getObject().isPresent()) {
            methodInvocationExpressionNode.getObject().get().accept(this);

            print(".");
        }

        String methodName = stringTable.retrieve(methodInvocationExpressionNode.getName());
        print("%s(", methodName);

        {
            topLevelExpression = true;

            boolean first = true;
            for (ExpressionNode argument : methodInvocationExpressionNode.getArguments()) {
                if (!first) {
                    print(", ");
                }

                argument.accept(this);

                first = false;
            }
        }

        print(")");

        topLevelExpression = _topLevelExpression;

        if (!topLevelExpression) {
            print(")");
        }

        return nothing;
    }

    @Override
    public Void visit(FieldAccessExpressionNode fieldAccessExpressionNode) {
        if (!topLevelExpression) {
            print("(");
        }

        boolean _topLevelExpression = topLevelExpression;
        topLevelExpression = false;

        fieldAccessExpressionNode.getObject().accept(this);

        String fieldName = stringTable.retrieve(fieldAccessExpressionNode.getName());
        print(".%s", fieldName);

        topLevelExpression = _topLevelExpression;

        if (!topLevelExpression) {
            print(")");
        }

        return nothing;
    }

    @Override
    public Void visit(ArrayAccessExpressionNode arrayAccessExpressionNode) {
        if (!topLevelExpression) {
            print("(");
        }

        boolean _topLevelExpression = topLevelExpression;
        topLevelExpression = false;

        arrayAccessExpressionNode.getObject().accept(this);

        print("[");

        topLevelExpression = true;

        arrayAccessExpressionNode.getExpression().accept(this);

        print("]");

        topLevelExpression = _topLevelExpression;

        if (!topLevelExpression) {
            print(")");
        }

        return nothing;
    }

    @Override
    public Void visit(IdentifierExpressionNode identifierExpressionNode) {
        String name = stringTable.retrieve(identifierExpressionNode.getIdentifier());

        print(name);

        return nothing;
    }

    @Override
    public Void visit(ThisExpressionNode thisExpressionNode) {
        print("this");

        return nothing;
    }

    public void printLiteralStringValue(String literal) {
        if (literal.startsWith("-")) {
            if (!topLevelExpression) {
                print("(");
            }

            print("-");
            printLiteralStringValue(literal.substring(1));

            if (!topLevelExpression) {
                print(")");
            }
        } else {
            print(literal);
        }
    }

    @Override
    public Void visit(ValueExpressionNode valueExpressionNode) {
        switch (valueExpressionNode.getValueType()) {
        case False:
            print("false");
            break;
        case IntegerLiteral:
            if (valueExpressionNode.getLiteralValue().isPresent()) {
                printLiteralStringValue(valueExpressionNode.getLiteralValue().get().toString());
            } else {
                new IllegalStateException("integer literal primary expression without associated integer literal");
            }
            break;
        case Null:
            print("null");
            break;
        case True:
            print("true");
            break;
        default:
            throw new IllegalStateException("Unsupported value primary expression type");
        }

        return nothing;
    }

    @Override
    public Void visit(NewObjectExpressionNode newObjectExpressionNode) {
        if (!topLevelExpression) {
            print("(");
        }

        boolean _topLevelExpression = topLevelExpression;
        topLevelExpression = false;

        String typeName = stringTable.retrieve(newObjectExpressionNode.getTypeName());
        print("new %s()", typeName);

        topLevelExpression = _topLevelExpression;

        if (!topLevelExpression) {
            print(")");
        }

        return nothing;
    }

    @Override
    public Void visit(NewArrayExpressionNode newArrayExpressionNode) {
        if (!topLevelExpression) {
            print("(");
        }

        boolean _topLevelExpression = topLevelExpression;
        topLevelExpression = false;

        String typeName = newArrayExpressionNode.getElementType().getRepresentation(stringTable);
        print("new %s[", typeName);

        topLevelExpression = true;

        newArrayExpressionNode.getLength().accept(this);

        print("]");

        for (int i = 1; i < newArrayExpressionNode.getDimensions(); i++) {
            print("[]");
        }

        topLevelExpression = _topLevelExpression;

        if (!topLevelExpression) {
            print(")");
        }

        return nothing;
    }

}
