package edu.kit.compiler.codegen.pattern;

public interface InstructionMatchVisitor {
    void visit(InstructionMatch.Block match);

    void visit(InstructionMatch.Basic match);

    void visit(InstructionMatch.Phi match);

    void visit(InstructionMatch.Condition match);
}
