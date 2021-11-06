package edu.kit.compiler.cmd;

import edu.kit.compiler.JavaEasyCompiler;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
public class LineEndingTestForEcho {


    private String absolutePath;
    private static final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private static final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    private static final PrintStream originalOut = System.out;
    private static final PrintStream originalErr = System.err;

    private final String expected = "thisisafile\n" +
            "\n" +
            "lkjasdfkjalsdf";

    @BeforeAll
    static void setupPrintStream() {
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
    }

    @AfterAll
    static void restorePrintStream() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    @BeforeEach
    public void setup() {
        String path = "src/test/resources/edu.kit.compiler.cmd/file_with_line_endings.txt";

        File file = new File(path);
        absolutePath = file.getAbsolutePath();

        outContent.reset();
    }

    @Test
    public void testLineEndings() throws IOException {
        JavaEasyCompiler.Result res = JavaEasyCompiler.echo(absolutePath);

        String actual = outContent.toString();
        assertEquals(expected, actual);
        assertEquals(res, JavaEasyCompiler.Result.Ok);
        log.info(actual);
    }

    @Test
    public void testLargeFile() throws IOException {
        String path = "src/test/resources/edu.kit.compiler.cmd/large_file";

        File file = new File(path);
        absolutePath = file.getAbsolutePath();
        JavaEasyCompiler.Result res =  JavaEasyCompiler.echo(absolutePath);

        String repeated = new String(new char[3000]).replace("\0", "A");
        String actual = outContent.toString();
        assertEquals(repeated, actual);
        assertEquals(res, JavaEasyCompiler.Result.Ok);
    }
}
