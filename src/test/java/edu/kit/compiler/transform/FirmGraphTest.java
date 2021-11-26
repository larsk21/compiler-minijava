package edu.kit.compiler.transform;

import edu.kit.compiler.JavaEasyCompiler;
import edu.kit.compiler.data.ast_nodes.ProgramNode;
import edu.kit.compiler.lexer.Lexer;
import edu.kit.compiler.lexer.StringTable;
import edu.kit.compiler.logger.Logger;
import edu.kit.compiler.semantic.NamespaceMapper;
import firm.Firm;
import firm.Graph;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Objects;

public class FirmGraphTest {

    private final ClassLoader classLoader = getClass().getClassLoader();
    private static final Logger log = new Logger();

    @BeforeAll
    public static void initializeFirm() {
        JFirmSingleton.initializeFirmLinux();
        log.info("Initialized libFirm Version: %s.%s\n",
                Firm.getMinorVersion(), Firm.getMajorVersion()
        );
    }

    @Test
    @Disabled
    public void testFirmGraphCreation() throws IOException {
        final String FILE = "edu/kit/compiler/transform/FullClass.java";

        var path = Objects.requireNonNull(classLoader.getResource(FILE)).getPath();
        Graph g = createFirmGraph(path);

        FirmGraphFactory.dumpTypeGraph();
        FirmGraphFactory.dumpGraph(g);
    }

    @Test
    public void testFirmGraphCreationSimple() throws IOException {
        final String FILE = "edu/kit/compiler/transform/LiteralExpressionNodes.java";

        var path = Objects.requireNonNull(classLoader.getResource(FILE)).getPath();
        Graph g = createFirmGraph(path);

        FirmGraphFactory.dumpTypeGraph();
        FirmGraphFactory.dumpGraph(g);
    }


    /**
     * Creates firm graph and initializes firm graph factory
     *
     * @param filePath path to the java file for that we create firm graphs
     * @return
     * @throws IOException
     */
    private Graph createFirmGraph(String filePath) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(filePath)))) {
            Lexer lexer = new Lexer(reader);
            StringTable stringTable = lexer.getStringTable();
            NamespaceMapper namespaceMapper = new NamespaceMapper();
            FirmGraphFactory firmGraphFactory = new FirmGraphFactory(namespaceMapper, stringTable);

            ProgramNode programNode = JavaEasyCompiler.createAttributedAst(reader, log, lexer, firmGraphFactory.getNamespaceMapper());
            Graph g = null;
            try {
                g = firmGraphFactory.createGraphFromAst(programNode);
            } catch (Exception e) {
                e.printStackTrace();
                throw (e);
            }

            return g;
        }
    }
}
