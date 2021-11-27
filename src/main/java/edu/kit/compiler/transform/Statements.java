package edu.kit.compiler.transform;

import edu.kit.compiler.data.AstVisitor;
import edu.kit.compiler.data.DataType;
import edu.kit.compiler.data.ast_nodes.ExpressionNode;
import edu.kit.compiler.data.ast_nodes.MethodNode;
import edu.kit.compiler.data.ast_nodes.StatementNode;
import edu.kit.compiler.data.ast_nodes.StatementNode.BlockStatementNode;
import edu.kit.compiler.data.ast_nodes.StatementNode.ExpressionStatementNode;
import edu.kit.compiler.data.ast_nodes.StatementNode.IfStatementNode;
import edu.kit.compiler.data.ast_nodes.StatementNode.LocalVariableDeclarationStatementNode;
import edu.kit.compiler.data.ast_nodes.StatementNode.ReturnStatementNode;
import edu.kit.compiler.data.ast_nodes.StatementNode.WhileStatementNode;
import firm.Construction;
import firm.Mode;
import firm.nodes.Node;
import lombok.AllArgsConstructor;

/**
 * Return value: whether the visited statement was a return statement.
 */
// TODO: maturing
public class Statements implements AstVisitor<Boolean> {

    public static void apply(TransformContext context) {
        Visitor visitor = new Visitor(context);
        context.getMethodNode().getStatementBlock().accept(visitor);

        // add implicit return for void methods
        if (context.getMethodNode().getType().equals(DataType.voidType())) {
            Construction con = context.getConstruction();
            Node returnNode = con.newReturn(con.getCurrentMem(), new Node[]{});
            context.getEndBlock().addPred(returnNode);
        }
    }

    @AllArgsConstructor
    private static final class Visitor implements AstVisitor<Boolean> {
        private TransformContext context;

        public Boolean visit(BlockStatementNode block) { 
            for (StatementNode stmt : block.getStatements()) {
                if (stmt.accept(this)) {
                    // we reached a return statement
                    return true;
                }
            }
            return false;
        }

        public Boolean visit(LocalVariableDeclarationStatementNode stmt) {
            Construction con = context.getConstruction();
            Node assignedVal;
            if (stmt.getExpression().isPresent()) {
                assignedVal = evalExpression(stmt.getExpression().get());
            } else {
                // zero-initialize the variable
                // TODO: does this work for reference types?
                // Note: for debugging, me might want to make no assignment
                assignedVal = con.newConst(0, getMode(stmt.getType()));
            }
            con.setVariable(context.getVariableIndex(stmt.getName()), assignedVal);
            return false;
        }

        public Boolean visit(ReturnStatementNode stmt) { 
            Construction con = context.getConstruction();
            Node mem = con.getCurrentMem();
            Node returnNode;
            if (stmt.getResult().isPresent()) {
                Node returnVal = evalExpression(stmt.getResult().get());
                returnNode = con.newReturn(mem, new Node[]{returnVal});
            } else {
                returnNode = con.newReturn(mem, new Node[]{});
            }
            context.getEndBlock().addPred(returnNode);
            return true;
        }

        public Boolean visit(ExpressionStatementNode stmt) {
            evalExpression(stmt.getExpression());
            return false;
        }

        public Boolean visit(IfStatementNode ifStmt) { throw new UnsupportedOperationException(); }
        public Boolean visit(WhileStatementNode WhileStmt) { throw new UnsupportedOperationException(); }

        private Node evalExpression(ExpressionNode expr) {
            // TODO: call expression visitor
            throw new UnsupportedOperationException();
        }

        private Mode getMode(DataType type) {
            return context.getTypeMapper().getDataType(type).getMode();
        }
    }
}
