package edu.kit.compiler.semantic;

import edu.kit.compiler.data.AstObject;
import edu.kit.compiler.data.AstVisitor;
import edu.kit.compiler.data.DataType;
import edu.kit.compiler.data.DataType.DataTypeClass;
import edu.kit.compiler.data.ast_nodes.ClassNode;
import edu.kit.compiler.data.ast_nodes.ClassNode.ClassNodeField;
import edu.kit.compiler.data.ast_nodes.MethodNode;
import edu.kit.compiler.data.ast_nodes.MethodNode.DynamicMethodNode;
import edu.kit.compiler.data.ast_nodes.MethodNode.StaticMethodNode;
import edu.kit.compiler.data.ast_nodes.ProgramNode;
import edu.kit.compiler.lexer.StringTable;
import edu.kit.compiler.semantic.NamespaceMapper.ClassNamespace;
import lombok.Getter;
import lombok.NonNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

// todo we really need a map for static methods?

/**
 * Post Conditions:
 * - If any of the following conditions are not met, `hasError` will be set
 *   on the offending `AstObject`
 *     - The program does not contain classes with duplicate names
 *     - Classes do not contain fields with duplicate names
 *     - Methods do not have parameters with duplicate names
 *     - The program contains exactly one static method 
 *         - which is called main
 *         - which has return type void
 *         - which has exactly on parameter of type String[]
 * - If a class has duplicate fields, `hasError` will be set for every field
 *   with the same name except for the FIRST occurrence
 * - If a method has duplicate parameters, `hasError` will be set for every
 *   parameter with the same name, except for the LAST occurrence
 * - A predefined class String with no fields or methods exists
 * - Non-duplicate classes, fields and methods are registered in the `NamespaceMapper`
 *     - Classes are registered even if they contain duplicate fields
 *     - Methods are registered even if they contain duplicate parameters
 *     - At most one static method is registered
 * - The first static method with name main is registered in `mainMethod`
 */
public final class NamespaceGatheringVisitor implements AstVisitor<Void> {
    @Getter
    private final NamespaceMapper namespaceMapper;

    @NonNull
    private final StringTable stringTable;

    @NonNull
    private final ErrorHandler errorHandler;

    @Getter
    private Optional<StaticMethodNode> mainMethod = Optional.empty();

    @Getter
    private final ClassNode stringClass;

    public NamespaceGatheringVisitor(
        NamespaceMapper namespaceMapper,
        StringTable stringTable,
        ErrorHandler errorHandler
    ) {
        this.namespaceMapper = namespaceMapper;
        this.stringTable = stringTable;
        this.errorHandler = errorHandler;

        // Prevent classes called String from being created
        this.stringClass = new ClassNode(0, 0, stringTable.insert("String"),
                Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), false);
        namespaceMapper.insertClassNode(stringClass);
    }

    @Override
    public Void visit(ProgramNode program) {
        for (var classNode: program.getClasses()) {
            if (namespaceMapper.containsClassNamespace(classNode.getName())) {
                semanticError(classNode, "duplicate class %s",
                        stringTable.retrieve(classNode.getName()));
            } else {
                var namespace = namespaceMapper.insertClassNode(classNode);
                var visitor = new ClassGatherer(namespace);
                classNode.accept(visitor);
            }
        }

        if (mainMethod.isEmpty()) {
            semanticError(program, "the program must contain a static method with name main");
        }

        return (Void) null;
    }

    private void semanticError(AstObject object, String format, Object... args) {
        object.setHasError(true);
        semanticError(object.getLine(), object.getColumn(), format, args);
    }

    private void semanticError(int line, int column, String format, Object... args) {
        errorHandler.receive(new SemanticError(line, column, String.format(format, args)));
    }

    private final class ClassGatherer implements AstVisitor<Void> {
        private final ClassNode classNode;
        private final Map<Integer, ClassNodeField> fields;
        private final Map<Integer, StaticMethodNode> staticMethods;
        private final Map<Integer, DynamicMethodNode> dynamicMethods;

        public ClassGatherer(ClassNamespace namespace) {
            classNode = namespace.getClassNodeRef();
            fields = namespace.getClassSymbols();
            staticMethods = namespace.getStaticMethods();
            dynamicMethods = namespace.getDynamicMethods();
        }

        @Override
        public Void visit(ClassNode classNode) {
            for (var field : classNode.getFields()) {
                addField(field);
            }
            for (var method : classNode.getStaticMethods()) {
                method.accept(this);
            }
            for (var method : classNode.getDynamicMethods()) {
                method.accept(this);
            }
            return (Void)null;
        }

        @Override
        public Void visit(StaticMethodNode method) {
            visitMethodNode(method);

            if (hasMethod(method.getName())) {
                semanticError(method, "method %s is already defined in class %s",
                        stringTable.retrieve(method.getName()),
                        stringTable.retrieve(classNode.getName()));
            } else if (mainMethod.isPresent()) {
                semanticError(method, "only a single static method is allowed in the entire program");
            } else if (!stringTable.retrieve(method.getName()).equals("main")) {
                semanticError(method, "only a single static method, which must "
                        + "be called main, is allowed in the entire program");
            } else {
                staticMethods.put(method.getName(), method);
                mainMethod = Optional.of(method);

                if (!method.getType().equals(new DataType(DataTypeClass.Void))) {
                    semanticError(method, "main method must have return type void");
                } else if (method.getParameters().size() != 1
                        || !method.getParameters().get(0).getType()
                        .equals(new DataType(new DataType(stringClass.getName())))
                ) {
                    semanticError(method, "main method must have exactly one parameter of type String[]");
                }
            }
            return (Void)null;
        }

        @Override
        public Void visit(DynamicMethodNode method) {
            visitMethodNode(method);

            if (hasMethod(method.getName())) {
                semanticError(method, "method %s is already defined in class %s",
                        stringTable.retrieve(method.getName()),
                        stringTable.retrieve(classNode.getName())
                );
            } else {
                dynamicMethods.put(method.getName(), method);
            }
            return (Void) null;
        }

        private void visitMethodNode(MethodNode method) {
            var parameters = new HashMap<Integer, AstObject>();
            for (var parameter : method.getParameters()) {
                // Set `hasError` on the previous parameter with the same name (if one exists)
                var previousDefinition = parameters.put(parameter.getName(), parameter);
                if (previousDefinition != null) {
                    previousDefinition.setHasError(true);
                    semanticError(parameter.getLine(), parameter.getColumn(),
                            "parameter %s is already defined for method %s",
                            stringTable.retrieve(parameter.getName()),
                            stringTable.retrieve(method.getName()));
                }
            }
        }

        private void addField(ClassNodeField field) {
            if (!fields.containsKey(field.getName())) {
                fields.put(field.getName(), field);
            } else {
                semanticError(field, "field %s is already defined in class %s",
                        stringTable.retrieve(field.getName()),
                        stringTable.retrieve(classNode.getName()));
            }
        }

        private boolean hasMethod(int name) {
            return dynamicMethods.containsKey(name)
                    || staticMethods.containsKey(name);
        }
    }
}
