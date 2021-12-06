package edu.kit.compiler.transform;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.Reader;
import java.io.StringReader;
import java.util.HashMap;

import org.junit.jupiter.api.Test;

import edu.kit.compiler.data.ast_nodes.ClassNode;
import edu.kit.compiler.data.ast_nodes.MethodNode;
import edu.kit.compiler.data.ast_nodes.ProgramNode;
import edu.kit.compiler.data.ast_nodes.MethodNode.DynamicMethodNode;
import edu.kit.compiler.lexer.Lexer;
import edu.kit.compiler.lexer.StringTable;
import edu.kit.compiler.logger.Logger;
import edu.kit.compiler.parser.Parser;
import edu.kit.compiler.semantic.ErrorHandler;
import edu.kit.compiler.semantic.NamespaceGatheringVisitor;
import edu.kit.compiler.semantic.NamespaceMapper;
import edu.kit.compiler.transform.TypeMapper.ClassEntry;
import firm.Entity;
import firm.Firm;
import firm.Mode;
import firm.PrimitiveType;

public class TransformContextTest {
    static {
        JFirmSingleton.initializeFirmLinux();
    }

    private static final String main = "class Main {public static void main(String[] args) {}}";
    private TypeMapper typeMapper;
    private StringTable stringTable;
    private ClassNode classNode;

    private MethodNode initMethod(String input) {
        JFirmSingleton.initializeFirmLinux();

        ErrorHandler errorHandler = new ErrorHandler(Logger.nullLogger());
        Lexer lexer = new Lexer(getReader(input));
        ProgramNode node =  new Parser(lexer).parse();

        NamespaceMapper namespaceMapper = new NamespaceMapper();
        NamespaceGatheringVisitor visitor = new NamespaceGatheringVisitor(
            namespaceMapper, lexer.getStringTable(), errorHandler
        );
        node.accept(visitor);
        this.stringTable = lexer.getStringTable();
        this.typeMapper = new TypeMapper(namespaceMapper, lexer.getStringTable());
        for (ClassNode classNode: node.getClasses()) {
            ClassEntry entry = typeMapper.getClassEntry(classNode);
            for (DynamicMethodNode m: classNode.getDynamicMethods()) {
                this.classNode = classNode;
                return m;
            }
        }
        throw new IllegalArgumentException("Class contains no method.");
    }

    @Test
    public void testCreation() {
        MethodNode method = initMethod("class c { public void m() { } }" + main);
        new TransformContext(typeMapper, classNode, method, new HashMap<>(), false);
    }

    @Test
    public void testParams() {
        MethodNode method = initMethod("class c { public void m(int x, int y) { } }" + main);
        TransformContext context =  new TransformContext(
            typeMapper, classNode, method, new HashMap<>(), false
        );
        int x_name = stringTable.insert("x");
        int y_name = stringTable.insert("y");
        assertEquals(0, context.getVariableIndex(x_name));
        assertEquals(1, context.getVariableIndex(y_name));
        context.getThisNode();
    }

    private static Reader getReader(String input) {
        return new StringReader(input);
    }
}
