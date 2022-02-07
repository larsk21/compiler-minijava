package edu.kit.compiler.cli;

import java.io.PrintStream;
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
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

/**
 * Definition of the command line interface.
 */
@RequiredArgsConstructor
public class Cli {

    /**
     * Groups with the options supported by the command line interface.
     */
    @Getter
    @NonNull
    private List<CliOptionGroup> optionGroups;

    @Getter
    @Setter
    private int helpDescriptionPadding = 30;

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
        printHelp(System.out, Optional.empty(), Optional.empty());
    }

    /**
     * Print a help text for this command line interface with the given header
     * and footer.
     */
    public void printHelp(String header, String footer) {
        printHelp(System.out, Optional.of(header), Optional.of(footer));
    }

    private void printHelp(PrintStream stream, Optional<String> header, Optional<String> footer) {
        if (header.isPresent()) {
            stream.println(header.get());
            stream.println();
        }

        for (CliOptionGroup optionGroup : optionGroups) {
            stream.printf("%s%n", optionGroup.getName());

            for (CliOption option : optionGroup.getOptions()) {
                String usage = String.format(" -%s --%s%s",
                    option.getShortName(),
                    option.getLongName(),
                    option.getArgName().map(arg -> String.format(" <%s>", arg)).orElse("")
                );
                stream.printf("%-" + helpDescriptionPadding + "s%s%n", usage, option.getDescription());
            }

            stream.println();
        }

        if (footer.isPresent()) {
            stream.println(footer.get());
        }
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
