package edu.kit.compiler.parser;

import edu.kit.compiler.lexer.Lexer;
import edu.kit.compiler.lexer.StringTable;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.Reader;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;


public class ParserTest {

    @BeforeEach
    public void setup() {
        stream = new ByteArrayOutputStream();
        sysout = System.out;
        System.setOut(new PrintStream(stream));
    }

    @AfterEach
    public void tearDown() {
        System.setOut(sysout);
    }

    private PrintStream sysout;
    private ByteArrayOutputStream stream;

    @Test
    public void testEmptyInput() {
        var parser = new Parser(new Lexer(getReader("")));
        parser.parse();
    }

    @Test
    public void testAttributes() {
        var parser = new Parser(new Lexer(getReader(
            "class Test {public int x; public ctype[][] z;}"
        )));
        parser.parse();
    }

    @Test
    public void testMethods() {
        var parser = new Parser(new Lexer(getReader(
            "class Test {"
            + "public void m1() {}"
            + "public void m2(int i, int j) {}"
            + "public static void main(String[] args){} }"
        )));
        parser.parse();
    }

    @Test
    public void testInvalidClass() {
        var parser = new Parser(new Lexer(getReader(
                "public class Test {}"
        )));
        assertThrows(ParseException.class, (parser)::parse, "not part of language spec");
    }

    @Test
    public void testInvalidIdentifier() {
        var parser = new Parser(new Lexer(getReader(
                "class int {}"
        )));
        assertThrows(ParseException.class, (parser)::parse, "not part of language spec");
    }

    @Test
    public void testSimpleAst() {
        Lexer lexer = new Lexer(getReader(
            "class Test {"
            + "public int i;"
            + "public static void main(String[] args){"
            + "  int j = i + 1 - j;"
            + "} }"
        ));

        StringTable stringTable = lexer.getStringTable();
        PrettyPrintAstVisitor visitor = new PrettyPrintAstVisitor(stringTable);

        var parser = new Parser(lexer);
        parser.parse().accept(visitor);
        String result = stream.toString();

        assertEquals(
            "class Test {\n"
            + "\tpublic static void main(String[] args) {\n"
            + "\t\tint j = (i + 1) - j;\n"
            + "\t}\n"
            + "\tpublic int i;\n"
            + "}\n",
            result
        );
    }

    @Test
    public void testExampleFromAssignment() {
        Lexer lexer = new Lexer(getReader(
            "class HelloWorld" +
            "{" +
            "public int c;" +
            "public boolean[] array;" +
            "public static /* blabla */ void main(String[] args)" +
            "{ System.out.println( (43110 + 0) );" +
            "boolean b = true && (!false);" +
            "if (23+19 == (42+0)*1)" +
            "b = (0 < 1);" +
            "else if (!array[2+2]) {" +
            "int x = 0;;" +
            "x = x+1;" +
            "} else {" +
            "new HelloWorld().bar(42+0*1, -1);" +
            "}" +
            "}" +
            "public int bar(int a, int b) { return c = (a+b); }" +
            "}"
        ));

        StringTable stringTable = lexer.getStringTable();
        PrettyPrintAstVisitor visitor = new PrettyPrintAstVisitor(stringTable);

        var parser = new Parser(lexer);
        parser.parse().accept(visitor);
        String result = stream.toString();
        String expected = "class HelloWorld {\n" +
        "\tpublic int bar(int a, int b) {\n" +
        "\t\treturn c = (a + b);\n" +
        "\t}\n" +
        "\tpublic static void main(String[] args) {\n" +
        "\t\t(System.out).println(43110 + 0);\n" +
        "\t\tboolean b = true && (!false);\n" +
        "\t\tif ((23 + 19) == ((42 + 0) * 1))\n" +
        "\t\t\tb = (0 < 1);\n" +
        "\t\telse if (!(array[2 + 2])) {\n" +
        "\t\t\tint x = 0;\n" +
        "\t\t\tx = (x + 1);\n" +
        "\t\t} else {\n" +
        "\t\t\t(new HelloWorld()).bar(42 + (0 * 1), -1);\n" +
        "\t\t}\n" +
        "\t}\n" +
        "\tpublic boolean[] array;\n" +
        "\tpublic int c;\n" +
        "}\n";

        assertEquals(expected, result);
    }

    @Test
    public void testPrecedenceAst() {
        Lexer lexer = new Lexer(getReader(
            "class Test {"
            + "public void m(){"
            + "  int j = (i + 1 - obj.m(-z[0]) < 0 < z) * j;"
            + "} }"
        ));

        StringTable stringTable = lexer.getStringTable();
        PrettyPrintAstVisitor visitor = new PrettyPrintAstVisitor(stringTable);

        var parser = new Parser(lexer);
        parser.parse().accept(visitor);
        String result = stream.toString();

        assertEquals(
            "class Test {\n"
            + "\tpublic void m() {\n"
            + "\t\tint j = ((((i + 1) - (obj.m(-(z[0])))) < 0) < z) * j;\n"
            + "\t}\n"
            + "}\n",
            result
        );
    }

    private static Reader getReader(String input) {
        return new StringReader(input);
    }
}
