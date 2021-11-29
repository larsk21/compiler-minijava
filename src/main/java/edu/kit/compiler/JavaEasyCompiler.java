package edu.kit.compiler;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;

import edu.kit.compiler.data.CompilerException;
import edu.kit.compiler.data.Token;
import edu.kit.compiler.data.TokenType;
import edu.kit.compiler.data.ast_nodes.ProgramNode;
import edu.kit.compiler.lexer.Lexer;
import edu.kit.compiler.lexer.StringTable;
import edu.kit.compiler.logger.Logger;
import edu.kit.compiler.logger.Logger.Verbosity;
import edu.kit.compiler.transform.FirmGraphFactory;
import edu.kit.compiler.parser.Parser;
import edu.kit.compiler.parser.PrettyPrintAstVisitor;
import edu.kit.compiler.semantic.DetailedNameTypeAstVisitor;
import edu.kit.compiler.semantic.ErrorHandler;
import edu.kit.compiler.semantic.NamespaceGatheringVisitor;
import edu.kit.compiler.semantic.NamespaceMapper;
import edu.kit.compiler.semantic.SemanticChecks;
import edu.kit.compiler.transform.JFirmSingleton;
import edu.kit.compiler.transform.Lower;
import edu.kit.compiler.transform.TypeMapper;
import firm.Backend;
import firm.Firm;

public class JavaEasyCompiler {
    /**
     * Output the file contents to stdout.
     * 
     * @param filePath Path of the file (absolute or relative)
     * @param logger the logger
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
     * @param logger the logger
     * @return Ok or FileInputError (in case of an IOException)
     */
    private static Result lextest(String filePath, Logger logger) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(filePath)))) {
            Lexer lexer = new Lexer(reader, logger);
            StringTable stringTable = lexer.getStringTable();

            Token token;
            do {
                token = lexer.getNextToken();
                System.out.println(token.getStringRepresentation(stringTable));
            } while (token.getType() != TokenType.EndOfStream);

            return Result.Ok;
        } catch (CompilerException e) {
            logger.withName(e.getCompilerStage().orElse(null)).exception(e);

            return e.getResult();
        } catch (IOException e) {
            logger.error("unable to read file: %s", e.getMessage());

            return Result.FileInputError;
        }
    }

    /**
     * Parses the file.
     * 
     * @param filePath Path of the file (absolute or relative)
     * @param logger the logger
     * @return Ok or an according error
     */
    private static Result parseTest(String filePath, Logger logger) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(filePath)))) {
            Parser parser = new Parser(new Lexer(reader, logger));
            parser.parse();

            return Result.Ok;
        } catch (CompilerException e) {
            logger.withName(e.getCompilerStage().orElse(null)).exception(e);

            return e.getResult();
        } catch (IOException e) {
            logger.error("unable to read file: %s", e.getMessage());

            return Result.FileInputError;
        }
    }

    /**
     * Parses the file and outputs a pretty printed version of the AST.
     *
     * @param filePath Path of the file (absolute or relative)
     * @param logger the logger
     * @return Ok or an according error
     */
    private static Result prettyPrint(String filePath, Logger logger) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(filePath)))) {
            Lexer lexer = new Lexer(reader, logger);
            StringTable stringTable = lexer.getStringTable();
            ProgramNode ast = (new Parser(lexer)).parse();
            ast.accept(new PrettyPrintAstVisitor(stringTable));

            return Result.Ok;
        } catch (CompilerException e) {
            logger.withName(e.getCompilerStage().orElse(null)).exception(e);

            return e.getResult();
        } catch (IOException e) {
            logger.error("unable to read file: %s", e.getMessage());

            return Result.FileInputError;
        }
    }

    /**
     * Parses the file and performs semantic analysis.
     *
     * @param filePath Path of the file (absolute or relative)
     * @param logger the logger
     * @return Ok or an according error
     */
    private static Result check(String filePath, Logger logger) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(filePath)))) {
            Lexer lexer = new Lexer(reader);
            NamespaceMapper namespaceMapper = new NamespaceMapper();
            createAttributedAst(reader, logger, lexer, namespaceMapper);

            return Result.Ok;
        } catch (CompilerException e) {
            logger.withName(e.getCompilerStage().orElse(null)).exception(e);

            return e.getResult();
        } catch (IOException e) {
            logger.error("unable to read file: %s", e.getMessage());

            return Result.FileInputError;
        }
    }

    /**
     * Parses the file and performs semantic analysis.
     *
     * @param filePath Path of the file (absolute or relative)
     * @param logger the logger
     * @return Ok or an according error
     */
    private static Result compileFirm(String filePath, Logger logger) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(filePath)))) {
            Lexer lexer = new Lexer(reader);
            StringTable stringTable = lexer.getStringTable();
            NamespaceMapper namespaceMapper = new NamespaceMapper();
            ProgramNode ast = createAttributedAst(reader, logger, lexer, namespaceMapper);

            JFirmSingleton.initializeFirmLinux();
            logger.info("Initialized libFirm Version: %s.%s",
                Firm.getMinorVersion(), Firm.getMajorVersion()
            );

            TypeMapper typeMapper = new TypeMapper(namespaceMapper, stringTable);
            FirmGraphFactory firmGraphFactory = new FirmGraphFactory(namespaceMapper, stringTable);
            firmGraphFactory.visitAST(ast);
            firmGraphFactory.dumpTypeGraph();

            // todo insert Firm graph generation here

            Lower.lower(typeMapper);

            var sourceFile = new File(filePath).getName();
            var assemblyFile = sourceFile + ".s";
            logger.info("assembling program: '%s'", assemblyFile);
            Backend.createAssembler(assemblyFile, sourceFile);

            var stdLibrary = System.getenv("STD_LIBRARY_PATH");
            if (stdLibrary == null || !new File(stdLibrary).exists()) {
                logger.error("standard library implementation not found");
                return Result.StandardLibraryError;
            }

            logger.info("compiling program: 'gcc \"%s\" \"%s\"'", assemblyFile, stdLibrary);
            Runtime.getRuntime().exec(new String[]{ "gcc", assemblyFile, stdLibrary });
            
            return Result.Ok;
        } catch (CompilerException e) {
            logger.withName(e.getCompilerStage().orElse(null)).exception(e);

            return e.getResult();
        } catch (IOException e) {
            logger.error("unable to read file: %s", e.getMessage());

            return Result.FileInputError;
        }
    }

    /**
     * Parses the file into an AST and performs semantic analysis,
     * filling the provided namespace mapper.
     *
     * @param filePath Path of the file (absolute or relative)
     * @param logger the logger
     * @param namespaceMapper empty namespace mapper
     * @return the AST
     * 
     * @throws CompilerException
     * @throws IOException
     */
    public static ProgramNode createAttributedAst(
            Reader reader, Logger logger, Lexer lexer, NamespaceMapper namespaceMapper
        ) throws IOException {
        ErrorHandler errorHandler = new ErrorHandler(logger);
        StringTable stringTable = lexer.getStringTable();
        ProgramNode ast = (new Parser(lexer)).parse();

        // collect classes and methods
        NamespaceGatheringVisitor gatheringVisitor = new NamespaceGatheringVisitor(
            namespaceMapper, stringTable, errorHandler
        );
        ast.accept(gatheringVisitor);
        // name and type analysis
        DetailedNameTypeAstVisitor nameTypeVisitor = new DetailedNameTypeAstVisitor(
            namespaceMapper, stringTable
        );
        ast.accept(nameTypeVisitor);
        // remaining semantic checks
        SemanticChecks.applyChecks(ast, errorHandler, gatheringVisitor.getStringClass());

        errorHandler.checkForErrors();
        return ast;
    }

    public static void main(String[] args) {
        // specify supported command line options
        Options options = new Options();
        options.addOption("h", "help", false, "print command line syntax help");

        var runOptions = new OptionGroup();
        runOptions.addOption(new Option("e", "echo", true, "output file contents"));
        runOptions.addOption(new Option("l", "lextest", true, "output the tokens from the lexer"));
        runOptions.addOption(new Option("p", "parsetest", true, "try to parse the file contents"));
        runOptions.addOption(new Option("a", "print-ast", true, "try to parse the file contents and output the AST"));
        runOptions.addOption(new Option("c", "check", true, "try to parse the file contents and perform semantic analysis"));
        runOptions.addOption(new Option("f", "compile-firm", true, "transform the file to Firm IR and compile it using the Firm backend"));
        options.addOptionGroup(runOptions);

        var verbosityOptions = new OptionGroup();
        verbosityOptions.addOption(new Option("v", "verbose", false, "be more verbose"));
        verbosityOptions.addOption(new Option("d", "debug", false, "print debug information"));
        options.addOptionGroup(verbosityOptions);

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

        var logger = parseLogger(cmd);

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
        } else if (cmd.hasOption("a")) {
            String filePath = cmd.getOptionValue("a");

            result = prettyPrint(filePath, logger);
        } else if (cmd.hasOption("c")) {
            String filePath = cmd.getOptionValue("c");

            result = check(filePath, logger);
        } else if (cmd.hasOption("f")) {
            String filePath = cmd.getOptionValue("f");

            result = compileFirm(filePath, logger);
        } else {
            System.err.println("Wrong command line arguments, see --help for supported commands.");

            result = Result.CliInputError;
        }

        // return exit code from executed function
        System.exit(result.getCode());
    }

    private static Logger parseLogger(CommandLine cmd) {
        var printColor = System.getenv("COLOR") != null;

        Verbosity verbosity;
        if (cmd.hasOption("v")) {
            verbosity = Verbosity.VERBOSE;
        } else if (cmd.hasOption("d")) {
            verbosity = Verbosity.DEBUG;
        } else {
            verbosity = Verbosity.DEFAULT;
        }
        
        return new Logger(verbosity, printColor);
    }

}
