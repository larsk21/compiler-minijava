package edu.kit.compiler.transform;

import com.sun.jna.Platform;
import firm.*;
import firm.nodes.Node;

public class Lower {
    public static String makeLdIdent(String str) {
        if (Platform.isMac() || Platform.isWindows()) {
            str = "_" + str;
        }
        return str;
    }

    public static void lower(TypeMapper typeMapper) {
        lowerMethods(typeMapper);
        Backend.lowerForTarget();
        Util.lowerSels();
    }

    /**
     * Moves all methods in the given `TypeMapper` to the program's global type.
     * Also makes sure their labels are unique and comply with the conventions
     * of the target architecture.
     */
    private static void lowerMethods(TypeMapper typeMapper) {
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

        // Rename main method, so gcc recognizes it
        typeMapper.getMainMethod().setLdIdent(Ident.mangleGlobal("main"));
    }
}
