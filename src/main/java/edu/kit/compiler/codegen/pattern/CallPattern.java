package edu.kit.compiler.codegen.pattern;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import edu.kit.compiler.codegen.MatcherState;
import edu.kit.compiler.codegen.Operand;
import edu.kit.compiler.codegen.Util;
import edu.kit.compiler.intermediate_lang.Instruction;
import edu.kit.compiler.intermediate_lang.RegisterSize;
import firm.MethodType;
import firm.bindings.binding_irnode.ir_opcode;
import firm.nodes.Address;
import firm.nodes.Call;
import firm.nodes.Node;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CallPattern implements Pattern<InstructionMatch> {

    private final Pattern<OperandMatch<Operand.Register>> pattern = OperandPattern.register();

    @Override
    public InstructionMatch match(Node node, MatcherState matcher) {
        if (node.getOpCode() == ir_opcode.iro_Call) {

            var arguments = new ArrayList<OperandMatch<Operand.Register>>();
            for (int i = 2; i < node.getPredCount(); ++i) {
                var match = pattern.match(node.getPred(i), matcher);

                if (match.matches()) {
                    arguments.add(match);
                } else {
                    throw new IllegalStateException("call arguments must be registers");
                }
            }

            var call = (Call) node;
            var targetRegister = getTarget(call, matcher::getNewRegister);
            return new CallMatch(node, getName(call), arguments, targetRegister);
        } else {
            return InstructionMatch.none();
        }
    }

    private static String getName(Call node) {
        var addr = (Address) node.getPtr();
        return addr.getEntity().getLdName();
    }

    private Optional<Integer> getTarget(Call node, Function<RegisterSize, Integer> register) {
        var type = (MethodType) node.getType();
        return switch (type.getNRess()) {
            case 0 -> Optional.empty();
            case 1 -> {
                var size = Util.getSize(type.getResType(0).getMode());
                yield Optional.of(register.apply(size));
            }
            default -> throw new UnsupportedOperationException();
        };
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public final class CallMatch extends InstructionMatch.Basic {

        private final Node node;
        private final String callName;
        private final List<OperandMatch<Operand.Register>> arguments;
        private final Optional<Integer> targetRegister;

        @Override
        public Node getNode() {
            return node;
        }

        @Override
        public List<Instruction> getInstructions() {
            return List.of(Instruction.newCall(
                    getArguments(), targetRegister, callName));
        }

        @Override
        public Optional<Integer> getTargetRegister() {
            return targetRegister;
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