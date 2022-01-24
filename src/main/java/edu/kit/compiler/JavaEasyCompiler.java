package edu.kit.compiler;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

import edu.kit.compiler.assembly.AssemblyOptimizer;
import edu.kit.compiler.assembly.AssemblyWriter;
import edu.kit.compiler.assembly.ElfAssemblyWriter;
import edu.kit.compiler.assembly.FunctionInstructions;
import edu.kit.compiler.assembly.JumpInversion;
import edu.kit.compiler.assembly.RemoveNop;
import edu.kit.compiler.cli.Cli;
import edu.kit.compiler.cli.CliOption;
import edu.kit.compiler.cli.CliOptionGroup;
import edu.kit.compiler.cli.Cli.CliCall;
import edu.kit.compiler.codegen.InstructionSelection;
import edu.kit.compiler.codegen.PatternCollection;
import edu.kit.compiler.codegen.PhiResolver;
import edu.kit.compiler.codegen.ReversePostfixOrder;
import edu.kit.compiler.intermediate_lang.Block;
import edu.kit.compiler.optimizations.common_subexpression.CommonSubexpressionElimination;
import edu.kit.compiler.optimizations.inlining.InliningOptimization;
import edu.kit.compiler.register_allocation.DumbAllocator;
import edu.kit.compiler.register_allocation.LinearScan;
import edu.kit.compiler.register_allocation.RegisterAllocator;
import edu.kit.compiler.transform.IRVisitor;
import firm.*;

import edu.kit.compiler.data.CompilerException;
import edu.kit.compiler.data.Token;
import edu.kit.compiler.data.TokenType;
import edu.kit.compiler.data.ast_nodes.ProgramNode;
import edu.kit.compiler.lexer.Lexer;
import edu.kit.compiler.lexer.StringTable;
import edu.kit.compiler.logger.Logger;
import edu.kit.compiler.logger.Logger.Verbosity;
import edu.kit.compiler.optimizations.ArithmeticIdentitiesOptimization;
import edu.kit.compiler.optimizations.ArithmeticReplacementOptimization;
import edu.kit.compiler.optimizations.ConstantOptimization;
import edu.kit.compiler.optimizations.LinearBlocksOptimization;
import edu.kit.compiler.optimizations.Optimizer;
import edu.kit.compiler.optimizations.PureFunctionOptimization;
import edu.kit.compiler.parser.Parser;
import edu.kit.compiler.parser.PrettyPrintAstVisitor;
import edu.kit.compiler.semantic.DetailedNameTypeAstVisitor;
import edu.kit.compiler.semantic.ErrorHandler;
import edu.kit.compiler.semantic.NamespaceGatheringVisitor;
import edu.kit.compiler.semantic.NamespaceMapper;
import edu.kit.compiler.semantic.SemanticChecks;
import edu.kit.compiler.transform.JFirmSingleton;
import edu.kit.compiler.transform.Lower;

import lombok.AllArgsConstructor;
import lombok.Getter;

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
            createAttributedAst(logger, lexer, namespaceMapper);

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
    private static Result compileFirm(String filePath, Logger logger, Optimizer optimizer) {
        try {
            createOptimizedIR(filePath, logger, optimizer);

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
            var process = Runtime.getRuntime().exec(new String[] { "gcc", assemblyFile, stdLibrary });

            if (process.waitFor() != 0) {
                logger.error("gcc failed with exit code %s", process.exitValue());
                return Result.GccError;
            }

            return Result.Ok;
        } catch (CompilerException e) {
            logger.withName(e.getCompilerStage().orElse(null)).exception(e);

            return e.getResult();
        } catch (IOException e) {
            logger.error("unable to read file: %s", e.getMessage());

            return Result.FileInputError;
        } catch (InterruptedException e) {
            logger.error("gcc was interrupted: %s", e.getMessage());

            return Result.GccError;
        }
    }

    /**
     * Compiles the file. This includes parsing, semantic analysis, transformation and codegen.
     *
     * @param filePath Path of the file (absolute or relative)
     * @param logger the logger
     * @return Ok or an according error
     */
    private static Result compile(String filePath, Logger logger, Optimizer optimizer,
                                  RegisterAllocator allocator, AssemblyOptimizer asmOptimizer) {
        try {
            var graphs = createOptimizedIR(filePath, logger, optimizer);

            PatternCollection coll = new PatternCollection();
            List<FunctionInstructions> functions = new ArrayList<>();
            int blockId = 0;
            for (Graph graph : graphs) {
                InstructionSelection selection = InstructionSelection.apply(graph, coll, blockId);
                Map<Integer, Block> blockMapping = PhiResolver.apply(selection);
                List<Block> il = ReversePostfixOrder.apply(blockMapping, selection.getBlocks().getStartBlock().getLabel());
                blockId = selection.getBlocks().newLabel();

                var type = (MethodType) graph.getEntity().getType();
                int nArgs = type.getNParams();
                List<String> instructions = allocator.performAllocation(
                        nArgs, il, selection.getMatcher().getRegisterSizes());
                List<String> optimizedInstructions = asmOptimizer.apply(instructions);

                functions.add(new FunctionInstructions(graph.getEntity().getLdName(), optimizedInstructions));
            }

            AssemblyWriter writer = new ElfAssemblyWriter();

            var sourceFile = new File(filePath).getName();
            var assemblyFile = sourceFile + ".s";
            var out = new FileOutputStream(assemblyFile);
            logger.info("assembling program: '%s'", assemblyFile);

            writer.writeAssembly(functions, out);

            var stdLibrary = System.getenv("STD_LIBRARY_PATH");
            if (stdLibrary == null || !new File(stdLibrary).exists()) {
                logger.error("standard library implementation not found");
                return Result.StandardLibraryError;
            }

            logger.info("compiling program: 'gcc \"%s\" \"%s\"'", assemblyFile, stdLibrary);
            var process = Runtime.getRuntime().exec(new String[] { "gcc", assemblyFile, stdLibrary });

            if (process.waitFor() != 0) {
                logger.error("gcc failed with exit code %s", process.exitValue());
                return Result.GccError;
            }

            return Result.Ok;
        } catch (CompilerException e) {
            logger.withName(e.getCompilerStage().orElse(null)).exception(e);

            return e.getResult();
        } catch (IOException e) {
            logger.error("unable to read file: %s", e.getMessage());

            return Result.FileInputError;
        } catch (InterruptedException e) {
            logger.error("gcc was interrupted: %s", e.getMessage());

            return Result.GccError;
        }
    }

    /**
     * Parses the file into an AST and performs semantic analysis,
     * filling the provided namespace mapper.
     *
     * @param logger the logger
     * @param namespaceMapper empty namespace mapper
     * @return the AST
     * 
     * @throws CompilerException
     * @throws IOException
     */
    private static ProgramNode createAttributedAst(Logger logger,
            Lexer lexer, NamespaceMapper namespaceMapper) throws IOException {
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
            namespaceMapper, stringTable, errorHandler
        );
        ast.accept(nameTypeVisitor);
        // remaining semantic checks
        SemanticChecks.applyChecks(ast, errorHandler, gatheringVisitor.getStringClass());

        errorHandler.checkForErrors();
        return ast;
    }

    /**
     * Parses the file, performs semantic analysis, creates and optimizes the IR.
     *
     * @param filePath Path of the file (absolute or relative)
     * @param logger the logger
     * @param optimizer the optimizer
     * @return the set of living functions
     */
    private static Set<Graph> createOptimizedIR(String filePath, Logger logger,
                                                Optimizer optimizer) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(filePath)));
        Lexer lexer = new Lexer(reader);
        StringTable stringTable = lexer.getStringTable();
        NamespaceMapper namespaceMapper = new NamespaceMapper();
        ProgramNode ast = createAttributedAst(logger, lexer, namespaceMapper);

        JFirmSingleton.initializeFirmLinux();
        logger.info("Initialized libFirm Version: %s.%s",
                Firm.getMinorVersion(), Firm.getMajorVersion()
        );

        IRVisitor irv = new IRVisitor(namespaceMapper, stringTable);
        ast.accept(irv);
        Entity main = Lower.lower(irv.getTypeMapper());

        return optimizer.optimize(main);
    }

    public static void main(String[] args) {
        Cli cli = new Cli(
            Arrays
                .stream(CliOptionGroups.values())
                .map(group -> group.getOptionGroup())
                .collect(Collectors.toList())
        );
        CliCall cliCall = parseCliCall(cli, args);

        Logger logger = parseLogger(cliCall);
        OptimizationLevel optimizationLevel = parseOptimizationLevel(cliCall);
        DebugFlags debugFlags = parseDebugFlags(cliCall);

        Optimizer optimizer;
        RegisterAllocator allocator;
        AssemblyOptimizer asmOptimizer;
        switch (optimizationLevel) {
            case Level0:
                optimizer = new Optimizer(List.of(), List.of(
                    new ConstantOptimization(),
                    new ArithmeticIdentitiesOptimization()
                ), debugFlags);
                allocator = new DumbAllocator();
                asmOptimizer = new AssemblyOptimizer(List.of());
                break;
            case Level1:
                optimizer = new Optimizer(List.of(), List.of(
                    new ConstantOptimization(),
                    new ArithmeticIdentitiesOptimization(),
                    new ArithmeticReplacementOptimization(),
                    new LinearBlocksOptimization(),
                    new CommonSubexpressionElimination(),
                    new InliningOptimization(),
                    new PureFunctionOptimization()
                ), debugFlags);
                allocator = new LinearScan();
                asmOptimizer = new AssemblyOptimizer(List.of(
                    new RemoveNop(),
                    new JumpInversion()
                ));
                break;
            default:
                throw new UnsupportedOperationException("unsupported optimization level");
        }

        // execute requested function
        Result result;
        if (cliCall.hasOption(CliOptions.Help.getOption())) {
            cli.printHelp(
                "Java Easy Compiler\n\n" +
                "usage: compiler [<action>] [<optimization-level>] [<output-verbosity>] [<debug options>]",
                "for more information check out: https://github.com/larsk21/compiler-minijava"
            );
            result = Result.Ok;
        } else if (cliCall.hasOption(CliOptions.Echo.getOption())) {
            String filePath = cliCall.getOptionArg(CliOptions.Echo.getOption());
            result = echo(filePath, System.out, logger);
        } else if (cliCall.hasOption(CliOptions.LexTest.getOption())) {
            String filePath = cliCall.getOptionArg(CliOptions.LexTest.getOption());

            result = lextest(filePath, logger);
        } else if (cliCall.hasOption(CliOptions.ParseTest.getOption())) {
            String filePath = cliCall.getOptionArg(CliOptions.ParseTest.getOption());

            result = parseTest(filePath, logger);
        } else if (cliCall.hasOption(CliOptions.PrintAst.getOption())) {
            String filePath = cliCall.getOptionArg(CliOptions.PrintAst.getOption());

            result = prettyPrint(filePath, logger);
        } else if (cliCall.hasOption(CliOptions.Check.getOption())) {
            String filePath = cliCall.getOptionArg(CliOptions.Check.getOption());

            result = check(filePath, logger);
        } else if (cliCall.hasOption(CliOptions.CompileFirm.getOption())) {
            String filePath = cliCall.getOptionArg(CliOptions.CompileFirm.getOption());

            result = compileFirm(filePath, logger, optimizer);
        } else if (cliCall.hasOption(CliOptions.Compile.getOption())) {
            String filePath = cliCall.getOptionArg(CliOptions.Compile.getOption());

            result = compile(filePath, logger, optimizer, allocator, asmOptimizer);
        }  else {
            if (cliCall.getFreeArgs().length == 0) {
                System.err.println("Wrong command line arguments, see --help for supported commands.");

                result = Result.CliInputError;
            } else {
                String filePath = cliCall.getFreeArgs()[0];

                result = compile(filePath, logger, optimizer, allocator, asmOptimizer);
            }
        }

        // return exit code from executed function
        System.exit(result.getCode());
    }

    private static CliCall parseCliCall(Cli cli, String[] args) {
        Optional<CliCall> cliCall_ = cli.parse(args);
        if (cliCall_.isPresent()) {
            return cliCall_.get();
        } else {
            System.err.println("Wrong command line arguments, see --help for supported commands.");

            System.exit(Result.CliInputError.getCode());
            return null; // never reached, necessary to please the Java compiler
        }
    }

    private static Logger parseLogger(CliCall cliCall) {
        boolean printColor = System.getenv("COLOR") != null;

        Verbosity verbosity;
        if (cliCall.hasOption(CliOptions.Verbose.getOption())) {
            verbosity = Verbosity.VERBOSE;
        } else if (cliCall.hasOption(CliOptions.Debug.getOption())) {
            verbosity = Verbosity.DEBUG;
        } else {
            verbosity = Verbosity.DEFAULT;
        }

        return new Logger(verbosity, printColor);
    }

    private static OptimizationLevel parseOptimizationLevel(CliCall cliCall) {
        if (cliCall.hasOption(CliOptions.Optimize0.getOption())) {
            return OptimizationLevel.Level0;
        } else if (cliCall.hasOption(CliOptions.Optimize1.getOption())) {
            return OptimizationLevel.Level1;
        } else {
            return OptimizationLevel.Level1;
        }
    }

    private static DebugFlags parseDebugFlags(CliCall cliCall) {
        DebugFlags debugFlags = new DebugFlags();

        if (cliCall.hasOption(CliOptions.DumpGraphs.getOption())) {
            debugFlags.setDumpGraphs(true);
        }

        return debugFlags;
    }

    @AllArgsConstructor
    public static enum CliOptions {
        Echo(new CliOption("e", "echo", Optional.of("path"), "output file contents")),
        LexTest(new CliOption("l", "lextest", Optional.of("path"), "output the tokens from the lexer")),
        ParseTest(new CliOption("p", "parsetest", Optional.of("path"), "try to parse the file contents")),
        PrintAst(new CliOption("a", "print-ast", Optional.of("path"), "try to parse the file contents and output the AST")),
        Check(new CliOption("c", "check", Optional.of("path"), "try to parse the file contents and perform semantic analysis")),
        CompileFirm(new CliOption("f", "compile-firm", Optional.of("path"), "transform the file to Firm IR and compile it using the Firm backend")),
        Compile(new CliOption("co", "compile", Optional.of("path"), "compile the file")),

        Optimize0(new CliOption("O0", "optimize0", Optional.empty(), "run (almost) no optimizations")),
        Optimize1(new CliOption("O1", "optimize1", Optional.empty(), "run standard optimizations (default)")),

        Verbose(new CliOption("v", "verbose", Optional.empty(), "be more verbose")),
        Debug(new CliOption("d", "debug", Optional.empty(), "print debug information")),

        DumpGraphs(new CliOption("dg", "dump-graphs", Optional.empty(), "dump the Firm graphs of all methods")),

        Help(new CliOption("h", "help", Optional.empty(), "print command line syntax help"));

        @Getter
        private CliOption option;

    }

    @AllArgsConstructor
    public static enum CliOptionGroups {
        Action(new CliOptionGroup("Action", true, Arrays.asList(
            CliOptions.Echo.getOption(),
            CliOptions.LexTest.getOption(),
            CliOptions.ParseTest.getOption(),
            CliOptions.PrintAst.getOption(),
            CliOptions.Check.getOption(),
            CliOptions.CompileFirm.getOption(),
            CliOptions.Compile.getOption()
        ))),
        OptimizationLevel(new CliOptionGroup("Optimization Level", true, Arrays.asList(
            CliOptions.Optimize0.getOption(),
            CliOptions.Optimize1.getOption()
        ))),
        OutputVerbosity(new CliOptionGroup("Output Verbosity", true, Arrays.asList(
            CliOptions.Verbose.getOption(),
            CliOptions.Debug.getOption()
        ))),
        DebugOptions(new CliOptionGroup("Debug Options", false, Arrays.asList(
            CliOptions.DumpGraphs.getOption()
        ))),
        Help(new CliOptionGroup("Help", false, Arrays.asList(
            CliOptions.Help.getOption()
        )));

        @Getter
        private CliOptionGroup optionGroup;

    }

}
