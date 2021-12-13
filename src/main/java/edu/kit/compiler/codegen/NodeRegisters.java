package edu.kit.compiler.codegen;

import java.util.HashMap;
import java.util.Map;

import firm.nodes.Node;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;

public class NodeRegisters {

    private final Map<Integer, Integer> registers = new HashMap<>();

    private int registerCount;

    private NodeRegisters() {
        this.registerCount = 0;
    }

    public NodeRegisters(int numParameters) {
        registerCount = numParameters;
    }

    // todo checked getRegister (throws if not present)

    public int getRegister(Node node) {
        return registers.getOrDefault(node.getNr(), -1);
    }

    public void setRegister(Node node, int register) {
        assert !registers.containsKey(node.getNr());
        registers.put(node.getNr(), register);
    }

    public int newRegister() {
        return registerCount++;
    }

    public Clone clone() {
        return new Clone(registerCount);
    }

    public void update(Clone clone) {
        this.registerCount = clone.registerCount;
    }

    @Override
    public String toString() {
        return registers.toString();
    }

    // todo this nested class is a pretty dirty workaround
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public final class Clone extends NodeRegisters {
        private int registerCount;

        @Override
        public int getRegister(Node node) {
            return NodeRegisters.this.getRegister(node);
        }

        @Override
        public void setRegister(Node node, int register) {
            throw new UnsupportedOperationException("not allowed to set registers on clone");
        }
        
        @Override
        public int newRegister() {
            return this.registerCount++;
        }
    }
}
