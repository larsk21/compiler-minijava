package edu.kit.compiler;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;

@Slf4j
public class JavaEasyCompiler {

    public static String byteArrayToHex(byte[] a) {
        StringBuilder sb = new StringBuilder(a.length * 2);
        for (byte b : a)
            sb.append(String.format("%02x", b));
        return sb.toString().toUpperCase(Locale.ROOT);
    }

    public static void main(String[] args) throws ParseException {
        //***Definition Stage***
        // create Options object
        Options options = new Options();

        // add option "-a"
        options.addOption("e", "echo", true, "add numbers");

        //***Parsing Stage***
        //Create a parser
        CommandLineParser parser = new DefaultParser();

        //parse the options passed as command line arguments
        CommandLine cmd = parser.parse(options, args);

        //***Interrogation Stage***
        //hasOptions checks if option is present or not
        if (cmd.hasOption("e")) {
            String path = cmd.getOptionValue("e");

            try (FileInputStream fis = new FileInputStream(new File(path))) {
                // Print bytes of this file to stdout
                byte[] bytes = fis.readAllBytes();
                log.info("bytes read " + byteArrayToHex(bytes));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
