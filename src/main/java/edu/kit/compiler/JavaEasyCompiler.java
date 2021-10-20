package edu.kit.compiler;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.*;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

@Slf4j
public class JavaEasyCompiler {

    public static Result main(CommandLine cmd, Options options) {
        if (cmd.hasOption("h")) {
            HelpFormatter help = new HelpFormatter();
            help.printHelp("Java Easy Compiler", options);

            return Result.Ok;
        }
        else if (cmd.hasOption("e")) {
            String filePath = cmd.getOptionValue("e");

            return echo(filePath);
        } else {
            System.err.println("Wrong command line arguments, see --help for supported commands.");

            return Result.CliInputError;
        }
    }

    private static Result echo(String filePath) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(filePath)))) {
            reader.lines().forEachOrdered(line -> {
                System.out.println(line);
            });

            return Result.Ok;
        } catch (IOException e) {
            System.err.println("Error during file io: " + e.getMessage());

            return Result.FileInputError;
        }
    }

    public static void main(String[] args) {
        Options options = new Options();
        options.addOption("e", "echo", true, "output file contents");
        options.addOption("h", "help", false, "print command line syntax help");

        CommandLineParser parser = new DefaultParser();
        Result result;
        try {
            CommandLine cmd = parser.parse(options, args);

            result = main(cmd, options);
        } catch (ParseException e) {
            System.err.println("Wrong command line arguments, see --help for supported commands.");

            result = Result.CliInputError;
        }

        System.exit(result.getCode());
    }

    public enum Result {
        Ok(0),
        CliInputError(1),
        FileInputError(1);

        private Result(int code) {
            this.code = code;
        }

        private int code;

        public int getCode() {
            return code;
        }
    }

}
