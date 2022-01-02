package edu.kit.compiler.assembly;

import java.io.OutputStream;

/**
 * Assembly file writer, taking care of file format specifications and
 * formatting.
 */
public interface AssemblyWriter {

    /**
     * Write the assembly file containing the given functions to the given
     * OutputStream.
     */
    public void writeAssembly(Iterable<FunctionInstructions> functions, OutputStream writer);

}
