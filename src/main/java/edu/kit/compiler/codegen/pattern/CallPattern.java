package edu.kit.compiler.codegen.pattern;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import edu.kit.compiler.codegen.MatcherState;
import edu.kit.compiler.codegen.Operand;
import edu.kit.compiler.intermediate_lang.Instruction;
import firm.MethodType;
import firm.bindings.binding_irnode.ir_opcode;
import firm.nodes.Node;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CallPattern implements Pattern<InstructionMatch> {

    private final Pattern<OperandMatch<Operand.Register>> register = OperandPattern.register();

    @Override
    public InstructionMatch match(Node node, MatcherState matcher) {
        if (node.getOpCode() == ir_opcode.iro_Call) {

            var arguments = new ArrayList<OperandMatch<Operand.Register>>();
            for (int i = 2; i < node.getPredCount(); ++i) {
                var pred = node.getPred(i);
                var match = register.match(pred, matcher);

                if (match.matches()) {
                    arguments.add(match);
                } else {
                    throw new IllegalStateException("call args must be registers");
                }
            }

            var call = (firm.nodes.Call) node;
            var destination = getDestination(call, matcher::getNewRegister);
            return new CallMatch(node, getName(call), arguments, destination);
        } else {
            return InstructionMatch.none();
        }
    }

    private static String getName(firm.nodes.Call node) {
        var addr = (firm.nodes.Address) node.getPtr();
        return addr.getEntity().getLdName();
    }

    private Optional<Integer> getDestination(firm.nodes.Call node, Supplier<Integer> register) {
        return switch (((MethodType) node.getType()).getNRess()) {
            case 0 -> Optional.empty();
            case 1 -> Optional.of(register.get());
            default -> throw new UnsupportedOperationException();
        };
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public final class CallMatch extends InstructionMatch.Basic {

        private final Node node;
        private final String callName;
        private final List<OperandMatch<Operand.Register>> arguments;
        private final Optional<Integer> destination;

        @Override
        public Node getNode() {
            return node;
        }

        @Override
        public List<Instruction> getInstructions() {
            return Arrays.asList(Instruction.newCall(
                    getArguments(), destination, callName));
        }

        @Override
        public Optional<Integer> getTargetRegister() {
            return destination;
        }

        @Override
        public Stream<Node> getPredecessors() {
            return Stream.concat(Stream.of(node.getPred(0)),
                    arguments.stream().flatMap(m -> m.getPredecessors()));
        }

        private List<Integer> getArguments() {
            return arguments.stream()
                    .map(m -> m.getOperand().get())
                    .collect(Collectors.toList());
        }
    }
}