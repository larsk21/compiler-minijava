package edu.kit.compiler.transform;

import firm.Entity;
import firm.Ident;
import firm.MethodType;
import firm.Mode;
import firm.PrimitiveType;
import firm.Program;
import firm.Type;
import firm.bindings.binding_typerep.ir_visibility;
import lombok.Getter;

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

    private StandardLibraryEntities() {
        var globalType = Program.getGlobalType();
        var intType = new PrimitiveType(Mode.getIs());

        this.read = new Entity(globalType, Ident.mangleGlobal("read"),
            new MethodType(new Type[] {}, new Type[] { intType }));
        this.print = new Entity(globalType, Ident.mangleGlobal("print"),
            new MethodType(new Type[] { intType }, new Type[] {}));
        this.write = new Entity(globalType, Ident.mangleGlobal("write"),
            new MethodType(new Type[] { intType }, new Type[] {}));
        this.flush = new Entity(globalType, Ident.mangleGlobal("flush"),
            new MethodType(new Type[] {}, new Type[] {}));

        read.setVisibility(ir_visibility.ir_visibility_external);
        print.setVisibility(ir_visibility.ir_visibility_external);
        write.setVisibility(ir_visibility.ir_visibility_external);
        flush.setVisibility(ir_visibility.ir_visibility_external);
    }
}
