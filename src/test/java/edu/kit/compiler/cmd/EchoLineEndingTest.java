package edu.kit.compiler.cmd;

import edu.kit.compiler.JavaEasyCompiler;
import edu.kit.compiler.Result;
import edu.kit.compiler.logger.Logger;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EchoLineEndingTest {

    private ClassLoader classLoader = getClass().getClassLoader();

    private static final String FILE = "edu/kit/compiler/cmd/file_with_line_endings.txt";
    private static final String LARGE_FILE = "edu/kit/compiler/cmd/large_file";

    private final String expected = "thisisafile\n" +
            "\n" +
            "lkjasdfkjalsdf";

    @Test
    public void testLineEndings() throws IOException {
        var path = classLoader.getResource(FILE).getPath();
        var os = new ByteArrayOutputStream();
        assertEquals(JavaEasyCompiler.echo(path, os, Logger.nullLogger()), Result.Ok);
        assertEquals(os.toString(), expected);
    }

    @Test
    public void testLargeFile() throws IOException {
        var path = classLoader.getResource(LARGE_FILE).getPath();
        var os = new ByteArrayOutputStream();
        assertEquals(JavaEasyCompiler.echo(path, os, Logger.nullLogger()), Result.Ok);

        String repeated = "A".repeat(3000);
        assertEquals(os.toString(), repeated);
    }
}
