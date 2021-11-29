package edu.kit.compiler.transform;


import edu.kit.compiler.data.ast_nodes.ExpressionNode;
import firm.Construction;
import firm.Mode;
import firm.nodes.Block;
import firm.nodes.Node;
import lombok.Getter;

public class IRBooleanExpressions {
    public static Node asValue(TransformContext context, ExpressionNode expr) {
        throw new UnsupportedOperationException();
    }

    public static BranchPair asConditional(TransformContext context, ExpressionNode expr) {
        throw new UnsupportedOperationException();
    }


    /**
     * Contains the Blocks for two branches of a conditional jump.
     */
    public static class BranchPair {
        @Getter
        private Block trueBranch;
        @Getter
        private Block falseBranch;

        public BranchPair(Block trueBranch, Block falseBranch) {
            this.trueBranch = trueBranch;
            this.falseBranch = falseBranch;
        }

        public static BranchPair fromConditional(Node cond, Construction constr) {
            Block trueBranch = constr.newBlock();
            Block falseBranch = constr.newBlock();
            trueBranch.addPred(constr.newProj(cond, Mode.getX(), 1));
            falseBranch.addPred(constr.newProj(cond, Mode.getX(), 0));
            return new BranchPair(trueBranch, falseBranch);
        }
    }
}
