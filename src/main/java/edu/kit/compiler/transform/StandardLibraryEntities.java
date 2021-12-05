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
    private final TypedEntity<MethodType> read;
    @Getter
    private final TypedEntity<MethodType> print;
    @Getter
    private final TypedEntity<MethodType> write;
    @Getter
    private final TypedEntity<MethodType> flush;
    @Getter
    private final TypedEntity<MethodType> calloc;

    /**
     * Return the corresponding entity for the given standard library method. This
     * method is intended to be used in combination with the `method` attribute of
     * `StandardLibraryMethodNode` during code generation.
     * 
     * @param method the standard library method, whose entity is to be returned
     * @return the corresponding entity of method type
     */
    public TypedEntity<MethodType> getEntity(MethodNode.StandardLibraryMethod method) {
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
        var ptrType = new PrimitiveType(Mode.getP());

        var readType = new MethodType(new Type[] {}, new Type[] { intType });
        var printType = new MethodType(new Type[] { intType }, new Type[] {});
        var writeType = new MethodType(new Type[] { intType }, new Type[] {});
        var flushType = new MethodType(new Type[] {}, new Type[] {});
        var callocType = new MethodType(new Type[] { intType, intType }, new Type[] { ptrType });

        this.read = new TypedEntity<>(
            new Entity(globalType, Ident.mangleGlobal("read"), readType), readType
        );
        this.print = new TypedEntity<>(
            new Entity(globalType, Ident.mangleGlobal("print"), printType), printType
        );
        this.write = new TypedEntity<>(
            new Entity(globalType, Ident.mangleGlobal("write"), writeType), writeType
        );
        this.flush = new TypedEntity<>(
            new Entity(globalType, Ident.mangleGlobal("flush"), flushType), flushType
        );
        this.calloc = new TypedEntity<>(
            new Entity(globalType, Ident.mangleGlobal("calloc"), callocType), callocType
        );

        read.getEntity().setVisibility(ir_visibility.ir_visibility_external);
        print.getEntity().setVisibility(ir_visibility.ir_visibility_external);
        write.getEntity().setVisibility(ir_visibility.ir_visibility_external);
        flush.getEntity().setVisibility(ir_visibility.ir_visibility_external);
        calloc.getEntity().setVisibility(ir_visibility.ir_visibility_external);
    }
}
