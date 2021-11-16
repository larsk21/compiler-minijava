package edu.kit.compiler.semantic;

import edu.kit.compiler.data.DataType;
import edu.kit.compiler.data.Positionable;

/**
 * Definition of a variable.
 */
public interface Definition extends Positionable {
    int getName();
    DataType getType();
    DefinitionKind getKind();
}
