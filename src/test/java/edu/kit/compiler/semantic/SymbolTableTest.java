package edu.kit.compiler.semantic;

import edu.kit.compiler.data.ast_nodes.StatementNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SymbolTableTest {


    private SymbolTable symbolTable;

    @BeforeEach
    public void setup() {
        symbolTable = new SymbolTable();
    }

    /**
     * Test for entering and leaving a scope, should return the same scope
     */
    @Test
    public void testBasicFunctionality() {
        SymbolTable.Scope expected = symbolTable.getCurrentScope();
        symbolTable.enterScope();
        symbolTable.leaveScope();
        SymbolTable.Scope actual = symbolTable.getCurrentScope();
        assertEquals(actual, expected);
    }

    @Test
    public void testLookup() {
        Definition expected = new StatementNode.LocalVariableDeclarationStatementNode(0, 0, null, 0, null, false);
        SymbolTable.Symbol s = symbolTable.insert(expected);
        // just test it without entering or leaving anything
        Definition actual = symbolTable.lookup(s);

        assertEquals(actual, expected);
    }

    @Test
    public void testLookupComplicated() {

    }
}
