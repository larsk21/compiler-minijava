package edu.kit.compiler.semantic;

/**
 * At which kind of location a variable is defined.
 */
public enum DefinitionKind {
    GlobalVariable,
    Field,
    Parameter,
    LocalVariable
}
