package edu.kit.compiler.assembly;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Represents a function by its name and the instructions from the function's
 * body.
 */
@AllArgsConstructor
public class FunctionInstructions {

    /**
     * Name of the function.
     */
    @Getter
    private String ldName;

    /**
     * Instruction in the function's body.
     */
    @Getter
    private List<String> instructions;

}
