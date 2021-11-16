package edu.kit.compiler.semantic;

import edu.kit.compiler.data.DataType;

public interface Definition {
    int getLine();
    int getColumn();
    int getName();
    DataType getType();
    DefinitionKind getKind();
}
