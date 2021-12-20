package edu.kit.compiler.codegen.pattern;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import edu.kit.compiler.codegen.MatcherState;
import edu.kit.compiler.codegen.Operand;
import edu.kit.compiler.intermediate_lang.Instruction;
import firm.bindings.binding_irnode.ir_opcode;
import firm.nodes.Node;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

public final class ReturnPattern implements Pattern<InstructionMatch> {

    private static final Pattern<OperandMatch<Operand.Register>> REGISTER = OperandPattern.register();

    @Override
    public InstructionMatch match(Node node, MatcherState matcher) {
        if (node.getOpCode() == ir_opcode.iro_Return) {
            Optional<OperandMatch<Operand.Register>> operand = switch (node.getPredCount()) {
                case 1 -> Optional.empty();
                case 2 -> {
                    var match = REGISTER.match(node.getPred(1), matcher);
                    if (match.matches()) {
                        yield Optional.of(match);
                    } else {
                        throw new IllegalStateException("result is not a register");
                    }
                }
                default -> throw new UnsupportedOperationException();
            };
            return new ReturnMatch(node, operand);
        } else {
            return InstructionMatch.none();
        }
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class ReturnMatch extends InstructionMatch.Basic {

        private final Node node;
        private final Optional<OperandMatch<Operand.Register>> source;

        @Override
        public Node getNode() {
            return node;
        }

        @Override
        public List<Instruction> getInstructions() {
            var resultRegister = source.map(match -> match.getOperand().get());
            return List.of(Instruction.newRet(resultRegister));
        }

        @Override
        public Optional<Integer> getTargetRegister() {
            return Optional.empty();
        }

        @Override
        public Stream<Node> getPredecessors() {
            var stream = Stream.of(node.getPred(0));
            if (source.isPresent()) {
                stream = Stream.concat(stream, source.get().getPredecessors());
            }

            return stream;
        }
    }
}
