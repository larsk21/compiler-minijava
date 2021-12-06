package edu.kit.compiler.transform;

import firm.Entity;
import firm.Type;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Helper class for entities with a type known at compile time.
 */
@RequiredArgsConstructor
public final class TypedEntity<T extends Type> {
    @Getter
    private final Entity entity;
    
    @Getter
    private final T type;
}
