package edu.kit.compiler.transform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.StringReader;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.kit.compiler.data.DataType;
import edu.kit.compiler.data.DataType.DataTypeClass;
import edu.kit.compiler.data.ast_nodes.ProgramNode;
import edu.kit.compiler.lexer.Lexer;
import edu.kit.compiler.lexer.StringTable;
import edu.kit.compiler.logger.Logger;
import edu.kit.compiler.parser.Parser;
import edu.kit.compiler.semantic.DetailedNameTypeAstVisitor;
import edu.kit.compiler.semantic.ErrorHandler;
import edu.kit.compiler.semantic.NamespaceGatheringVisitor;
import edu.kit.compiler.semantic.NamespaceMapper;

import firm.Mode;

public class TypeMapperTest {
    private TypeMapper typeMapper;
    private StringTable stringTable;

    private static final String INPUT = "class Main { public static void main(String[] args) {} }";
    
    @BeforeEach
    public void setup() throws IOException {
        JFirmSingleton.initializeFirmLinux();

        ErrorHandler errorHandler = new ErrorHandler(Logger.nullLogger());
        Lexer lexer = new Lexer(new StringReader(INPUT));
        stringTable = lexer.getStringTable();
        ProgramNode node =  new Parser(lexer).parse();

        NamespaceMapper namespaceMapper = new NamespaceMapper();
        NamespaceGatheringVisitor visitor = new NamespaceGatheringVisitor(
            namespaceMapper, lexer.getStringTable(), errorHandler
        );
        node.accept(visitor);
        node.accept(new DetailedNameTypeAstVisitor(namespaceMapper, stringTable, errorHandler));
        typeMapper = new TypeMapper(namespaceMapper, lexer.getStringTable());
    }

    @Test
    public void testBooleanMode() {
        var type = new DataType(DataTypeClass.Boolean);
        assertEquals(Mode.getBu(), typeMapper.getDataType(type).getMode());
        assertEquals(Mode.getBu(), typeMapper.getMode(type));
    }

    @Test
    public void testIntegerMode() {
        var type = new DataType(DataTypeClass.Int);
        assertEquals(Mode.getIs(), typeMapper.getDataType(type).getMode());
        assertEquals(Mode.getIs(), typeMapper.getMode(type));
    }

    @Test
    public void testArrayMode() {
        var type = new DataType(new DataType(DataTypeClass.Int));
        assertEquals(Mode.getP(), typeMapper.getDataType(type).getMode());
        assertEquals(Mode.getP(), typeMapper.getMode(type));
    }

    @Test
    public void testUserMode() {
        var type = new DataType(new DataType(stringTable.insert("Main")));
        assertEquals(Mode.getP(), typeMapper.getDataType(type).getMode());
        assertEquals(Mode.getP(), typeMapper.getMode(type));
    }

    @Test
    public void testVoidMode() {
        var type = new DataType(DataTypeClass.Void);
        assertThrows(RuntimeException.class, () -> typeMapper.getDataType(type).getMode());
        assertThrows(RuntimeException.class, () -> typeMapper.getMode(type));
    }

    @Test
    public void testAnyMode() {
        var type = new DataType(DataTypeClass.Any);
        assertThrows(RuntimeException.class, () -> typeMapper.getDataType(type).getMode());
        assertThrows(RuntimeException.class, () -> typeMapper.getMode(type));
    }
}
