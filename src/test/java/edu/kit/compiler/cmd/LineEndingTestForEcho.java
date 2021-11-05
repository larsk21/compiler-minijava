package edu.kit.compiler.cmd;

import edu.kit.compiler.JavaEasyCompiler;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
public class LineEndingTestForEcho {


    private String absolutePath;

    String expected = "thisisafile\n" +
            "\n" +
            "lkjasdfkjalsdf";

    @BeforeEach
    public void setup() {
        String path = "src/test/resources/edu.kit.compiler.cmd/file_with_line_endings.txt";

        File file = new File(path);
        absolutePath = file.getAbsolutePath();
    }

    @Test
    public void testLineEndings() throws IOException {
        String content = JavaEasyCompiler.echo(absolutePath);
        assertEquals(content, expected);

        log.info(content);
    }

    @Test
    public void testLargeFile() throws IOException {
        String path = "src/test/resources/edu.kit.compiler.cmd/large_file";

        File file = new File(path);
        absolutePath = file.getAbsolutePath();
        String content = JavaEasyCompiler.echo(absolutePath);

        String repeated = new String(new char[3000]).replace("\0", "A");
        assertEquals(content, repeated);
    }
}
