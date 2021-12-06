package edu.kit.compiler.transform;

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
import firm.Mode;
import firm.nodes.Node;

public class IRPointerVisitorTest {
    private static final String main = "class Main {public static void main(String[] args) {}}";
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
        TypeMapper typeMapper = new TypeMapper(namespaceMapper, lexer.getStringTable());
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
    public void testFieldAccess() {
        ExpressionNode expr = initAndGetExpression(
            "class c { public int a; public int m(c obj) { return obj.a; } }" + main
        );
        createLoadAndReturnNode(expr.accept(new IRPointerVisitor(context)));
    }

    @Test
    public void testMemberAccess() {
        ExpressionNode expr = initAndGetExpression(
            "class c { public int a; public int m(c obj) { return a; } }" + main
        );
        createLoadAndReturnNode(expr.accept(new IRPointerVisitor(context)));
    }

    @Test
    public void testArrayAccess() {
        ExpressionNode expr = initAndGetExpression(
            "class c { public int a; public int m(int[] array) { return array[5]; } }" + main
        );
        createLoadAndReturnNode(expr.accept(new IRPointerVisitor(context)));
    }

    private void createLoadAndReturnNode(Node selOrMember) {
        Construction con = context.getConstruction();
        Node load = con.newLoad(con.getCurrentMem(), selOrMember, Mode.getIs());
        con.setCurrentMem(con.newProj(load, Mode.getM(), 0));
        Node proj = con.newProj(load, Mode.getIs(), 1);
        Node returnNode = con.newReturn(con.getCurrentMem(), new Node[]{proj});
        context.getEndBlock().addPred(returnNode);
    }

    private static Reader getReader(String input) {
        return new StringReader(input);
    }
}
