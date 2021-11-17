package edu.kit.compiler.semantic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.Reader;
import java.io.StringReader;

import org.junit.jupiter.api.Test;

import edu.kit.compiler.data.ast_nodes.ClassNode;
import edu.kit.compiler.data.ast_nodes.MethodNode;
import edu.kit.compiler.data.ast_nodes.ProgramNode;
import edu.kit.compiler.data.ast_nodes.MethodNode.DynamicMethodNode;
import edu.kit.compiler.io.ReaderCharIterator;
import edu.kit.compiler.lexer.Lexer;
import edu.kit.compiler.logger.Logger;
import edu.kit.compiler.parser.Parser;

public class SemanticChecksTest {
    private void checkHasError(ProgramNode node, boolean hasError) {
        ErrorHandler errorHandler = new ErrorHandler(Logger.nullLogger());
        SemanticChecks.applyChecks(node, errorHandler);
        assertEquals(hasError, errorHandler.hasError());
    }

    @Test
    public void testAlwaysReturns() {
        ErrorHandler errorHandler = new ErrorHandler(Logger.nullLogger());
        MethodCheckVisitor visitor = new MethodCheckVisitor(false, errorHandler);
        assertEquals(
            false,
            getMethod("class c { public void m() { } }").getStatementBlock().accept(visitor)
        );
        assertEquals(
            true,
            getMethod("class c { public void m() { return; } }").getStatementBlock().accept(visitor)
        );
        assertEquals(
            false,
            getMethod("class c { public void m() { if(x) return; } }").getStatementBlock().accept(visitor)
        );
        assertEquals(
            false,
            getMethod("class c { public void m() { if(x) ; else return; } }").getStatementBlock().accept(visitor)
        );
        assertEquals(
            true,
            getMethod("class c { public void m() { if(x) return; else return; } }").getStatementBlock().accept(visitor)
        );
        assertEquals(
            true,
            getMethod("class c { public void m() { if(x) ; else ; return; } }").getStatementBlock().accept(visitor)
        );
        assertEquals(
            false,
            getMethod("class c { public void m() { while(x) return; } }").getStatementBlock().accept(visitor)
        );
        assertEquals(
            true,
            getMethod("class c { public void m() { return x; z=y; } }").getStatementBlock().accept(visitor)
        );
    }

    @Test
    public void testLValue() {
        checkHasError(createAst("class c { public void m() { (x+y)=z; } }"), true);
        checkHasError(createAst("class c { public void m() { (x.m())=z; } }"), true);
        checkHasError(createAst("class c { public void m() { this=z; } }"), true);
        checkHasError(createAst("class c { public void m() { x=y=z; } }"), false);
        checkHasError(createAst("class c { public void m() { x.a.b=z; } }"), false);
        checkHasError(createAst("class c { public void m() { x[0][1]=z; } }"), false);
    }

    @Test
    public void testExpressionStatement() {
        checkHasError(createAst("class c { public void m() { this; } }"), true);
        checkHasError(createAst("class c { public void m() { x+y; } }"), true);
        checkHasError(createAst("class c { public void m() { a.z; } }"), true);
        checkHasError(createAst("class c { public void m() { x=y; } }"), false);
        checkHasError(createAst("class c { public void m() { x.m(); } }"), false);
    }

    private MethodNode getMethod(String input) {
        ProgramNode node = createAst(input);
        for (ClassNode classNode: node.getClasses()) {
            for (DynamicMethodNode m: classNode.getDynamicMethods()) {
                return m;
            }
        }
        throw new IllegalArgumentException("Class contains no method.");
    }

    private ProgramNode createAst(String input) {
        var parser = new Parser(new Lexer(getIterator(input)));
        return parser.parse();
    }

    private static ReaderCharIterator getIterator(String input) {
        return getIterator(new StringReader(input));
    }

    private static ReaderCharIterator getIterator(Reader reader) {
        return new ReaderCharIterator(reader);
    }
}
