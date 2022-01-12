package edu.kit.compiler.transform;

import edu.kit.compiler.data.AstVisitor;
import edu.kit.compiler.data.ast_nodes.ExpressionNode;
import edu.kit.compiler.data.ast_nodes.StatementNode;
import edu.kit.compiler.data.ast_nodes.StatementNode.BlockStatementNode;
import edu.kit.compiler.data.ast_nodes.StatementNode.ExpressionStatementNode;
import edu.kit.compiler.data.ast_nodes.StatementNode.IfStatementNode;
import edu.kit.compiler.data.ast_nodes.StatementNode.LocalVariableDeclarationStatementNode;
import edu.kit.compiler.data.ast_nodes.StatementNode.ReturnStatementNode;
import edu.kit.compiler.data.ast_nodes.StatementNode.WhileStatementNode;
import firm.Construction;
import firm.Mode;
import firm.nodes.Block;
import firm.nodes.Node;

/**
 * The statement visitor is called inside the context of a single method.
 * 
 * Return value: whether the visited statement always returns.
 */
public class IRStatementVisitor implements AstVisitor<Boolean> {
    private final TransformContext context;
    private final IRExpressionVisitor expressionVisitor;

    public IRStatementVisitor(TransformContext context) {
        this.context = context;
        this.expressionVisitor = new IRExpressionVisitor(context);
    }

    @Override
    public Boolean visit(BlockStatementNode block) {
        for (StatementNode stmt : block.getStatements()) {
            if (stmt.accept(this)) {
                // we reached a return statement
                return true;
            }
        }
        return false;
    }

    @Override
    public Boolean visit(LocalVariableDeclarationStatementNode stmt) {
        Construction con = context.getConstruction();

        Mode mode = context.getTypeMapper().getMode(stmt.getType());
        int variableIndex = context.getVariableIndex(stmt.getName());

        // initialize variable first, necessary to avoid Unknown node if the
        // init expression references the variable itself (e.g. Foo foo = foo;)
        // Note: it would be cleaner to set the variable to Bad instead, however
        // it would then need to be replaced later instead
        con.setVariable(variableIndex, con.newConst(0, mode));

        if (stmt.getExpression().isPresent()) {
            Node assignedValue = evalExpression(stmt.getExpression().get());
            con.setVariable(variableIndex, assignedValue);
        }

        return false;
    }

    @Override
    public Boolean visit(ReturnStatementNode stmt) {
        Construction con = context.getConstruction();
        Node returnNode;
        if (stmt.getResult().isPresent()) {
            Node returnVal = evalExpression(stmt.getResult().get());
            returnNode = con.newReturn(con.getCurrentMem(), new Node[]{returnVal});
        } else {
            returnNode = con.newReturn(con.getCurrentMem(), new Node[]{});
        }
        context.getEndBlock().addPred(returnNode);
        return true;
    }

    @Override
    public Boolean visit(ExpressionStatementNode stmt) {
        evalExpression(stmt.getExpression());
        return false;
    }

    @Override
    public Boolean visit(IfStatementNode ifStmt) {
        Construction con = context.getConstruction();

        Block finalBlock = con.newBlock();
        Block thenBlock = con.newBlock();
        boolean hasElse = ifStmt.getElseStatement().isPresent();
        if (hasElse) {
            Block elseBlock = con.newBlock();
            IRBooleanExpressions.asConditional(context, ifStmt.getCondition(), thenBlock, elseBlock);
            elseBlock.mature();
            con.setCurrentBlock(elseBlock);
            if (!ifStmt.getElseStatement().get().accept(this)) {
                // block does not return
                Node elseJmp = con.newJmp();
                finalBlock.addPred(elseJmp);
            }
        } else {
            IRBooleanExpressions.asConditional(context, ifStmt.getCondition(), thenBlock, finalBlock);
        }

        thenBlock.mature();
        con.setCurrentBlock(thenBlock);
        if (!ifStmt.getThenStatement().accept(this)) {
            // block does not return
            Node thenJmp = con.newJmp();
            finalBlock.addPred(thenJmp);
        }

        finalBlock.mature();
        con.setCurrentBlock(finalBlock);

        // TODO: can this cause problems?
        return false;
    }

    @Override
    public Boolean visit(WhileStatementNode whileStmt) {
        Construction con = context.getConstruction();
        Node jmp = con.newJmp();
        Block loopHeader = con.newBlock();
        loopHeader.addPred(jmp);
        con.setCurrentBlock(loopHeader);

        // endless loop: ensure construction of mem node and keep block
        con.getCurrentMem();
        con.getGraph().keepAlive(loopHeader);

        Block loopBody = con.newBlock();
        Block loopExit = con.newBlock();
        IRBooleanExpressions.asConditional(context, whileStmt.getCondition(), loopBody, loopExit);

        loopBody.mature();
        con.setCurrentBlock(loopBody);
        if (!whileStmt.getStatement().accept(this)) {
            // block does not return
            Node backJmp = con.newJmp();
            loopHeader.addPred(backJmp);
        }
        loopHeader.mature();

        loopExit.mature();
        con.setCurrentBlock(loopExit);
        return false;
    }

    private Node evalExpression(ExpressionNode expr) {
        return expr.accept(expressionVisitor);
    }
}
