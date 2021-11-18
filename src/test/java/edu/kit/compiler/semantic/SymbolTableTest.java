package edu.kit.compiler.semantic;

import edu.kit.compiler.data.ast_nodes.StatementNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

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
        SymbolTable.Symbol s = this.symbolTable.insert(expected);
        // just test it without entering or leaving anything
        Definition actual = this.symbolTable.lookup(s);

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

    @Test
    public void testLookupDefinitionInLowerScope() {
        Definition definition0 = new StatementNode.LocalVariableDeclarationStatementNode(0, 0, null, 0, null, false);
        SymbolTable table = new SymbolTable();
        this.symbolTable.enterScope();
        this.symbolTable.insert(definition0);
        for (int i = 0; i < 10; i++) {
            this.symbolTable.enterScope();
        }
        Definition actual = this.symbolTable.lookup(0);
        assertNotNull(actual);
        assertEquals(actual, definition0);
    }

    @Test
    public void testInsertMultipleDefinitionsForSameName() {
        Definition definition0 = new StatementNode.LocalVariableDeclarationStatementNode(0, 0, null, 1, null, false);
        Definition definition1 = new StatementNode.LocalVariableDeclarationStatementNode(0, 0, null, 1, null, false);

        SymbolTable table = new SymbolTable();
        this.symbolTable.enterScope();
        this.symbolTable.insert(definition0);
        for (int i = 0; i < 10; i++) {
            this.symbolTable.enterScope();
        }
        // this should be okay since we are in a new scope here
        this.symbolTable.insert(definition1);
        this.symbolTable.enterScope();
        Definition actual = this.symbolTable.lookup(1);
        assertEquals(actual, definition1);

        this.symbolTable.leaveScope();
        this.symbolTable.leaveScope();

        actual = this.symbolTable.lookup(1);
        assertEquals(actual, definition0);
    }


    @Test
    public void isDefinedTest() {
        Definition definition0 = new StatementNode.LocalVariableDeclarationStatementNode(0, 0, null, 1, null, false);
        Definition definition1 = new StatementNode.LocalVariableDeclarationStatementNode(0, 0, null, 1, null, false);

        SymbolTable table = new SymbolTable();
        this.symbolTable.enterScope();
        this.symbolTable.insert(definition0);
        for (int i = 0; i < 10; i++) {
            this.symbolTable.enterScope();
        }
        // this should be okay since we are in a new scope here
        this.symbolTable.insert(definition1);
        this.symbolTable.enterScope();
        boolean actual = this.symbolTable.isDefined(1);
        assertTrue(actual);

        this.symbolTable.leaveScope();
        this.symbolTable.leaveScope();

        actual = this.symbolTable.isDefined(1);
        assertTrue(actual);

        for (int i = 0; i < 10; i++) {
            this.symbolTable.leaveScope();
        }
        assertFalse(this.symbolTable.isDefined(1));
    }

    @Test
    public void testDoubleInsert() {
        Definition definition0 = new StatementNode.LocalVariableDeclarationStatementNode(0, 0, null, 1, null, false);
        this.symbolTable.insert(definition0);

        assertThrows(SemanticException.class, () -> this.symbolTable.insert(definition0));
    }
}
