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

    public static void asConditional(TransformContext context, ExpressionNode expr,
                                     Block trueBranch, Block falseBranch) {
        throw new UnsupportedOperationException();
    }


}
