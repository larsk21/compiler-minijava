package edu.kit.compiler.intermediate_lang;

import edu.kit.compiler.intermediate_lang.data.operations.IL_Op;
import edu.kit.compiler.intermediate_lang.data.operations.IL_Op2;
import edu.kit.compiler.intermediate_lang.linscan.Interval;
import firm.nodes.*;

import java.util.LinkedList;
import java.util.List;

/**
 * visitor that can walk our firm graph and generate intermediate representation of our backend x64 lines.
 *
 * this needs to be changed probably
 */
public class IntermediateLangVisitor implements NodeVisitor {

    private final List<IL_Op> il_ops = new LinkedList<>();
    private final List<Interval> intervals = new LinkedList<>();

    public IntermediateLangVisitor() {

    }

    public void defaultVisit(Node n) {
        throw new UnsupportedOperationException("not supported firm node for backend generation " + n.toString());
    }

    @Override
    public void visit(Add node) {
        Node lhs = node.getLeft();
        lhs.accept(this);
        IL_Op2 addNode = new IL_Op2();
    }

    @Override
    public void visit(Address node) {
        defaultVisit(node);
    }

    @Override
    public void visit(Align node) {
        defaultVisit(node);
    }

    @Override
    public void visit(Alloc node) {
        defaultVisit(node);
    }

    @Override
    public void visit(Anchor node) {
        defaultVisit(node);
    }

    @Override
    public void visit(And node) {
        defaultVisit(node);
    }

    @Override
    public void visit(Bad node) {
        defaultVisit(node);
    }

    @Override
    public void visit(Bitcast node) {
        defaultVisit(node);
    }

    @Override
    public void visit(Block node) {
        defaultVisit(node);
    }

    @Override
    public void visit(Builtin node) {
        defaultVisit(node);
    }

    @Override
    public void visit(Call node) {
        defaultVisit(node);
    }

    @Override
    public void visit(Cmp node) {
        defaultVisit(node);
    }

    @Override
    public void visit(Cond node) {
        defaultVisit(node);
    }

    @Override
    public void visit(Confirm node) {
        defaultVisit(node);
    }

    @Override
    public void visit(Const node) {
        defaultVisit(node);
    }

    @Override
    public void visit(Conv node) {
        defaultVisit(node);
    }

    @Override
    public void visit(CopyB node) {
        defaultVisit(node);
    }

    @Override
    public void visit(Deleted node) {
        defaultVisit(node);
    }

    @Override
    public void visit(Div node) {
        defaultVisit(node);
    }

    @Override
    public void visit(Dummy node) {
        defaultVisit(node);
    }

    @Override
    public void visit(End node) {
        defaultVisit(node);
    }

    @Override
    public void visit(Eor node) {
        defaultVisit(node);
    }

    @Override
    public void visit(Free node) {
        defaultVisit(node);
    }

    @Override
    public void visit(IJmp node) {
        defaultVisit(node);
    }

    @Override
    public void visit(Id node) {
        defaultVisit(node);
    }

    @Override
    public void visit(Jmp node) {
        defaultVisit(node);
    }

    @Override
    public void visit(Load node) {
        defaultVisit(node);
    }

    @Override
    public void visit(Member node) {
        defaultVisit(node);
    }

    @Override
    public void visit(Minus node) {
        defaultVisit(node);
    }

    @Override
    public void visit(Mod node) {
        defaultVisit(node);
    }

    @Override
    public void visit(Mul node) {
        defaultVisit(node);
    }

    @Override
    public void visit(Mulh node) {
        defaultVisit(node);
    }

    @Override
    public void visit(Mux node) {
        defaultVisit(node);
    }

    @Override
    public void visit(NoMem node) {
        defaultVisit(node);
    }

    @Override
    public void visit(Not node) {
        defaultVisit(node);
    }

    @Override
    public void visit(Offset node) {
        defaultVisit(node);
    }

    @Override
    public void visit(Or node) {
        defaultVisit(node);
    }

    @Override
    public void visit(Phi node) {
        defaultVisit(node);
    }

    @Override
    public void visit(Pin node) {
        defaultVisit(node);
    }

    @Override
    public void visit(Proj node) {
        defaultVisit(node);
    }

    @Override
    public void visit(Raise node) {
        defaultVisit(node);
    }

    @Override
    public void visit(Return node) {
        defaultVisit(node);
    }

    @Override
    public void visit(Sel node) {
        defaultVisit(node);
    }

    @Override
    public void visit(Shl node) {
        defaultVisit(node);
    }

    @Override
    public void visit(Shr node) {
        defaultVisit(node);
    }

    @Override
    public void visit(Shrs node) {
        defaultVisit(node);
    }

    @Override
    public void visit(Size node) {
        defaultVisit(node);
    }

    @Override
    public void visit(Start node) {
        defaultVisit(node);
    }

    @Override
    public void visit(Store node) {
        defaultVisit(node);
    }

    @Override
    public void visit(Sub node) {
        defaultVisit(node);
    }

    @Override
    public void visit(Switch node) {
        defaultVisit(node);
    }

    @Override
    public void visit(Sync node) {
        defaultVisit(node);
    }

    @Override
    public void visit(Tuple node) {
        defaultVisit(node);
    }

    @Override
    public void visit(Unknown node) {
        defaultVisit(node);
    }

    @Override
    public void visitUnknown(Node node) {
        defaultVisit(node);
    }
}

