package edu.kit.compiler.transform;

import com.sun.jna.Platform;
import firm.*;
import firm.nodes.Node;

public class Lower {

    private static Node calloc;

    public static String makeLdIdent(String str) {
        if (Platform.isMac() || Platform.isWindows()) {
            str = "_" + str;
        }
        return str;
    }

    /**
     * returns the address of calloc in this construction
     * @param construction
     * @return
     */
    public static Node getCallocNode(Construction construction) {
        SegmentType global = Program.getGlobalType();
        if (calloc == null) {
            MethodType callocType = new MethodType(new Type[]{ TypeMapper.getIntegerType(), TypeMapper.getIntegerType()},
                    new Type[]{ TypeMapper.getPointerType()});

            Entity callocEntity = new Entity(global, "calloc", callocType);
            callocEntity.setLdIdent(Lower.makeLdIdent("calloc"));
            calloc = construction.newAddress(callocEntity);
        }
        return calloc;
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
