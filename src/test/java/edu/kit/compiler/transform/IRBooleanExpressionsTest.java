package edu.kit.compiler.transform;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

import org.junit.jupiter.api.Test;

import edu.kit.compiler.data.ast_nodes.ClassNode;
import edu.kit.compiler.data.ast_nodes.ExpressionNode;
import edu.kit.compiler.data.ast_nodes.ProgramNode;
import edu.kit.compiler.data.ast_nodes.StatementNode;
import edu.kit.compiler.data.ast_nodes.StatementNode.ReturnStatementNode;
import edu.kit.compiler.data.ast_nodes.MethodNode.DynamicMethodNode;
import edu.kit.compiler.lexer.Lexer;
import edu.kit.compiler.logger.Logger;
import edu.kit.compiler.parser.Parser;
import edu.kit.compiler.semantic.DetailedNameTypeAstVisitor;
import edu.kit.compiler.semantic.ErrorHandler;
import edu.kit.compiler.semantic.NamespaceGatheringVisitor;
import edu.kit.compiler.semantic.NamespaceMapper;
import firm.Construction;
import firm.Dump;
import firm.nodes.Node;

public class IRBooleanExpressionsTest {
    private static final String main = "class Main {public static void main(String[] args) {}}";
    private TypeMapper typeMapper;
    private TransformContext context;

    private ExpressionNode initAndGetExpression(String input) {
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
                this.context = new TransformContext(
                    typeMapper, classNode, m, mapping, false
                );
                for (StatementNode stmt: m.getStatementBlock().getStatements()) {
                    return ((ReturnStatementNode)stmt).getResult().get();
                }
                throw new IllegalArgumentException("Class contains no method.");
            }
        }
        throw new IllegalArgumentException("Class contains no method.");
    }

    @Test
    public void testAnd() throws IOException {
        ExpressionNode expr = initAndGetExpression(
            "class c { public boolean m(boolean x, boolean y) { return x && y; } }" + main
        );
        Construction con = context.getConstruction();
        Node val = IRBooleanExpressions.asValue(context, expr);
        Node returnNode = con.newReturn(con.getCurrentMem(), new Node[]{val});
        context.getEndBlock().addPred(returnNode);
    }

    @Test
    public void testNots() throws IOException {
        ExpressionNode expr = initAndGetExpression(
            "class c { public boolean m(boolean x, boolean y) { return !((!!x == true) && !y); } }" + main
        );
        Construction con = context.getConstruction();
        Node val = IRBooleanExpressions.asValue(context, expr);
        Node returnNode = con.newReturn(con.getCurrentMem(), new Node[]{val});
        context.getEndBlock().addPred(returnNode);
    }

    @Test
    public void testXorInvert() throws IOException {
        ExpressionNode expr = initAndGetExpression(
            "class c { public boolean m(boolean x, boolean y) { return !(!x == !y); } }" + main
        );
        Construction con = context.getConstruction();
        Node val = IRBooleanExpressions.asValue(context, expr);
        Node returnNode = con.newReturn(con.getCurrentMem(), new Node[]{val});
        context.getEndBlock().addPred(returnNode);
    }

    private static Reader getReader(String input) {
        return new StringReader(input);
    }
}
