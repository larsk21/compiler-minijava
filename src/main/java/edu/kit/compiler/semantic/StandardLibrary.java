package edu.kit.compiler.semantic;

import edu.kit.compiler.data.DataType;
import edu.kit.compiler.data.DataType.DataTypeClass;
import edu.kit.compiler.data.ast_nodes.MethodNode;
import edu.kit.compiler.data.ast_nodes.MethodNode.StandardLibraryMethod;
import edu.kit.compiler.data.ast_nodes.MethodNode.StandardLibraryMethodNode;
import edu.kit.compiler.lexer.StringTable;

import lombok.Getter;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents the standard library "System" of MiniJava.
 * 
 * This class provides names and method definitions for the supported
 * standard library methods:
 * - System.in.read
 * - System.out.write
 * - System.out.println
 * - System.out.flush
 */
public class StandardLibrary {

    /**
     * Create the MiniJava standard library.
     */
    public static StandardLibrary create(StringTable stringTable) {
        StandardLibrary standardLibrary = new StandardLibrary(
            stringTable.insert("System"),
            stringTable.insert("in"),
            stringTable.insert("out")
        );

        standardLibrary.systemInMethods.put(
            stringTable.insert("read"),
            new StandardLibraryMethodNode(
                new DataType(DataType.DataTypeClass.Int),
                stringTable.insert("read"),
                Arrays.asList(),
                StandardLibraryMethod.Read
            )
        );

        standardLibrary.systemOutMethods.put(
            stringTable.insert("write"),
            new StandardLibraryMethodNode(
                new DataType(DataType.DataTypeClass.Void),
                stringTable.insert("write"),
                Arrays.asList(
                    new MethodNode.MethodNodeParameter(-1, -1, new DataType(DataTypeClass.Int), stringTable.insert("value"), false)
                ),
                StandardLibraryMethod.Write
            )
        );
        standardLibrary.systemOutMethods.put(
            stringTable.insert("println"),
            new StandardLibraryMethodNode(
                new DataType(DataType.DataTypeClass.Void),
                stringTable.insert("println"),
                Arrays.asList(
                    new MethodNode.MethodNodeParameter(-1, -1, new DataType(DataTypeClass.Int), stringTable.insert("value"), false)
                ),
                StandardLibraryMethod.PrintLn
            )
        );
        standardLibrary.systemOutMethods.put(
            stringTable.insert("flush"),
            new StandardLibraryMethodNode(
                new DataType(DataType.DataTypeClass.Void),
                stringTable.insert("flush"),
                Arrays.asList(),
                StandardLibraryMethod.Flush
            )
        );

        return standardLibrary;
    }

    private StandardLibrary(int systemName, int systemInName, int systemOutName) {
        this.systemName = systemName;
        this.systemInName = systemInName;
        this.systemOutName = systemOutName;

        this.systemInMethods = new HashMap<>();
        this.systemOutMethods = new HashMap<>();
    }

    /**
     * Get the name "System".
     */
    @Getter
    private int systemName;
    /**
     * Get the name "in".
     */
    @Getter
    private int systemInName;
    /**
     * Get the name "out".
     */
    @Getter
    private int systemOutName;

    /**
     * Get the supported methods of `System.in`.
     */
    @Getter
    private Map<Integer, MethodNode> systemInMethods;
    /**
     * Get the supported methods of `System.out`.
     */
    @Getter
    private Map<Integer, MethodNode> systemOutMethods;

}
