package edu.kit.compiler.transform;

import edu.kit.compiler.data.ast_nodes.*;
import edu.kit.compiler.lexer.Lexer;
import edu.kit.compiler.logger.Logger;
import edu.kit.compiler.parser.Parser;
import edu.kit.compiler.semantic.DetailedNameTypeAstVisitor;
import edu.kit.compiler.semantic.ErrorHandler;
import edu.kit.compiler.semantic.NamespaceGatheringVisitor;
import edu.kit.compiler.semantic.NamespaceMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

public class IRExpressionVisitorTest {

    private static final String main = "class Main {public static void main(String[] args) {}}";
    private TypeMapper typeMapper;

    private static Reader getReader(String input) {
        return new StringReader(input);
    }

    private ExpressionNode getReturnExpressionFromProgram(String input) {
        JFirmSingleton.initializeFirmLinux();

        ErrorHandler errorHandler = new ErrorHandler(Logger.nullLogger());
        Lexer lexer = new Lexer(getReader(input));
        ProgramNode ast =  new Parser(lexer).parse();

        NamespaceMapper namespaceMapper = new NamespaceMapper();
        NamespaceGatheringVisitor visitor = new NamespaceGatheringVisitor(
                namespaceMapper, lexer.getStringTable(), errorHandler
        );
        ast.accept(visitor);
        DetailedNameTypeAstVisitor dntv = new DetailedNameTypeAstVisitor(namespaceMapper, lexer.getStringTable());
        ast.accept(dntv);

        this.typeMapper = new TypeMapper(namespaceMapper, lexer.getStringTable());
        for (ClassNode classNode: ast.getClasses()) {
            for (MethodNode.DynamicMethodNode m: classNode.getDynamicMethods()) {
                var mapping = LocalVariableCounter.apply(m);
                new TransformContext(
                        typeMapper, classNode, m, mapping, false
                );
                for (StatementNode stmt: m.getStatementBlock().getStatements()) {
                    if(stmt instanceof StatementNode.ReturnStatementNode) {
                        return ((StatementNode.ReturnStatementNode)stmt).getResult().get();
                    }
                }
                throw new IllegalArgumentException("Class contains no return statement node");
            }
        }
        throw new IllegalArgumentException("Class contains no method.");
    }

    @Test
    public void testArrayAccess() throws IOException {
        getReturnExpressionFromProgram(
                "class c { public int a; public int m(int[] array) { return array[5]; } }" + main
        );
    }

}
