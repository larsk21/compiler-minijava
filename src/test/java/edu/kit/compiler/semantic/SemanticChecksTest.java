package edu.kit.compiler.semantic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.Reader;
import java.io.StringReader;

import org.junit.jupiter.api.Test;

import edu.kit.compiler.data.ast_nodes.ClassNode;
import edu.kit.compiler.data.ast_nodes.MethodNode;
import edu.kit.compiler.data.ast_nodes.ProgramNode;
import edu.kit.compiler.data.ast_nodes.MethodNode.DynamicMethodNode;
import edu.kit.compiler.lexer.Lexer;
import edu.kit.compiler.logger.Logger;
import edu.kit.compiler.parser.Parser;

public class SemanticChecksTest {
    private static final String main = "class Main {public static void main(String[] args) {}}";

    private void checkHasError(String input, boolean hasError, boolean nameTypeCheck) {
        ErrorHandler errorHandler = new ErrorHandler(Logger.nullLogger());
        Lexer lexer = new Lexer(getReader(input));
        ProgramNode node =  new Parser(lexer).parse();

        NamespaceMapper namespaceMapper = new NamespaceMapper();
        NamespaceGatheringVisitor visitor1 = new NamespaceGatheringVisitor(
            namespaceMapper, lexer.getStringTable(), errorHandler
        );
        node.accept(visitor1);
        if (nameTypeCheck) {
            try {
                DetailedNameTypeAstVisitor visitor2 = new DetailedNameTypeAstVisitor(namespaceMapper, lexer.getStringTable(), errorHandler);
                node.accept(visitor2);
            } catch (SemanticException e) {
                assert(hasError);
                return;
            }
        }
        SemanticChecks.applyChecks(node, errorHandler, visitor1.getStringClass());
        assertEquals(hasError, errorHandler.hasError());
    }

    private void checkHasError(String input, boolean hasError) {
        checkHasError(input, hasError, false);
    }

    @Test
    public void testAlwaysReturns() {
        ErrorHandler errorHandler = new ErrorHandler(Logger.nullLogger());
        MethodCheckVisitor visitor = new MethodCheckVisitor(false, errorHandler, null);
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
    public void testReturnError() {
        checkHasError("class c { public int m() { } }" + main, true);
        checkHasError("class c { public int m() { return 0; } }" + main, false);
        checkHasError("class c { public void m() { } }" + main, false);
    }

    @Test
    public void testLValue() {
        checkHasError("class c { public void m() { (x+y)=z; } }" + main, true);
        checkHasError("class c { public void m() { (x.m())=z; } }" + main, true);
        checkHasError("class c { public void m() { this=z; } }" + main, true);
        checkHasError("class c { public void m() { x=y=z; } }" + main, false);
        checkHasError("class c { public void m() { x.a.b=z; } }" + main, false);
        checkHasError("class c { public void m() { x[0][1]=z; } }" + main, false);
    }

    @Test
    public void testExpressionStatement() {
        checkHasError("class c { public void m() { this; } }" + main, true);
        checkHasError("class c { public void m() { x+y; } }" + main, true);
        checkHasError("class c { public void m() { a.z; } }" + main, true);
        checkHasError("class c { public void m() { x=y; } }" + main, false);
        checkHasError("class c { public void m() { x.m(); } }" + main, false);
    }

    @Test
    public void testString() {
        checkHasError("class c { public void m() { String s = new String(); } }" + main, true);
        checkHasError("class c { public void m() { String s; } }" + main, false);
        checkHasError("class c { public void m() { String[] s = new String[1]; } }" + main, false);
        checkHasError("class c { public void m() { String[] s = new String[1]; s[0] = new String(); } }" + main, true);
    }

    @Test
    public void testArgs() {
        checkHasError("class Main {public static void main(String[] args) {String[] args;}}", true, true);
        checkHasError("class Main {public static void main(String[] args) {String s = args[0];}}", true, true);
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
        var parser = new Parser(new Lexer(getReader(input)));
        return parser.parse();
    }

    private static Reader getReader(String input) {
        return new StringReader(input);
    }
}
