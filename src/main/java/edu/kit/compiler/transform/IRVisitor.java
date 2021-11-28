package edu.kit.compiler.transform;

import java.util.Optional;

import edu.kit.compiler.data.AstVisitor;
import edu.kit.compiler.data.DataType;
import edu.kit.compiler.data.ast_nodes.ExpressionNode;
import edu.kit.compiler.data.ast_nodes.MethodNode;
import edu.kit.compiler.data.ast_nodes.StatementNode;
import edu.kit.compiler.data.ast_nodes.ExpressionNode.IdentifierExpressionNode;
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
import lombok.AllArgsConstructor;

/**
 * Return value: whether the visited statement always returns.
 */
public class IRVisitor implements AstVisitor<Boolean> {

    public static void apply(TransformContext context) {
        Construction con = context.getConstruction();
        Visitor visitor = new Visitor(context);
        boolean returns = context.getMethodNode().getStatementBlock().accept(visitor);

        // add implicit return for void methods
        if (!returns && context.getMethodNode().getType().equals(DataType.voidType())) {
            Node returnNode = con.newReturn(con.getCurrentMem(), new Node[]{});
            context.getEndBlock().addPred(returnNode);
        }
        con.finish();
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

        public Boolean visit(IfStatementNode ifStmt) {
            Construction con = context.getConstruction();
            // TODO: proper condition evaluation
            ExpressionNode cond = ifStmt.getCondition();
            Node left = evalExpression(cond);
            Node right = con.newConst(0, getMode(cond.getResultType()));
            Node cmp = con.newCmp(left, right, Relation.LessGreater);
            Node getCond = con.newCond(cmp);
            Node projTrue = con.newProj(getCond, Mode.getX(), 1);
            Node projFalse = con.newProj(getCond, Mode.getX(), 0);

            Block finalBlock = con.newBlock();

            boolean hasElse = ifStmt.getElseStatement().isPresent();
            if (hasElse) {
                // else block
                Block elseBlock = con.newBlock();
                elseBlock.addPred(projFalse);
                elseBlock.mature();
                con.setCurrentBlock(elseBlock);
                if (!ifStmt.getElseStatement().get().accept(this)) {
                    // block does not return
                    Node elseJmp = con.newJmp();
                    finalBlock.addPred(elseJmp);
                }
            } else {
                finalBlock.addPred(projFalse);
            }

            // then block
            Block thenBlock = con.newBlock();
            thenBlock.addPred(projTrue);
            thenBlock.mature();
            con.setCurrentBlock(thenBlock);
            if (!ifStmt.getThenStatement().accept(this)) {
                // block does not return
                Node thenJmp = con.newJmp();
                finalBlock.addPred(thenJmp);
            }

            // final block
            finalBlock.mature();
            con.setCurrentBlock(finalBlock);

            // TODO: can this cause problems?
            return false;
        }

        public Boolean visit(WhileStatementNode whileStmt) {
            Construction con = context.getConstruction();
            Node jmp = con.newJmp();
            Block loopHeader = con.newBlock();
            loopHeader.addPred(jmp);
            con.setCurrentBlock(loopHeader);

            // endless loop: ensure construction of mem node and keep block
            con.getCurrentMem();
            con.getGraph().keepAlive(loopHeader);

            // TODO: proper condition evaluation
            ExpressionNode cond = whileStmt.getCondition();
            Node left = evalExpression(cond);
            Node right = con.newConst(0, getMode(cond.getResultType()));
            Node cmp = con.newCmp(left, right, Relation.LessGreater);
            Node getCond = con.newCond(cmp);
            Node projTrue = con.newProj(getCond, Mode.getX(), 1);
            Node projFalse = con.newProj(getCond, Mode.getX(), 0);

            Block loopBody = con.newBlock();
            loopBody.addPred(projTrue);
            loopBody.mature();
            con.setCurrentBlock(loopBody);
            if (!whileStmt.getStatement().accept(this)) {
                // block does not return
                Node backJmp = con.newJmp();
                loopHeader.addPred(backJmp);
            }
            loopHeader.mature();

            Block nextBlock = con.newBlock();
            nextBlock.addPred(projFalse);
            nextBlock.mature();
            con.setCurrentBlock(nextBlock);
            return false;
         }

        private Node evalExpression(ExpressionNode expr) {
            Construction con = context.getConstruction();
            if (expr instanceof IdentifierExpressionNode) {
                IdentifierExpressionNode id = (IdentifierExpressionNode)expr;
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
}
