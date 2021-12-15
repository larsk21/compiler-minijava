package edu.kit.compiler.codegen;

import static firm.bindings.binding_irnode.ir_opcode.*;

import java.util.List;
import java.util.Map;

import edu.kit.compiler.codegen.Operand.Memory;
import edu.kit.compiler.codegen.Operand.Register;
import edu.kit.compiler.codegen.pattern.BinaryInstructionPattern;
import edu.kit.compiler.codegen.pattern.BlockPattern;
import edu.kit.compiler.codegen.pattern.CallPattern;
import edu.kit.compiler.codegen.pattern.ComparisonPattern;
import edu.kit.compiler.codegen.pattern.ConditionPattern;
import edu.kit.compiler.codegen.pattern.ConversionPattern;
import edu.kit.compiler.codegen.pattern.DivisionPattern;
import edu.kit.compiler.codegen.pattern.DivisionPattern.Type;
import edu.kit.compiler.codegen.pattern.InstructionMatch;
import edu.kit.compiler.codegen.pattern.LoadImmediatePattern;
import edu.kit.compiler.codegen.pattern.LoadMemoryPattern;
import edu.kit.compiler.codegen.pattern.OperandMatch;
import edu.kit.compiler.codegen.pattern.OperandPattern;
import edu.kit.compiler.codegen.pattern.Pattern;
import edu.kit.compiler.codegen.pattern.PhiPattern;
import edu.kit.compiler.codegen.pattern.ReturnPattern;
import edu.kit.compiler.codegen.pattern.UnaryInstructionPattern;
import firm.bindings.binding_irnode.ir_opcode;
import firm.nodes.Node;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

public class Patterns implements Pattern<InstructionMatch> {

    private static final Pattern<OperandMatch<Register>> REGISTER = OperandPattern.register();
    private static final Pattern<OperandMatch<Memory>> MEMORY = OperandPattern.memory();

    private final Map<ir_opcode, Pattern<InstructionMatch>> map;

    @Override
    public InstructionMatch match(Node node, MatcherState matcher) {
        var pattern = map.get(node.getOpCode());
        if (pattern != null) {
            return pattern.match(node, matcher);
        } else {
            return InstructionMatch.none();
        }
    }

    public Patterns() {
        map = Map.ofEntries(
                Map.entry(iro_Const, new LoadImmediatePattern()),
                Map.entry(iro_Add, new BinaryInstructionPattern(iro_Add, "add", REGISTER, REGISTER, true, false)),
                Map.entry(iro_Sub, new BinaryInstructionPattern(iro_Sub, "sub", REGISTER, REGISTER, true, false)),
                Map.entry(iro_Mul, new BinaryInstructionPattern(iro_Mul, "imul", REGISTER, REGISTER, true, false)),
                Map.entry(iro_Eor, new BinaryInstructionPattern(iro_Eor, "xor", REGISTER, REGISTER, true, false)),

                Map.entry(iro_Div, new DivisionPattern(Type.DIV, REGISTER, REGISTER)),
                Map.entry(iro_Mod, new DivisionPattern(Type.MOD, REGISTER, REGISTER)),

                Map.entry(iro_Minus, new UnaryInstructionPattern(iro_Minus, "neg", REGISTER, true, false)),

                Map.entry(iro_Cmp, new ComparisonPattern()),

                Map.entry(iro_Conv, new ConversionPattern()),

                Map.entry(iro_Store, new BinaryInstructionPattern(iro_Store, "mov", MEMORY, REGISTER, false, true)),
                Map.entry(iro_Load, new LoadMemoryPattern()),

                Map.entry(iro_Call, new CallPattern()),
                Map.entry(iro_Phi, new PhiPattern()),
                Map.entry(iro_Block, new BlockPattern()),
                Map.entry(iro_Return, new ReturnPattern()),

                Map.entry(iro_Jmp, new ConditionPattern()),
                Map.entry(iro_Cond, new ConditionPattern()),

                // trivial pattern for nodes without predecessors
                Map.entry(iro_Start, new EmptyPattern()),
                Map.entry(iro_End, new EmptyPattern()),
                Map.entry(iro_Address, new EmptyPattern()));
    }

    private static final class EmptyPattern implements Pattern<InstructionMatch> {
        @Override
        public InstructionMatch match(Node node, MatcherState matcher) {
            return InstructionMatch.empty(node);
        }
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private static final class CompoundPattern implements Pattern<InstructionMatch> {

        private final List<Pattern<InstructionMatch>> patterns;

        @Override
        public InstructionMatch match(Node node, MatcherState matcher) {
            for (var pattern : patterns) {
                var shadow = matcher.getShadow();
                var match = pattern.match(node, shadow);
                if (match.matches()) {
                    matcher.update(shadow);
                    return match;
                }
            }
            return InstructionMatch.none();
        }

    }
}
