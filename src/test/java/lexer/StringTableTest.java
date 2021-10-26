package lexer;

import edu.kit.compiler.lexer.StringTable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class StringTableTest {

    StringTable table;

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
