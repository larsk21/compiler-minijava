package edu.kit.compiler.assembly;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import edu.kit.compiler.io.BufferedLookaheadIterator;
import edu.kit.compiler.io.LookaheadIterator;
import lombok.Getter;

public final class JumpInversion implements Iterator<String> {

    private final LookaheadIterator<String> input;

    public JumpInversion(FunctionInstructions function) {
        this.input = new BufferedLookaheadIterator<>(
                function.getInstructions().iterator());
    }

    @Override
    public boolean hasNext() {
        return input.has();
    }

    @Override
    public String next() {
        if (input.has(2)) {
            return parseConditional();
        } else {
            return input.getNext();
        }
    }

    private String parseConditional() {
        assert input.has(0);

        var instruction = input.get(0);
        var space = instruction.indexOf(' ');

        if (space != -1) {
            return Jump.get(instruction.substring(0, space))
                    .map(jump -> {
                        var trueLabel = instruction.substring(space).stripLeading();
                        return parseUnconditional(jump, trueLabel);
                    })
                    .orElseGet(input::getNext);
        } else {
            return input.getNext();
        }
    }

    private String parseUnconditional(Jump trueJump, String trueLabel) {
        assert input.has(1);

        var instruction = input.get(1);

        if (instruction.startsWith("jmp ")) {
            var falseLabel = instruction.substring(3).stripLeading();
            return parseLabel(trueJump, trueLabel, falseLabel);
        } else {
            return input.getNext();
        }
    }

    private String parseLabel(Jump trueJump, String trueLabel, String falseLabel) {
        assert input.has(2);

        var instruction = input.get(2);
        if (instruction.startsWith(trueLabel) && instruction.endsWith(":")) {
            input.next(2);
            return trueJump.getInverse().getInstruction() + " " + falseLabel;
        } else {
            return input.getNext();
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

        public static Optional<Jump> get(String instruction) {
            return Optional.ofNullable(JUMPS.get(instruction));
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
