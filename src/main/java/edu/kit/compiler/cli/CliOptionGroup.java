package edu.kit.compiler.cli;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Represents a group of options in a command line interface.
 */
@RequiredArgsConstructor
@AllArgsConstructor
public class CliOptionGroup {

    /**
     * Name of the group.
     */
    @Getter
    private String name;
    /**
     * If the options in the group are exclusive, i.e. two or more options from
     * the group may not be present in the same call to the command line
     * interface.
     */
    @Getter
    private boolean exclusive;
    /**
     * Options contained in this group.
     */
    @Getter
    private List<CliOption> options = new ArrayList<>();

}
