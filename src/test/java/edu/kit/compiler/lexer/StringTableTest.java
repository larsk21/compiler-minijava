package edu.kit.compiler.lexer;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class StringTableTest {

    private StringTable table;

    @BeforeEach
    public void setup() {
        table = new StringTable();
    }

    @Test
    public void testInsertAndGet() {
        int index = table.insert("muhh");
        String s = table.retrieve(index);

        Assertions.assertEquals("muhh", s);
    }
}
