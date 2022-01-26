package edu.kit.compiler.assembly;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import edu.kit.compiler.assembly.AssemblyOptimizer.AssemblyOptimization;
import lombok.Getter;

/**
 * An optimization to improve control flow in assembly. A conditional jump
 * to Label A, followed by an unconditional jump to label B is replaced with
 * a single conditional jump to label B if fallthrough is possible.
 */
public final class JumpInversion extends AssemblyOptimization {

    public JumpInversion() {
        super(3);
    }

    @Override
    public Optional<String[]> optimize(String[] instructions) {
        var trueJump = Jump.parse(instructions[0]);
        if (trueJump.isEmpty()) {
            return Optional.empty();
        }

        if (!instructions[1].startsWith("jmp ")) {
            return Optional.empty();
        }

        var trueLabel = parseLabel(instructions[0]);
        var falseLabel = parseLabel(instructions[1]);
        if (trueLabel == null || falseLabel == null) {
            return Optional.empty();
        }

        if (!instructions[2].startsWith(".L")) {
            return Optional.empty();
        }

        if (instructions[2].equals(trueLabel + ":")) {
            var invertedJump = trueJump.get().getInverse().getInstruction() + " " + falseLabel;
            return Optional.of(new String[] { invertedJump, instructions[2] });
        } else {
            return Optional.empty();
        }
    }

    private static String parseLabel(String instruction) {
        var space = instruction.indexOf(' ');

        if (space != -1) {
            var label = instruction.substring(space).stripLeading();
            if (label.startsWith(".L")) {
                return label;
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    private enum Jump {
        JO,
        JNO,
        JS,
        JNS,
        JE,
        JZ,
        JNE,
        JNZ,
        JB,
        JNAE,
        JC,
        JNB,
        JAE,
        JNC,
        JBE,
        JNA,
        JA,
        JNBE,
        JL,
        JNGE,
        JGE,
        JNL,
        JLE,
        JNG,
        JG,
        JNLE,
        JP,
        JPE,
        JNP,
        JPO;

        @Getter
        private final String instruction;

        private Jump() {
            this.instruction = this.name().toLowerCase();
        }

        public static Optional<Jump> parse(String instruction) {
            var space = instruction.indexOf(' ');

            if (space != -1) {
                return Optional.ofNullable(JUMPS.get(instruction.substring(0, space)));
            } else {
                return Optional.empty();
            }
        }

        private static final Map<String, Jump> JUMPS = Arrays.stream(Jump.values())
                .collect(Collectors.toMap(Jump::getInstruction, x -> x));

        public Jump getInverse() {
            return switch (this) {
                case JO -> JNO;
                case JNO -> JO;
                case JS -> JNS;
                case JNS -> JS;
                case JE -> JNE;
                case JNE -> JE;
                case JZ -> JNZ;
                case JNZ -> JZ;
                case JB -> JNB;
                case JNB -> JB;
                case JNAE -> JAE;
                case JAE -> JNAE;
                case JC -> JNC;
                case JNC -> JC;
                case JBE -> JNBE;
                case JNBE -> JBE;
                case JNA -> JA;
                case JA -> JNA;
                case JL -> JNL;
                case JNL -> JL;
                case JNGE -> JGE;
                case JGE -> JNGE;
                case JLE -> JNLE;
                case JNLE -> JLE;
                case JNG -> JG;
                case JG -> JNG;
                case JP -> JNP;
                case JNP -> JP;
                case JPE -> JPO;
                case JPO -> JPE;
            };
        }
    }
}
