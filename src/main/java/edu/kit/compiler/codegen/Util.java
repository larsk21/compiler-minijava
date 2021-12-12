package edu.kit.compiler.codegen;

import edu.kit.compiler.intermediate_lang.RegisterSize;
import firm.Mode;

public class Util {

    public static String formatCmd(String cmd, RegisterSize size, Operand op) {
        return String.format("%s%c %s", cmd, getSizeChar(size), op.format());
    }

    public static String formatCmd(String cmd, RegisterSize size, Operand lhs, Operand rhs) {
        return String.format("%s%c %s, %s", cmd, getSizeChar(size), lhs.format(), rhs.format());
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

    public static char getSizeChar(RegisterSize size) {
        return switch (size) {
            case BYTE -> 'b';
            case WORD -> 'w';
            case DOUBLE -> 'l';
            case QUAD -> 'q';
            default -> throw new IllegalStateException();
        };
    }
}
