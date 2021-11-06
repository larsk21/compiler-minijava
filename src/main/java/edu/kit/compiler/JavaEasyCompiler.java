package edu.kit.compiler;

import edu.kit.compiler.data.Token;
import edu.kit.compiler.data.TokenType;
import edu.kit.compiler.io.ReaderCharIterator;
import edu.kit.compiler.lexer.LexException;
import edu.kit.compiler.lexer.Lexer;
import edu.kit.compiler.lexer.StringTable;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.*;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

@Slf4j
public class JavaEasyCompiler {

    /**
     * Output the file contents to stdout.
     * 
     * @param filePath Path of the file (absolute or relative)
     * @return Ok or FileInputError (in case of an IOException)
     */
    public static Result echo(String filePath, OutputStream oStream) {
        try (
            InputStream iStream = new FileInputStream(filePath);
        ) {
            iStream.transferTo(oStream);
            return Result.Ok;
        } catch (IOException e) {
            System.err.println("Error during file io: " + e.getMessage());
            return Result.FileInputError;
        }
    }

    /**
     * Split the file contents in Lexer Tokens and output the representations one Token per line.
     * 
     * @param filePath Path of the file (absolute or relative)
     * @return Ok or FileInputError (in case of an IOException)
     */
    private static Result lextest(String filePath) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(filePath)))) {
            Lexer lexer = new Lexer(new ReaderCharIterator(reader));
            StringTable stringTable = lexer.getStringTable();

            Token token;
            while ((token = lexer.getNextToken()).getType() != TokenType.EndOfStream) {
                System.out.println(token.getStringRepresentation(stringTable));
            }
            System.out.println(token.getStringRepresentation(stringTable));

            return Result.Ok;
        } catch (LexException e) {
            System.err.printf("error: lexer: %d,%d: %s%n", e.getLine(), e.getColumn(), e.getMessage());

            return Result.LexError;
        } catch (IOException e) {
            System.err.println("Error during file io: " + e.getMessage());

            return Result.FileInputError;
        }
    }

    public static void main(String[] args) {
        // specify supported command line options
        Options options = new Options();
        options.addOption("h", "help", false, "print command line syntax help");
        options.addOption("e", "echo", true, "output file contents");
        options.addOption("l", "lextest", true, "output the tokens from the lexer");

        // parse command line arguments
        CommandLine cmd;
        try {
            CommandLineParser parser = new DefaultParser(false);

            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            System.err.println("Wrong command line arguments, see --help for supported commands.");

            System.exit(Result.CliInputError.getCode());
            return;
        }

        // execute requested function
        Result result;
        if (cmd.hasOption("h")) {
            HelpFormatter help = new HelpFormatter();

            String usage = "compiler [-h] [-e <file>] [-l <file>]";
            String header = "\n";
            String footer = "\nfor more information check out: https://github.com/larsk21/compiler-minijava";

            help.printHelp(usage, header, options, footer);

            result = Result.Ok;
        } else if (cmd.hasOption("e")) {
            String filePath = cmd.getOptionValue("e");
            result = echo(filePath, System.out);
        } else if (cmd.hasOption("l")) {
            String filePath = cmd.getOptionValue("l");

            result = lextest(filePath);
        } else {
            System.err.println("Wrong command line arguments, see --help for supported commands.");

            result = Result.CliInputError;
        }

        // return exit code from executed function
        System.exit(result.getCode());
    }

    /**
     * Represents the result of a command execution.
     */
    public enum Result {
        Ok(0),
        CliInputError(1),
        FileInputError(1),
        LexError(1);

        /**
         * @param code The exit code associated with this Result
         */
        private Result(int code) {
            this.code = code;
        }

        private final int code;

        /**
         * @return The exit code associated with this Result.
         */
        public int getCode() {
            return code;
        }
    }

}
