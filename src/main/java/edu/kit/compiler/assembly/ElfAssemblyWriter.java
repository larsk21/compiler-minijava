package edu.kit.compiler.assembly;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.stream.Collectors;

import edu.kit.compiler.register_allocation.ApplyAssignment;

/**
 * Writes the assembly file in the ELF format.
 */
public class ElfAssemblyWriter implements AssemblyWriter {

    private static final int DEFAULT_COMMAND_INDENT = 8;
    private static final int DEFAULT_COMMENT_INDENT = 40;
    private static final String COMMENT_START_CHAR = "#";
    private static final String FUNCTION_ALIGNMENT = "4,,15";

    private PrintStream output;

    private String format(String format, Object... args) {
        return String.format(format, args);
    }

    private void printCustom(String command, int commandIndent, String comment, int commentIndent) {
        if (!command.isEmpty()) {
            char[] indentChars = new char[commandIndent];
            Arrays.fill(indentChars, ' ');
            output.print(indentChars);

            output.print(command);
        }

        if (!comment.isEmpty()) {
            if (!command.isEmpty()) {
                char[] indentChars = new char[Math.max(0, commentIndent - command.length() - commandIndent)];
                Arrays.fill(indentChars, ' ');
                output.print(indentChars);
            }

            output.print(comment);
        }

        output.println();
    }

    private void print(String command) {
        printCustom(command, DEFAULT_COMMAND_INDENT, "", DEFAULT_COMMENT_INDENT);
    }

    private void print(String command, String comment) {
        printCustom(command, DEFAULT_COMMAND_INDENT, comment, DEFAULT_COMMENT_INDENT);
    }

    private void printLabel(String label) {
        printCustom(label, 0, "", 0);
    }

    private String[] split(String line) {
        String[] split = line.split(COMMENT_START_CHAR);
        if (split.length == 1) {
            return new String[] {
                split[0],
                ""
            };
        } else {
            String comment = COMMENT_START_CHAR + Arrays.stream(split).skip(1).collect(Collectors.joining(COMMENT_START_CHAR));

            return new String[] {
                split[0].trim(),
                comment.trim()
            };
        }
    }

    @Override
    public void writeAssembly(Iterable<FunctionInstructions> functions, OutputStream writer) {
        output = new PrintStream(writer);

        writeFileProlog();
        for (FunctionInstructions function : functions) {
            writeFunction(function);
        }
        writeFileEpilog();
    }

    private void writeFileProlog() {
        print(".text");
    }

    private void writeFileEpilog() {
        return;
    }

    private void writeFunction(FunctionInstructions function) {
        writeFunctionProlog(function.getLdName());

        for (String line : function.getInstructions()) {
            String[] commandComment = split(line);
            String command = commandComment[0];
            String comment = commandComment[1];

            command = command.replace(ApplyAssignment.FINAL_BLOCK_LABEL, format(".LFE_%s", function.getLdName()));

            if (command.startsWith(".L")) {
                writeBlockLabel(command);
            } else {
                writeCommand(command, comment);
            }
        }

        writeFunctionEpilog(function.getLdName());
    }

    private void writeFunctionProlog(String name) {
        print("", format("# -- Begin  %s", name));
        print(format(".p2align %s", FUNCTION_ALIGNMENT));
        print(format(".globl %s", name));
        print(format(".type %s, @function", name));

        printLabel(format("%s:", name));
    }

    private void writeFunctionEpilog(String name) {
        print(format(".size %s, .-%s", name, name));
        print("", format("# -- End  %s", name));
        print("");
    }

    private void writeBlockLabel(String label) {
        printLabel(format("%s", label));
    }

    private void writeCommand(String command, String comment) {
        print(format("%s", command), comment);
    }

}
