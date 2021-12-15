package edu.kit.compiler.codegen;

import edu.kit.compiler.data.ast_nodes.ProgramNode;
import edu.kit.compiler.lexer.Lexer;
import edu.kit.compiler.lexer.StringTable;
import edu.kit.compiler.logger.Logger;
import edu.kit.compiler.parser.Parser;
import edu.kit.compiler.semantic.DetailedNameTypeAstVisitor;
import edu.kit.compiler.semantic.ErrorHandler;
import edu.kit.compiler.semantic.NamespaceGatheringVisitor;
import edu.kit.compiler.semantic.NamespaceMapper;
import edu.kit.compiler.transform.IRVisitor;
import edu.kit.compiler.transform.JFirmSingleton;
import firm.Dump;
import firm.Graph;
import firm.Program;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class SimpleTestCompiler {

    private static final ClassLoader systemClassLoader = ClassLoader.getSystemClassLoader();
    private static final ClassLoader loader = ClassLoader.getPlatformClassLoader();
    private static final File tempDir;

    // temp test resources
    static {
        String p = Objects.requireNonNull(systemClassLoader.getResource(".")).getFile();
        tempDir = new File(p + "/tmp");
        if(!tempDir.exists()) {
            try {
                Files.createDirectory(tempDir.toPath());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (tempDir.exists()) {
            // rm tmp
            String[] entries = tempDir.list();
            assert entries != null;
            for (String s : entries) {
                File currentFile = new File(tempDir.getPath(), s);
                boolean success = currentFile.delete();
                if(!success) {
                    try {
                        throw new RuntimeException("could not delete tmp file " + currentFile.getCanonicalPath());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    public static void dumpGraph(Graph g) throws IOException {
        Dump.dumpGraph(g, "dumped-graph");
        String p = Objects.requireNonNull(systemClassLoader.getResource(".")).getFile();
        File rootDir = new File(p + "/../../");
        if(!rootDir.exists()) {
            throw new IllegalStateException("could not recover main directory");
        }
        for(String s : rootDir.list()) {
            if (s.contains("dumped-graph")) {
                // move to output directory
                File currentFile = new File(rootDir.getPath(), s);
                if(!currentFile.exists()) {
                    throw new IllegalStateException("output graph does not exist!" + currentFile);
                }
                File targetFile = new File(tempDir.getPath(), s);
                Files.move(currentFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

                currentFile.delete();
                System.out.printf("moved file %s\n", s);

            }
        }
    }

    public static List<Graph> getFirmGraphs(String inputFile) throws IOException {
        JFirmSingleton.initializeFirmLinux();

        URL path = systemClassLoader.getResource(inputFile);
        ErrorHandler errorHandler = new ErrorHandler(Logger.nullLogger());

        ProgramNode ast;
        StringTable st;
        try {
            assert path != null;
            try (FileReader reader = new FileReader(path.getFile())) {
                Lexer lexer = new Lexer(reader);
                ast = new Parser(lexer).parse();
                st = lexer.getStringTable();
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw (e);
        }

        NamespaceMapper namespaceMapper = new NamespaceMapper();
        NamespaceGatheringVisitor visitor = new NamespaceGatheringVisitor(
                namespaceMapper, st, errorHandler
        );
        ast.accept(visitor);
        DetailedNameTypeAstVisitor dntv = new DetailedNameTypeAstVisitor(namespaceMapper, st);
        ast.accept(dntv);

        IRVisitor irv = new IRVisitor(namespaceMapper, st);
        try {
            ast.accept(irv);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return StreamSupport.stream(Program.getGraphs().spliterator(), false).collect(Collectors.toList());
    }

}
