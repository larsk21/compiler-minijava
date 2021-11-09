package edu.kit.compiler.ag;

import edu.kit.compiler.lexer.StringTable;

public class SymbolTable  {

    static class Scope {
        Scope parent;
        int oldSize;
    }
    private final StringTable stringTable;

    public SymbolTable(StringTable stringTable) {
        this.stringTable = stringTable;
    }

    void enterScope() {

    }
    void leaveScope() {

    }
    void insert(String name, Definition declaration) {

    }
    Definition locate(String name) {
        return null;
    }
    boolean isDefinedInCurrentScope(String name) { return false; }

}
