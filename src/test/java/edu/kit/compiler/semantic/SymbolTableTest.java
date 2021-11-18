package edu.kit.compiler.semantic;

import edu.kit.compiler.data.DataType;
import edu.kit.compiler.data.ast_nodes.StatementNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

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
    public void testLookupInsideScope() {
        Definition definition = new StatementNode.LocalVariableDeclarationStatementNode(0, 0, null, 0, null, false);

        SymbolTable symbolTable = new SymbolTable();
        symbolTable.enterScope();
        symbolTable.insert(definition);

        assertNotNull(symbolTable.lookup(0));
    }

    @Test
    public void testLookupLeaveScope() {
        Definition definition = new StatementNode.LocalVariableDeclarationStatementNode(0, 0, null, 0, null, false);

        SymbolTable symbolTable = new SymbolTable();
        symbolTable.enterScope();
        symbolTable.insert(definition);
        symbolTable.leaveScope();

        assertNull(symbolTable.lookup(0));
    }

    @Test
    public void testLookupLeaveInnerScope() {
        Definition definition = new StatementNode.LocalVariableDeclarationStatementNode(0, 0, null, 0, null, false);

        SymbolTable symbolTable = new SymbolTable();
        symbolTable.enterScope();
        symbolTable.enterScope();
        symbolTable.insert(definition);
        symbolTable.leaveScope();

        assertNull(symbolTable.lookup(0));
    }

    @Test
    public void testLookupLeaveInnerScopeTwoVariables() {
        Definition definition0 = new StatementNode.LocalVariableDeclarationStatementNode(0, 0, null, 0, null, false);
        Definition definition1 = new StatementNode.LocalVariableDeclarationStatementNode(0, 0, null, 1, null, false);

        SymbolTable symbolTable = new SymbolTable();
        symbolTable.enterScope();
        symbolTable.enterScope();
        symbolTable.insert(definition0);
        symbolTable.insert(definition1);
        symbolTable.leaveScope();

        assertNull(symbolTable.lookup(0));
        assertNull(symbolTable.lookup(1));
    }

}
