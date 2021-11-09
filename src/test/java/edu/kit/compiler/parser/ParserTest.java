package edu.kit.compiler.parser;

import edu.kit.compiler.data.ast_nodes.ClassNode;
import edu.kit.compiler.io.ReaderCharIterator;
import edu.kit.compiler.lexer.Lexer;
import edu.kit.compiler.lexer.StringTable;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.Reader;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;


public class ParserTest {
    @Test
    public void testEmptyInput() {
        var parser = new Parser(new Lexer(getIterator("")));
        parser.parse();
    }

    @Test
    public void testAttributes() {
        var parser = new Parser(new Lexer(getIterator(
            "class Test {public int x; public ctype[][] z;}"
        )));
        parser.parse();
    }

    @Test
    public void testMethods() {
        var parser = new Parser(new Lexer(getIterator(
            "class Test {"
            + "public void m1() {}"
            + "public void m2(int i, int j) {}"
            + "public static void main(String[] args){} }"
        )));
        parser.parse();
    }

    @Test
    public void testInvalidClass() {
        var parser = new Parser(new Lexer(getIterator(
                "public class Test {}"
        )));
        assertThrows(ParseException.class, (parser)::parse, "not part of language spec");
    }

    @Test
    public void testInvalidIdentifier() {
        var parser = new Parser(new Lexer(getIterator(
                "class int {}"
        )));
        assertThrows(ParseException.class, (parser)::parse, "not part of language spec");
    }

    @Test
    public void testSimpleAst() {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        PrintStream sysout = System.out;
        System.setOut(new PrintStream(stream));

        Lexer lexer = new Lexer(getIterator(
            "class Test {"
            + "public int i;"
            + "public static void main(String[] args){"
            + "  int j = i + 1 - j;"
            + "} }"
        ));

        StringTable stringTable = lexer.getStringTable();
        PrettyPrintAstVisitor visitor = new PrettyPrintAstVisitor(stringTable);

        var parser = new Parser(lexer);
        var classes = parser.parse();

        for (ClassNode node: classes) {
            node.accept(visitor);
        }
        String result = stream.toString();

        assertEquals(
            "class Test {\n"
            + "\tpublic static void main(String[] args) {\n"
            + "\t\tint j = (i + 1) - j;\n"
            + "\t}\n"
            + "\tpublic int i;\n"
            + "}",
            result
        );
        System.setOut(sysout);
    }

    private static ReaderCharIterator getIterator(String input) {
        return getIterator(new StringReader(input));
    }

    private static ReaderCharIterator getIterator(Reader reader) {
        return new ReaderCharIterator(reader);
    }
}
