package edu.kit.compiler;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.*;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

@Slf4j
public class JavaEasyCompiler {

    public static void main(CommandLine cmd) {
        if (cmd.hasOption("e")) {
            String filePath = cmd.getOptionValue("e");

            echo(filePath);
        } else {
            // error
        }
    }

    private static void echo(String filePath) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(filePath)))) {
            reader.lines().forEachOrdered(line -> {
                System.out.println(line);
            });
        } catch (IOException e) {
            // error
        }
    }

    public static void main(String[] args) throws ParseException {
        Options options = new Options();
        options.addOption("e", "echo", true, "output file contents");

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, args);

        main(cmd);
    }

}
