package edu.kit.compiler.cli;

import java.util.Optional;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Represents an option of a command line interface.
 */
@RequiredArgsConstructor
@AllArgsConstructor
public class CliOption {

    /**
     * Short name.
     */
    @Getter
    private String shortName;
    /**
     * Long name.
     */
    @Getter
    private String longName;
    /**
     * Name of the argument if the option accepts an argument.
     */
    @Getter
    private Optional<String> argName = Optional.empty();
    /**
     * Description.
     */
    @Getter
    private String description;

}
