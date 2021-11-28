package edu.kit.compiler.transform;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

import org.junit.jupiter.api.Test;

import edu.kit.compiler.data.ast_nodes.ClassNode;
import edu.kit.compiler.data.ast_nodes.ProgramNode;
import edu.kit.compiler.data.ast_nodes.MethodNode.DynamicMethodNode;
import edu.kit.compiler.lexer.Lexer;
import edu.kit.compiler.logger.Logger;
import edu.kit.compiler.parser.Parser;
import edu.kit.compiler.semantic.DetailedNameTypeAstVisitor;
import edu.kit.compiler.semantic.ErrorHandler;
import edu.kit.compiler.semantic.NamespaceGatheringVisitor;
import edu.kit.compiler.semantic.NamespaceMapper;

public class IRVisitorTest{
    private static final String main = "class Main {public static void main(String[] args) {}}";
    private TypeMapper typeMapper;

    private TransformContext initContext(String input) {
        JFirmSingleton.initializeFirmLinux();

        ErrorHandler errorHandler = new ErrorHandler(Logger.nullLogger());
        Lexer lexer = new Lexer(getReader(input));
        ProgramNode node =  new Parser(lexer).parse();

        NamespaceMapper namespaceMapper = new NamespaceMapper();
        NamespaceGatheringVisitor visitor = new NamespaceGatheringVisitor(
            namespaceMapper, lexer.getStringTable(), errorHandler
        );
        node.accept(visitor);
        DetailedNameTypeAstVisitor dntv = new DetailedNameTypeAstVisitor(namespaceMapper, lexer.getStringTable());
        node.accept(dntv);
        this.typeMapper = new TypeMapper(namespaceMapper, lexer.getStringTable());
        for (ClassNode classNode: node.getClasses()) {
            for (DynamicMethodNode m: classNode.getDynamicMethods()) {
                var mapping = LocalVariableCounter.apply(m);
                return new TransformContext(
                    typeMapper, classNode, m, mapping, false
                );
            }
        }
        throw new IllegalArgumentException("Class contains no method.");
    }

    @Test
    public void testEmpty() throws IOException {
        var context = initContext("class c { public void m() { } }" + main);
        IRVisitor.apply(context);
    }

    @Test
    public void testWithReturn() throws IOException {
        var context = initContext("class c { public int m() { int i; return i; } }" + main);
        IRVisitor.apply(context);
    }

    @Test
    public void testWithRefReturn() throws IOException {
        var context = initContext("class c { public c m() { c ref; return ref; } }" + main);
        IRVisitor.apply(context);
    }

    @Test
    public void testWithParReturn() throws IOException {
        var context = initContext("class c { public int m(int x) { return x; } }" + main);
        IRVisitor.apply(context);
    }

    @Test
    public void testSimpleIf() throws IOException {
        var context = initContext("class c { public void m(int x) { if (x == 0) return; } }" + main);
        IRVisitor.apply(context);
    }

    @Test
    public void testIfInt() throws IOException {
        var context = initContext("class c { public int m(int x) { int y; if (x == 0) { return x;} else {return 0;} } }" + main);
        IRVisitor.apply(context);
    }

    @Test
    public void testWhile() throws IOException {
        var context = initContext("class c { public int m(int x) { while (x == 0) { if (x > 0) return 1; } return x; } }" + main);
        IRVisitor.apply(context);
    }

    private static Reader getReader(String input) {
        return new StringReader(input);
    }
}
