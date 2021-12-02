package edu.kit.compiler.optimizations;

import firm.nodes.*;

/**
 * Firm Node visitor with default implementations from all methods except
 * `visitUnknown`. The default implementation of the other methods calls
 * `visitUnknown` with the respective node.
 */
public interface PartialNodeVisitor extends NodeVisitor {

    @Override
    public default void visit(Add node) {
        visitUnknown(node);
    }

    @Override
    public default void visit(Address node) {
        visitUnknown(node);
    }

    @Override
    public default void visit(Align node) {
        visitUnknown(node);
    }

    @Override
    public default void visit(Alloc node) {
        visitUnknown(node);
    }

    @Override
    public default void visit(Anchor node) {
        visitUnknown(node);
    }

    @Override
    public default void visit(And node) {
        visitUnknown(node);
    }

    @Override
    public default void visit(Bad node) {
        visitUnknown(node);
    }

    @Override
    public default void visit(Bitcast node) {
        visitUnknown(node);
    }

    @Override
    public default void visit(Block node) {
        visitUnknown(node);
    }

    @Override
    public default void visit(Builtin node) {
        visitUnknown(node);
    }

    @Override
    public default void visit(Call node) {
        visitUnknown(node);
    }

    @Override
    public default void visit(Cmp node) {
        visitUnknown(node);
    }

    @Override
    public default void visit(Cond node) {
        visitUnknown(node);
    }

    @Override
    public default void visit(Confirm node) {
        visitUnknown(node);
    }

    @Override
    public default void visit(Const node) {
        visitUnknown(node);
    }

    @Override
    public default void visit(Conv node) {
        visitUnknown(node);
    }

    @Override
    public default void visit(CopyB node) {
        visitUnknown(node);
    }

    @Override
    public default void visit(Deleted node) {
        visitUnknown(node);
    }

    @Override
    public default void visit(Div node) {
        visitUnknown(node);
    }

    @Override
    public default void visit(Dummy node) {
        visitUnknown(node);
    }

    @Override
    public default void visit(End node) {
        visitUnknown(node);
    }

    @Override
    public default void visit(Eor node) {
        visitUnknown(node);
    }

    @Override
    public default void visit(Free node) {
        visitUnknown(node);
    }

    @Override
    public default void visit(IJmp node) {
        visitUnknown(node);
    }

    @Override
    public default void visit(Id node) {
        visitUnknown(node);
    }

    @Override
    public default void visit(Jmp node) {
        visitUnknown(node);
    }

    @Override
    public default void visit(Load node) {
        visitUnknown(node);
    }

    @Override
    public default void visit(Member node) {
        visitUnknown(node);
    }

    @Override
    public default void visit(Minus node) {
        visitUnknown(node);
    }

    @Override
    public default void visit(Mod node) {
        visitUnknown(node);
    }

    @Override
    public default void visit(Mul node) {
        visitUnknown(node);
    }

    @Override
    public default void visit(Mulh node) {
        visitUnknown(node);
    }

    @Override
    public default void visit(Mux node) {
        visitUnknown(node);
    }

    @Override
    public default void visit(NoMem node) {
        visitUnknown(node);
    }

    @Override
    public default void visit(Not node) {
        visitUnknown(node);
    }

    @Override
    public default void visit(Offset node) {
        visitUnknown(node);
    }

    @Override
    public default void visit(Or node) {
        visitUnknown(node);
    }

    @Override
    public default void visit(Phi node) {
        visitUnknown(node);
    }

    @Override
    public default void visit(Pin node) {
        visitUnknown(node);
    }

    @Override
    public default void visit(Proj node) {
        visitUnknown(node);
    }

    @Override
    public default void visit(Raise node) {
        visitUnknown(node);
    }

    @Override
    public default void visit(Return node) {
        visitUnknown(node);
    }

    @Override
    public default void visit(Sel node) {
        visitUnknown(node);
    }

    @Override
    public default void visit(Shl node) {
        visitUnknown(node);
    }

    @Override
    public default void visit(Shr node) {
        visitUnknown(node);
    }

    @Override
    public default void visit(Shrs node) {
        visitUnknown(node);
    }

    @Override
    public default void visit(Size node) {
        visitUnknown(node);
    }

    @Override
    public default void visit(Start node) {
        visitUnknown(node);
    }

    @Override
    public default void visit(Store node) {
        visitUnknown(node);
    }

    @Override
    public default void visit(Sub node) {
        visitUnknown(node);
    }

    @Override
    public default void visit(Switch node) {
        visitUnknown(node);
    }

    @Override
    public default void visit(Sync node) {
        visitUnknown(node);
    }

    @Override
    public default void visit(Tuple node) {
        visitUnknown(node);
    }

    @Override
    public default void visit(Unknown node) {
        visitUnknown(node);
    }

}
