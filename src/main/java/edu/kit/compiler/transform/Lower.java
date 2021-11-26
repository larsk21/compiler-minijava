package edu.kit.compiler.transform;

import firm.Ident;
import firm.Program;

public class Lower {
    /**
     * Moves all methods in the given `TypeMapper` to the program's global type.
     * Also makes sure their labels are unique and comply with the conventions
     * of the target architecture.
     */
    public static void lowerMethods(TypeMapper typeMapper) {
        var globalType = Program.getGlobalType();
        var uid = 0;

        for (var entry : typeMapper.getClassEntries()) {
            var className = entry.getClassType().getName();
            for (var method : entry.getMethods()) {
                var label = String.format("%s_%s_%s", className, method.getName(), uid++);
                method.setLdIdent(Ident.mangleGlobal(label));
                method.setOwner(globalType);
            }
        }
    }
}
