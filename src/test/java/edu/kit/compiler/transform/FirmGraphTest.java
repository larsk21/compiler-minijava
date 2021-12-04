package edu.kit.compiler.transform;

import edu.kit.compiler.JavaEasyCompiler;
import edu.kit.compiler.lexer.Lexer;
import edu.kit.compiler.lexer.StringTable;
import edu.kit.compiler.logger.Logger;
import edu.kit.compiler.semantic.NamespaceMapper;
import firm.Firm;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Objects;

public class FirmGraphTest {

    private final ClassLoader classLoader = getClass().getClassLoader();
    private static final String FILE = "edu/kit/compiler/transform/FullClass.java";

    @Test
    public void testFirmGraphCreation() throws IOException {
        var path = Objects.requireNonNull(classLoader.getResource(FILE)).getPath();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(path)))) {
            Lexer lexer = new Lexer(reader);
            StringTable stringTable = lexer.getStringTable();
            NamespaceMapper namespaceMapper = new NamespaceMapper();

            Logger log = new Logger();
            JavaEasyCompiler.createAttributedAst(reader, log, lexer, namespaceMapper);

            JFirmSingleton.initializeFirmLinux();
            log.info("Initialized libFirm Version: %s.%s\n",
                    Firm.getMinorVersion(), Firm.getMajorVersion()
            );

            FirmGraphFactory firmGraphFactory = new FirmGraphFactory(namespaceMapper, stringTable);
            firmGraphFactory.createGraphFromAst();
            firmGraphFactory.dumpGraph();
        }
    }
}
