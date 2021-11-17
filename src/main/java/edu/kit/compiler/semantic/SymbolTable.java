package edu.kit.compiler.semantic;

import edu.kit.compiler.data.CompilerException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;

@Data
public class SymbolTable  {

    private final ArrayDeque<Change> changes = new ArrayDeque<>();
    private final SymbolStringTable symbolStringTable = new SymbolStringTable();
    private Scope currentScope;

    /**
     * Construct a new symbol table that has yet to enter a valid scope.
     */
    public SymbolTable() {

    }

    @RequiredArgsConstructor
    public static class Symbol {
        final int name;
        Scope currentScope;
        Definition currentDefinition;
    }

    @AllArgsConstructor
    protected static class Scope {
        Scope parent;
        int oldSize;
    }

    @AllArgsConstructor
    protected static class Change {
        Symbol sym;
        Definition previousDefinition;
        Scope previousScope;

    }

    /**
     * AstNodes do not hold a String reference, they only hold a reference integer to our global string table.
     * Thus the mapping from String to symbol can be simplified by only providing a mapping from
     * Integer -> Symbol instead of String -> Integer -> Symbol
     */
    static class SymbolStringTable {

        private final LinkedHashMap<Integer, Symbol> table = new LinkedHashMap<>();

        /**
         * Return the symbol for which this name is mapped.
         * @param name The name of the symbol mapped by our global String table
         * @return The symbol for which this name is mapped otherwise null
         */
        public Symbol find(int name) {
            return table.get(name);
        }

        /**
         * Returns a symbol that has yet to be initialized with current scope and current definition
         * @param name The name of the symbol
         * @return If there exists a symbol in the string table return the symbol otherwise create a new symbol
         */
        public Symbol insert(int name) {
            if(table.get(name) == null) {
                Symbol s = new Symbol(name);
                table.put(name, s);
                return s;
            }
            else {
                return table.get(name);
            }
        }

        /**
         * Remove the symbol for which this name is mapped.
         * @param name The name of the symbol mapped by our global String table
         */
        public void remove(int name) {
            table.remove(name);
        }

    }

    /**
     * Enter scope should be called when symbols from outer definitions can coexist with new definitions.
     * <b>Note that enter scope has to be called once before interacting with the symbol table.</b>
     */
    public void enterScope() {
        currentScope = new Scope(currentScope, changes.size());
    }
    public void leaveScope() {
        // revert all previous changes
        while (changes.size() > currentScope.oldSize) {
            Change c = changes.pop();
            // check if we leave our defining scope then remove symbol entirely from table
            if (c.previousScope == null) {
                symbolStringTable.remove(c.sym.name);
                return;
            }
            c.sym.currentDefinition = c.previousDefinition;
            c.sym.currentScope = c.previousScope;
        }
        currentScope = currentScope.parent;
    }

    /**
     * Insert the given Definition in the current scope.
     */
    public Symbol insert(Definition definition) {
        int name = definition.getName();
        Symbol s = symbolStringTable.find(name);
        if(isDefinedInCurrentScope(name)) {
            // this should never happen, make sure to check in upper level to get line number and column
            throw new SemanticException("symbol is already defined in scope!", definition);
        }
        if(s == null) {
            s = symbolStringTable.insert(name);
        }

        s.currentDefinition = null;
        s.currentScope = null;

        // last change resets the symbol
        changes.push(new Change(s, null, null));
        s.currentScope = currentScope;
        s.currentDefinition = definition;

        return s;
    }

    /**
     * Return a definition for this symbol
     * @param symbol The symbol to be checked
     * @return Definition of the symbol
     */
    public Definition lookup(Symbol symbol) {
        return symbol.currentDefinition;
    }

    /**
     * Return a definition for this symbol if there exists one otherwise return null
     * @param symbol The symbol to be checked
     * @return Definition if there exists a symbol otherwise null
     */
    public Definition lookup(int symbol) {
        Symbol s = symbolStringTable.find(symbol);
        if(symbolStringTable.find(symbol) == null) {
            return null;
        }
        else {
            return s.currentDefinition;
        }
    }

    /**
     * Return if the symbol is defined in the current scope.
     * @param symbol The symbol to be checked
     */
    public boolean isDefinedInCurrentScope(int symbol) {
        Symbol s = symbolStringTable.find(symbol);
        if(s == null) {
            return false;
        }
        return s.currentScope == currentScope;
    }

    /**
     * Return if the symbol is defined either in the current scope or in some
     * parent scope of the current scope.
     * @param symbol The symbol to be checked
     */
    public boolean isDefined(int symbol) {
        return symbolStringTable.find(symbol) != null;
    }

}
