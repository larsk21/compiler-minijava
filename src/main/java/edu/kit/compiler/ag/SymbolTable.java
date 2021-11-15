package edu.kit.compiler.ag;

import edu.kit.compiler.lexer.StringTable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;

@Data
public class SymbolTable  {

    private final StringTable stringTable;
    private final ArrayDeque<Change> changes = new ArrayDeque<>();
    private final SymbolStringTable symbolStringTable = new SymbolStringTable();
    private Scope currentScope;

    @RequiredArgsConstructor
    static class Symbol {
        @NonNull
        String name;
        Scope currentScope;
        Definition currentDefinition;
    }

    @AllArgsConstructor
    static class Scope {
        Scope parent;
        int oldSize;
    }

    @AllArgsConstructor
    static class Change {
        Symbol sym;
        Definition previousDefinition;
        Scope previousScope;

    }

    static class SymbolStringTable {
        private final LinkedHashMap<String, Symbol> table = new LinkedHashMap<>();

        /**
         * Returns a symbol that has yet to be initialized with current scope and current definition
         * @param name The name of the symbol
         * @return If there exists a symbol in the string table return the symbol otherwise create a new symbol
         */
        public Symbol findOrInsert(String name) {
            if(table.get(name) == null) {
                Symbol s = new Symbol(name);
                table.put(name, s);
                return s;
            }
            else {
                return table.get(name);
            }
        }
    }

    public SymbolTable(StringTable stringTable) {
        this.stringTable = stringTable;
    }

    public void enterScope() {
        currentScope = new Scope(currentScope, changes.size());
    }
    public void leaveScope() {
        // revert all previous changes
        while (changes.size() > currentScope.oldSize) {
            Change c = changes.pop();
            c.sym.currentDefinition = c.previousDefinition;
            c.sym.currentScope = c.previousScope;
        }
        currentScope = currentScope.parent;
    }
    public Symbol insert(String name, Definition definition) {
        Symbol s = symbolStringTable.findOrInsert(name);

        if(isDefinedInCurrentScope(s)) {
            // this should never happen, make sure to check in upper level to get line number and column
            throw new SemanticException("symbol is already defined in scope!");
        }

        s.currentDefinition = null;
        s.currentScope = null;

        // last change resets the symbol
        changes.push(new Change(s, null, null));
        s.currentScope = currentScope;
        s.currentDefinition = definition;

        return s;
    }
    public Definition lookup(Symbol symbol) {
        return symbol.currentDefinition;
    }
    public boolean isDefinedInCurrentScope(Symbol symbol) {
        return symbol.currentScope == currentScope;
    }



}
