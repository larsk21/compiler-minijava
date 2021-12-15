package edu.kit.compiler.codegen;

import static firm.bindings.binding_irnode.ir_opcode.iro_Add;
import static firm.bindings.binding_irnode.ir_opcode.iro_Block;
import static firm.bindings.binding_irnode.ir_opcode.iro_Call;
import static firm.bindings.binding_irnode.ir_opcode.iro_Cmp;
import static firm.bindings.binding_irnode.ir_opcode.iro_Const;
import static firm.bindings.binding_irnode.ir_opcode.iro_Conv;
import static firm.bindings.binding_irnode.ir_opcode.iro_Div;
import static firm.bindings.binding_irnode.ir_opcode.iro_Eor;
import static firm.bindings.binding_irnode.ir_opcode.iro_Load;
import static firm.bindings.binding_irnode.ir_opcode.iro_Minus;
import static firm.bindings.binding_irnode.ir_opcode.iro_Mod;
import static firm.bindings.binding_irnode.ir_opcode.iro_Mul;
import static firm.bindings.binding_irnode.ir_opcode.iro_Phi;
import static firm.bindings.binding_irnode.ir_opcode.iro_Return;
import static firm.bindings.binding_irnode.ir_opcode.iro_Store;
import static firm.bindings.binding_irnode.ir_opcode.iro_Sub;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import edu.kit.compiler.codegen.Operand.Memory;
import edu.kit.compiler.codegen.Operand.Register;
import edu.kit.compiler.codegen.pattern.BinaryInstructionPattern;
import edu.kit.compiler.codegen.pattern.BlockPattern;
import edu.kit.compiler.codegen.pattern.CallPattern;
import edu.kit.compiler.codegen.pattern.ComparisonPattern;
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

public class Patterns implements Pattern<InstructionMatch> {

    private static final Pattern<OperandMatch<Register>> REGISTER = OperandPattern.register();
    private static final Pattern<OperandMatch<Memory>> MEMORY = OperandPattern.memory();

    private final Map<ir_opcode, List<Pattern<InstructionMatch>>> map;

    @Override
    public InstructionMatch match(Node node, MatcherState matcher) {
        var patterns = map.get(node.getOpCode());
        if (patterns != null) {
            for (var pattern : patterns) {
                var shadow = matcher.getShadow();
                var match = pattern.match(node, shadow);
                if (match.matches()) {
                    matcher.update(shadow);
                    return match;
                }
            }
        }
        return InstructionMatch.none();
    }

    public Patterns() {
        map = Map.ofEntries(
                Map.entry(iro_Const, Arrays.asList(new LoadImmediatePattern())),
                Map.entry(iro_Add, Arrays.asList(
                        new BinaryInstructionPattern(iro_Add, "add", REGISTER, REGISTER, true, false))),
                Map.entry(iro_Sub, Arrays.asList(
                        new BinaryInstructionPattern(iro_Sub, "sub", REGISTER, REGISTER, true, false))),
                Map.entry(iro_Mul, Arrays.asList(
                        new BinaryInstructionPattern(iro_Mul, "imul", REGISTER, REGISTER, true, false))),
                Map.entry(iro_Eor, Arrays.asList(
                        new BinaryInstructionPattern(iro_Eor, "xor", REGISTER, REGISTER, true, false))),

                Map.entry(iro_Minus, Arrays.asList(
                        new UnaryInstructionPattern(iro_Minus, "neg", REGISTER, true, false))),

                Map.entry(iro_Cmp, Arrays.asList(new ComparisonPattern())),

                Map.entry(iro_Conv, Arrays.asList(new ConversionPattern())),

                Map.entry(iro_Store, Arrays.asList(
                        new BinaryInstructionPattern(iro_Store, "mov", MEMORY, REGISTER, false, true))),
                Map.entry(iro_Load, Arrays.asList(new LoadMemoryPattern())),

                Map.entry(iro_Div, Arrays.asList(new DivisionPattern(Type.DIV, REGISTER, REGISTER))),
                Map.entry(iro_Mod, Arrays.asList(new DivisionPattern(Type.MOD, REGISTER, REGISTER))),

                Map.entry(iro_Call, Arrays.asList(new CallPattern())),
                Map.entry(iro_Phi, Arrays.asList(new PhiPattern())),
                Map.entry(iro_Block, Arrays.asList(new BlockPattern())),
                Map.entry(iro_Return, Arrays.asList(new ReturnPattern()))
        );
    }
}
