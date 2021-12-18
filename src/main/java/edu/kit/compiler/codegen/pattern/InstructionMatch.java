package edu.kit.compiler.codegen.pattern;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import edu.kit.compiler.codegen.ExitCondition;
import edu.kit.compiler.codegen.PhiInstruction;
import edu.kit.compiler.intermediate_lang.Instruction;
import firm.nodes.Node;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

/**
 * A match for an instruction, associated with a specific node int he Firm
 * graph. May optionally write to a target register. There a number of special
 * instruction types, for which a visitor pattern has been implemented.
 * 
 * - Basic: Can be translated into a list of IL instruction
 * - Block: Related to a Firm Block, needed for its control flow predecessors
 * - Phi: Related to a Firm Phi, can be translated to a special PhiInstruction
 * - Condition: Can be translated into an exit condition for a basic block
 */
public interface InstructionMatch extends Match {

    /**
     * Returns the associated Firm node.
     */
    Node getNode();

    /**
     * Returns the target register of the instruction if one exists.
     */
    Optional<Integer> getTargetRegister();

    void accept(InstructionMatchVisitor visitor);

    public static InstructionMatch none() {
        return new None();
    }

    public static InstructionMatch empty(Node node) {
        return new Empty(node, Collections.emptyList(), Optional.empty());
    }

    public static InstructionMatch empty(Node node, Node predecessor) {
        return new Empty(node, List.of(predecessor), Optional.empty());
    }

    public static InstructionMatch empty(Node node, int register) {
        return new Empty(node, Collections.emptyList(), Optional.of(register));
    }

    public static InstructionMatch empty(Node node, List<Node> predecessors) {
        return new Empty(node, predecessors, Optional.empty());
    }

    public static InstructionMatch empty(Node node, List<Node> predecessors, int register) {
        return new Empty(node, predecessors, Optional.of(register));
    }

    public static abstract class Block extends Some {
        @Override
        public abstract firm.nodes.Block getNode();

        @Override
        public void accept(InstructionMatchVisitor visitor) {
            visitor.visit(this);
        }
    }

    @RequiredArgsConstructor(access = AccessLevel.PROTECTED)
    public static abstract class Basic extends Some {
        public abstract List<Instruction> getInstructions();

        @Override
        public void accept(InstructionMatchVisitor visitor) {
            visitor.visit(this);
        }
    }

    @RequiredArgsConstructor(access = AccessLevel.PROTECTED)
    public static abstract class Condition extends Some {
        public abstract ExitCondition getCondition();

        @Override
        public void accept(InstructionMatchVisitor visitor) {
            visitor.visit(this);
        }
    }

    @RequiredArgsConstructor(access = AccessLevel.PROTECTED)
    public static abstract class Phi extends Some {
        public abstract PhiInstruction getPhiInstruction();

        @Override
        public void accept(InstructionMatchVisitor visitor) {
            visitor.visit(this);
        }
    }

    @RequiredArgsConstructor(access = AccessLevel.PROTECTED)
    public static abstract class Some implements InstructionMatch {
        @Override
        public boolean matches() {
            return true;
        }
    }

    @RequiredArgsConstructor(access = AccessLevel.PROTECTED)
    public static final class None implements InstructionMatch {
        @Override
        public boolean matches() {
            return false;
        }

        @Override
        public Node getNode() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Integer> getTargetRegister() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void accept(InstructionMatchVisitor visitor) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Stream<Node> getPredecessors() {
            throw new UnsupportedOperationException();
        }
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class Empty extends Some {

        private final Node node;
        private final List<Node> predecessors;
        private final Optional<Integer> register;

        @Override
        public Node getNode() {
            return node;
        }

        @Override
        public Optional<Integer> getTargetRegister() {
            return register;
        }

        @Override
        public Stream<Node> getPredecessors() {
            return predecessors.stream();
        }

        @Override
        public void accept(InstructionMatchVisitor visitor) {
            // nothing to do for empty match
        }
    }
}
