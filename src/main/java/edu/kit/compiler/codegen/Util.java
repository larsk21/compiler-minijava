package edu.kit.compiler.codegen;

import java.util.stream.Stream;

import edu.kit.compiler.codegen.Operand.Immediate;
import edu.kit.compiler.codegen.Operand.Register;
import edu.kit.compiler.intermediate_lang.RegisterSize;
import edu.kit.compiler.io.CommonUtil;

import firm.Mode;
import firm.TargetValue;
import firm.nodes.Node;

public class Util {

    public static String formatCmd(String cmd, RegisterSize size, Operand op) {
        return String.format("%s%c %s", cmd, size.getSuffix(), op.format());
    }

    public static String formatCmd(String cmd, RegisterSize size, int targetRegister) {
        return String.format("%s%c @%d", cmd, size.getSuffix(), targetRegister);
    }

    public static String formatCmd(String cmd, RegisterSize size, Operand lhs, Operand rhs) {
        return String.format("%s%c %s, %s", cmd, size.getSuffix(), lhs.format(), rhs.format());
    }

    public static String formatCmd(String cmd, RegisterSize size, Operand lhs, int targetRegister) {
        return String.format("%s%c %s, @%d", cmd, size.getSuffix(), lhs.format(), targetRegister);
    }

    public static String formatJmp(String cmd, int destination) {
        return String.format("%s .L%d", cmd, destination);
    }

    public static String formatLoad(RegisterSize size, Immediate value, Register target) {
        if (value.get().isNull()) {
            return Util.formatCmd("xor", size, target, target);
        } else {
            return Util.formatCmd("mov", size, value, target);
        }
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

    public static Stream<Node> streamPreds(Node node) {
        return CommonUtil.stream(node.getPreds());
    }

    public static boolean isOverflow(TargetValue value, RegisterSize maxSize) {
        // based on is_overflow(..) in libfirm/ir/tv/tv.c
        if (value.getMode().isSigned()) {
            // if sign bit is set, all upper bits must be zro
            return value.highest_bit() >= maxSize.getBits() - 1
                    && value.not().highest_bit() >= maxSize.getBits() - 1;
        } else {
            // overflow if any of the upper bits is zero
            return value.highest_bit() >= maxSize.getBits();
        }
    }
}
