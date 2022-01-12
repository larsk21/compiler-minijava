package edu.kit.compiler.cli;

import java.util.List;
import java.util.Optional;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Definition of the command line interface.
 */
@AllArgsConstructor
public class Cli {

    /**
     * Groups with the options supported by the command line interface.
     */
    @Getter
    private List<CliOptionGroup> optionGroups;

    /**
     * Parse the command line arguments with this definition of a command line
     * interface.
     */
    public Optional<CliCall> parse(String[] args) {
        Options options = transformOptions();

        try {
            CommandLineParser parser = new DefaultParser(false);
            CommandLine cmd = parser.parse(options, args);

            return Optional.of(new CliCall(cmd));
        } catch (org.apache.commons.cli.ParseException e) {
            return Optional.empty();
        }
    }

    /**
     * Print a help text for this command line interface.
     */
    public void printHelp() {
        throw new UnsupportedOperationException();
    }

    /**
     * Represents a call to the command line interface with specific arguments.
     */
    @AllArgsConstructor
    public class CliCall {

        private CommandLine cmd;

        /**
         * Return true if the given option was specified in this call.
         */
        public boolean hasOption(CliOption option) {
            return cmd.hasOption(option.getShortName());
        }

        /**
         * Return the argument of the given option in this call.
         * 
         * @throws IllegalArgumentException If the given option does not accept
         * an argument.
         */
        public String getOptionArg(CliOption option) {
            if (option.getArgName().isEmpty()) {
                throw new IllegalArgumentException("passed option does not accept an argument");
            }

            return cmd.getOptionValue(option.getShortName());
        }

        /**
         * Return the arguments in this call which do not belong to any option.
         */
        public String[] getFreeArgs() {
            return cmd.getArgs();
        }

    }

    private Options transformOptions() {
        Options options = new Options();

        for (CliOptionGroup optionGroup : optionGroups) {
            if (optionGroup.isExclusive()) {
                OptionGroup group = new OptionGroup();

                for (CliOption option : optionGroup.getOptions()) {
                    group.addOption(new Option(
                        option.getShortName(),
                        option.getLongName(),
                        option.getArgName().isPresent(),
                        option.getDescription()
                    ));
                }

                options.addOptionGroup(group);
            } else {
                for (CliOption option : optionGroup.getOptions()) {
                    options.addOption(new Option(
                        option.getShortName(),
                        option.getLongName(),
                        option.getArgName().isPresent(),
                        option.getDescription()
                    ));
                }
            }
        }

        return options;
    }

}
