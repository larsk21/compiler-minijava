package edu.kit.compiler.codegen.pattern;

import java.util.Arrays;
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

public interface InstructionMatch extends Match {

    public abstract Optional<Integer> getTargetRegister();

    public abstract void accept(MatchVisitor visitor);

    public static InstructionMatch none() {
        return new None();
    }

    public static InstructionMatch empty() {
        return new Empty(Collections.emptyList(), Optional.empty());
    }

    public static InstructionMatch empty(Node predecessor) {
        return new Empty(Arrays.asList(predecessor), Optional.empty());
    }

    public static InstructionMatch empty(int register) {
        return new Empty(Collections.emptyList(), Optional.of(register));
    }

    public static InstructionMatch empty(List<Node> predecessors) {
        return new Empty(predecessors, Optional.empty());
    }

    public static InstructionMatch empty(List<Node> predecessors, int register) {
        return new Empty(predecessors, Optional.of(register));
    }

    @RequiredArgsConstructor(access = AccessLevel.PROTECTED)
    public static final class None extends Match.None implements InstructionMatch {
        @Override
        public Optional<Integer> getTargetRegister() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void accept(MatchVisitor visitor) {
            throw new UnsupportedOperationException();
        }
    }

    public static abstract class Block extends Some {
        @Override
        public void accept(MatchVisitor visitor) {
            visitor.visit(this);
        }
    }

    @RequiredArgsConstructor(access = AccessLevel.PROTECTED)
    public static abstract class Basic extends Some {
        public abstract List<Instruction> getInstructions();

        @Override
        public void accept(MatchVisitor visitor) {
            visitor.visit(this);
        }
    }

    @RequiredArgsConstructor(access = AccessLevel.PROTECTED)
    public static abstract class Condition extends Some {
        public abstract ExitCondition getCondition();

        @Override
        public void accept(MatchVisitor visitor) {
            visitor.visit(this);
        }
    }

    @RequiredArgsConstructor(access = AccessLevel.PROTECTED)
    public static abstract class Phi extends Some {
        public abstract PhiInstruction getPhiInstruction();

        @Override
        public void accept(MatchVisitor visitor) {
            visitor.visit(this);
        }
    }

    public static abstract class Jump extends Some {
        // public abstract
    }

    @RequiredArgsConstructor(access = AccessLevel.PROTECTED)
    public static abstract class Some extends Match.Some implements InstructionMatch {
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class Empty extends Some {

        private final List<Node> predecessors;
        private final Optional<Integer> register;

        @Override
        public Optional<Integer> getTargetRegister() {
            return register;
        }

        @Override
        public Stream<Node> getPredecessors() {
            return predecessors.stream();
        }

        @Override
        public void accept(MatchVisitor visitor) {
            // nothing to do for empty match
        }
    }
}
