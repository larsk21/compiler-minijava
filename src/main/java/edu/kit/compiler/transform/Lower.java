package edu.kit.compiler.transform;

import com.sun.jna.Platform;
import firm.*;
import firm.nodes.Call;
import firm.nodes.Node;
import firm.bindings.binding_typerep.ir_visibility;

public class Lower {
    public static String makeLdIdent(String str) {
        if (Platform.isMac() || Platform.isWindows()) {
            str = "_" + str;
        }
        return str;
    }

    public static void lower(TypeMapper typeMapper) {
        lowerMethods(typeMapper);
        setMainMethod(typeMapper);
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
                var entity = method.getEntity();
                var label = String.format("%s_%s_%s", className, entity.getName(), uid++);
                entity.setLdIdent(Ident.mangleGlobal(label));
                entity.setOwner(globalType);
            }
        }
    }

    /**
     * Generates a virtual main method that wraps the user defined main method.
     * The generated method always returns zero to ensure that compiled programs
     * exit with status 0.
     */
    private static void setMainMethod(TypeMapper typeMapper) {
        var global = Program.getGlobalType();
        var mainType = new MethodType(new Type[] {}, new Type[] { new PrimitiveType(Mode.getIs())} );
        var mainEntity = new Entity(global, Ident.mangleGlobal("main"), mainType);
        mainEntity.setVisibility(ir_visibility.ir_visibility_external);

        var graph = new Graph(mainEntity, 0);
        var cons = new Construction(graph);

        // call user-defined main method
        var call = cons.newCall(cons.getCurrentMem(),
            cons.newAddress(typeMapper.getMainMethod()),
            new Node[] { cons.newConst(0, Mode.getP()) },
            typeMapper.getMainMethod().getType()
        );
        cons.setCurrentMem(cons.newProj(call, Mode.getM(), Call.pnM));
        
        // Set exit status (= return value) to zero
        var ret = cons.newReturn(cons.getCurrentMem(),
            new Node[] { cons.newConst(0, Mode.getIs()) }
        );

        graph.getEndBlock().addPred(ret);
        cons.finish();

        Program.setMainGraph(graph);
    }
}
