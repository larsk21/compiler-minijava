package edu.kit.compiler;

import edu.kit.compiler.data.Token;
import edu.kit.compiler.data.TokenType;
import edu.kit.compiler.io.ReaderCharIterator;
import edu.kit.compiler.lexer.LexException;
import edu.kit.compiler.lexer.Lexer;
import edu.kit.compiler.lexer.StringTable;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.*;
import edu.kit.compiler.parser.Parser;
import edu.kit.compiler.parser.ParseException;
import edu.kit.compiler.logger.Logger;
import edu.kit.compiler.logger.Logger.Verbosity;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;

@Slf4j
public class JavaEasyCompiler {
    /**
     * Output the file contents to stdout.
     * 
     * @param filePath Path of the file (absolute or relative)
     * @return Ok or FileInputError (in case of an IOException)
     */
    public static Result echo(String filePath, OutputStream oStream, Logger logger) {
        try (InputStream iStream = new FileInputStream(filePath)) {
            iStream.transferTo(oStream);
            return Result.Ok;
        } catch (IOException e) {
            logger.error("unable to read file: %s", e.getMessage());
            return Result.FileInputError;
        }
    }

    /**
     * Split the file contents in Lexer Tokens and output the representations one Token per line.
     * 
     * @param filePath Path of the file (absolute or relative)
     * @return Ok or FileInputError (in case of an IOException)
     */
    private static Result lextest(String filePath, Logger logger) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(filePath)))) {
            Lexer lexer = new Lexer(new ReaderCharIterator(reader), logger);
            StringTable stringTable = lexer.getStringTable();

            Token token;
            do {
                token = lexer.getNextToken();
                System.out.println(token.getStringRepresentation(stringTable));
            } while (token.getType() != TokenType.EndOfStream);

            return Result.Ok;
        } catch (LexException e) {
            logger.withName("lexer").error(e.getLine(), e.getColumn(), e.getMessage());

            return Result.LexError;
        } catch (IOException e) {
            logger.error("unable to read file: %s", e.getMessage());

            return Result.FileInputError;
        }
    }

    /**
     * Split the file contents in Lexer Tokens and output the representations one Token per line.
     * 
     * @param filePath Path of the file (absolute or relative)
     * @return Ok or an according error
     */
    private static Result parseTest(String filePath, Logger logger) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(filePath)))) {
            Parser parser = new Parser(new Lexer(new ReaderCharIterator(reader)));
            parser.parse();

            return Result.Ok;
        } catch (ParseException e) {
            logger.withName("parser").error(e.getLine(), e.getColumn(), e.getMessage());

            return Result.ParseError;
        } catch (LexException e) {
            logger.withName("lexer").error(e.getLine(), e.getColumn(), e.getMessage());

            return Result.LexError;
        } catch (IOException e) {
            logger.error("unable to read file: %s", e.getMessage());

            return Result.FileInputError;
        }
    }

    public static void main(String[] args) {
        // specify supported command line options
        Options options = new Options();
        options.addOption("h", "help", false, "print command line syntax help");
        options.addOption("e", "echo", true, "output file contents");
        options.addOption("l", "lextest", true, "output the tokens from the lexer");
        options.addOption("p", "parsetest", true, "try to parse the file contents");
        options.addOption("v", "verbose", false, "be more verbose");

        // parse command line arguments
        CommandLine cmd;
        try {
            CommandLineParser parser = new DefaultParser(false);

            cmd = parser.parse(options, args);
        } catch (org.apache.commons.cli.ParseException e) {
            System.err.println("Wrong command line arguments, see --help for supported commands.");

            System.exit(Result.CliInputError.getCode());
            return;
        }

        var printColor = System.getenv("COLOR") != null;
        var verbosity = cmd.hasOption("v") ? Verbosity.Verbose : Verbosity.Default;
        var logger = new Logger(verbosity, printColor);

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
            result = echo(filePath, System.out, logger);
        } else if (cmd.hasOption("l")) {
            String filePath = cmd.getOptionValue("l");

            result = lextest(filePath, logger);
        } else if (cmd.hasOption("p")) {
            String filePath = cmd.getOptionValue("p");

            result = parseTest(filePath, logger);
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
        LexError(1),
        ParseError(1);

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
