package edu.kit.compiler.transform;

import edu.kit.compiler.data.ast_nodes.MethodNode;
import firm.Entity;
import firm.Ident;
import firm.MethodType;
import firm.Mode;
import firm.PrimitiveType;
import firm.Program;
import firm.Type;
import firm.bindings.binding_typerep.ir_visibility;
import lombok.Getter;

/**
 * A Singleton holding reference to the standard library methods.
 * Each method is a Entity in the global type of the program.
 */
public enum StandardLibraryEntities {
    INSTANCE;

    @Getter
    private final Entity read;
    @Getter
    private final Entity print;
    @Getter
    private final Entity write;
    @Getter
    private final Entity flush;

    /**
     * Return the corresponding entity for the given standard library method. This
     * method is intended to be used in combination with the `method` attribute of
     * `StandardLibraryMethodNode` during code generation.
     * 
     * @param method the standard library method, whose entity is to be returned
     * @return the corresponding entity of method type
     */
    public Entity getEntity(MethodNode.StandardLibraryMethod method) {
        return switch (method) {
            case PrintLn -> print;
            case Read -> read;
            case Write -> write;
            case Flush -> flush;
            default -> throw new IllegalStateException(
                "unsupported standard library method"
            );
        };
    }

    private StandardLibraryEntities() {
        JFirmSingleton.initializeFirmLinux();

        var globalType = Program.getGlobalType();
        var intType = new PrimitiveType(Mode.getIs());

        this.read = new Entity(globalType, Ident.mangleGlobal("read"),
            new MethodType(new Type[] {}, new Type[] { intType })
        );
        this.print = new Entity(globalType, Ident.mangleGlobal("print"),
            new MethodType(new Type[] { intType }, new Type[] {})
        );
        this.write = new Entity(globalType, Ident.mangleGlobal("write"),
            new MethodType(new Type[] { intType }, new Type[] {})
        );
        this.flush = new Entity(globalType, Ident.mangleGlobal("flush"),
            new MethodType(new Type[] {}, new Type[] {})
        );

        read.setVisibility(ir_visibility.ir_visibility_external);
        print.setVisibility(ir_visibility.ir_visibility_external);
        write.setVisibility(ir_visibility.ir_visibility_external);
        flush.setVisibility(ir_visibility.ir_visibility_external);
    }
}
