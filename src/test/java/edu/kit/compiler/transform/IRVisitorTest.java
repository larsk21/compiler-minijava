package edu.kit.compiler.transform;

import edu.kit.compiler.data.ast_nodes.ProgramNode;
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

public class IRVisitorTest {

    private static final String main = "class Main {public static void main(String[] args) {}}";

    private static Reader getReader(String input) {
        return new StringReader(input);
    }

    private void createGraphsFromCode(String input) {
        JFirmSingleton.initializeFirmLinux();

        ErrorHandler errorHandler = new ErrorHandler(Logger.nullLogger());
        Lexer lexer = new Lexer(getReader(input));
        ProgramNode ast = new Parser(lexer).parse();

        NamespaceMapper namespaceMapper = new NamespaceMapper();
        NamespaceGatheringVisitor visitor = new NamespaceGatheringVisitor(
                namespaceMapper, lexer.getStringTable(), errorHandler
        );
        ast.accept(visitor);
        DetailedNameTypeAstVisitor dntv = new DetailedNameTypeAstVisitor(namespaceMapper, lexer.getStringTable(), errorHandler);
        ast.accept(dntv);

        IRVisitor irv = new IRVisitor(namespaceMapper, lexer.getStringTable());
        try {
            ast.accept(irv);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    @Test
    public void testFieldAccess() throws IOException {
        createGraphsFromCode("class c { public int a; public int m(c obj) { return obj.a; } }" + main);
    }

    @Test
    public void testAssignment() throws IOException {
        createGraphsFromCode("class c { " +
                "public int a;" +
                " public int m(c obj) { " +
                "a = 5; " +
                "return a; " +
                "} " +
                "}" + main);
    }

    @Test
    public void testMethodCall() {
        createGraphsFromCode("class c { public int m(int obj) { return obj; } public int a(int obj) { return m(obj); } }" + main);
    }

    @Test
    public void testThisCall() {
        createGraphsFromCode("class c { public int a; public int m(int obj) { this.a = 5; return obj; } public int a(int obj) { return this.m(obj); } }" + main);
    }

    @Test
    public void testAdd() {
        createGraphsFromCode("class c { public int a; public int z(int obj) { return obj + a; } }" + main);
    }

}
