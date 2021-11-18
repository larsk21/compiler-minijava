package edu.kit.compiler.semantic;

import edu.kit.compiler.lexer.StringTable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class StringTableTest {

    private StringTable table;

    @BeforeEach
    public void setup() {
        this.table = new StringTable();
    }

    @Test
    public void testInsertAndGet() {
        int index = this.table.insert("muhh");
        String s = this.table.retrieve(index);

        Assertions.assertEquals("muhh", s);
    }
}
