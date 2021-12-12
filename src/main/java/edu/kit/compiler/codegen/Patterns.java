package edu.kit.compiler.codegen;

import static firm.bindings.binding_irnode.ir_opcode.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import edu.kit.compiler.codegen.pattern.BinaryInstruction;
import edu.kit.compiler.codegen.pattern.Conversion;
import edu.kit.compiler.codegen.pattern.Division;
import edu.kit.compiler.codegen.pattern.InstructionMatch;
import edu.kit.compiler.codegen.pattern.LoadImmediate;
import edu.kit.compiler.codegen.pattern.LoadMemory;
import edu.kit.compiler.codegen.pattern.MemoryPattern;
import edu.kit.compiler.codegen.pattern.Pattern;
import edu.kit.compiler.codegen.pattern.RegisterPattern;
import edu.kit.compiler.codegen.pattern.ReturnPattern;
import edu.kit.compiler.codegen.pattern.UnaryInstruction;
import edu.kit.compiler.codegen.pattern.Division.Type;
import firm.bindings.binding_irnode.ir_opcode;
import firm.nodes.Node;

public class Patterns {
    private static final RegisterPattern REGISTER = new RegisterPattern();
    private static final MemoryPattern MEMORY = new MemoryPattern();

    private final Map<ir_opcode, List<Pattern<InstructionMatch>>> map;

    public InstructionMatch match(Node node, NodeRegisters registers) {
        var patterns = map.get(node.getOpCode());
        if (Objects.isNull(patterns)) {
            // todo
            return InstructionMatch.none();
        }
        for (var pattern : patterns) {
            var clone = registers.clone();
            var match = pattern.match(node, clone);
            if (match.matches()) {
                registers.update(clone);
                return match;
            }
        }
        return InstructionMatch.none();
    }

    public Patterns() {
        map = Map.ofEntries(
                Map.entry(iro_Const, Arrays.asList(new LoadImmediate())),
                Map.entry(iro_Add, Arrays.asList(
                        new BinaryInstruction(iro_Add, "add", REGISTER, REGISTER, true, false))),
                Map.entry(iro_Sub, Arrays.asList(
                        new BinaryInstruction(iro_Sub, "sub", REGISTER, REGISTER, true, false))),
                Map.entry(iro_Mul, Arrays.asList(
                        new BinaryInstruction(iro_Mul, "imul", REGISTER, REGISTER, true, false))),

                // todo div, mod
                Map.entry(iro_Minus, Arrays.asList(
                        new UnaryInstruction(iro_Minus, "neg", REGISTER, true, false))),

                Map.entry(iro_Conv, Arrays.asList(new Conversion())),

                Map.entry(iro_Store, Arrays.asList(
                        new BinaryInstruction(iro_Store, "mov", MEMORY, REGISTER, false, true))),
                Map.entry(iro_Load, Arrays.asList(new LoadMemory())),

                Map.entry(iro_Div, Arrays.asList(new Division(Type.DIV, REGISTER, REGISTER))),
                Map.entry(iro_Mod, Arrays.asList(new Division(Type.MOD, REGISTER, REGISTER))),


                Map.entry(iro_Return, Arrays.asList(new ReturnPattern()))

        );
    }
}
