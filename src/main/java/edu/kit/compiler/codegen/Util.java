package edu.kit.compiler.codegen;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import edu.kit.compiler.codegen.pattern.Match;
import edu.kit.compiler.intermediate_lang.RegisterSize;
import firm.Mode;
import firm.nodes.Node;

public class Util {

    public static String formatCmd(String cmd, RegisterSize size, Operand op) {
        return String.format("%s%c %s", cmd, size.getSuffix(), op.format());
    }

    public static String formatCmd(String cmd, RegisterSize size, Operand lhs, Operand rhs) {
        return String.format("%s%c %s, %s", cmd, size.getSuffix(), lhs.format(), rhs.format());
    }

    public static String formatJmp(String cmd, int destination) {
        return String.format("%s .L%d", cmd, destination);
    }

    public static RegisterSize getSize(Mode mode) {
        return switch (mode.getSizeBytes()) {
            case 1 -> RegisterSize.BYTE;
            case 2 -> RegisterSize.WORD;
            case 4 -> RegisterSize.DOUBLE;
            case 8 -> RegisterSize.QUAD;
            default -> throw new IllegalStateException("illegal operand size");
        };
    }

    public static List<Node> concat(Match... matches) {
        return Arrays.stream(matches)
            .flatMap(m -> m.getPredecessors())
            .collect(Collectors.toList());
    }
}
