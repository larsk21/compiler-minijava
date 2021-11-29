package edu.kit.compiler.transform;

import edu.kit.compiler.data.AstVisitor;
import edu.kit.compiler.data.DataType;
import edu.kit.compiler.data.ast_nodes.ExpressionNode;
import edu.kit.compiler.data.ast_nodes.StatementNode;
import edu.kit.compiler.data.ast_nodes.StatementNode.BlockStatementNode;
import edu.kit.compiler.data.ast_nodes.StatementNode.ExpressionStatementNode;
import edu.kit.compiler.data.ast_nodes.StatementNode.IfStatementNode;
import edu.kit.compiler.data.ast_nodes.StatementNode.LocalVariableDeclarationStatementNode;
import edu.kit.compiler.data.ast_nodes.StatementNode.ReturnStatementNode;
import edu.kit.compiler.data.ast_nodes.StatementNode.WhileStatementNode;
import edu.kit.compiler.semantic.DefinitionKind;
import firm.Construction;
import firm.Mode;
import firm.Relation;
import firm.nodes.Block;
import firm.nodes.Node;

/**
 * The statement visitor is called inside the context of a single method.
 * 
 * Return value: whether the visited statement always returns.
 */
public class IRStatementVisitor implements AstVisitor<Boolean> {
    private final TransformContext context;

    public IRStatementVisitor(TransformContext context) {
        this.context = context;
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
        Node assignedVal;
        if (stmt.getExpression().isPresent()) {
            assignedVal = evalExpression(stmt.getExpression().get());
        } else {
            // zero-initialize the variable
            // Note: for debugging, me might want to make no assignment
            assignedVal = con.newConst(0, getMode(stmt.getType()));
        }
        con.setVariable(context.getVariableIndex(stmt.getName()), assignedVal);
        return false;
    }

    @Override
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
        Construction con = context.getConstruction();
        if (expr instanceof ExpressionNode.IdentifierExpressionNode) {
            ExpressionNode.IdentifierExpressionNode id = (ExpressionNode.IdentifierExpressionNode)expr;
            if (id.getDefinition().getKind() == DefinitionKind.LocalVariable) {
                Mode mode = getMode(id.getResultType());
                return con.getVariable(context.getVariableIndex(id.getIdentifier()), mode);
            } else if (id.getDefinition().getKind() == DefinitionKind.Parameter) {
                return context.createParamNode(id.getIdentifier());
            } else {
                throw new UnsupportedOperationException();
            }
        }
        // TODO: call expression visitor
        return con.newConst(1, getMode(expr.getResultType()));
    }

    private Mode getMode(DataType type) {
        return context.getTypeMapper().getDataType(type).getMode();
    }
}
