package edu.kit.compiler.transform;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.kit.compiler.data.DataType;
import edu.kit.compiler.data.ast_nodes.ClassNode;
import edu.kit.compiler.data.ast_nodes.ClassNode.ClassNodeField;
import edu.kit.compiler.data.ast_nodes.MethodNode;
import edu.kit.compiler.data.ast_nodes.MethodNode.MethodNodeParameter;
import edu.kit.compiler.lexer.StringTable;
import edu.kit.compiler.semantic.NamespaceMapper;
import edu.kit.compiler.semantic.NamespaceMapper.ClassNamespace;
import firm.ClassType;
import firm.Entity;
import firm.MethodType;
import firm.Mode;
import firm.PointerType;
import firm.PrimitiveType;
import firm.Type;
import firm.bindings.binding_typerep.ir_visibility;
import lombok.Getter;


/**
 * Provides the type system for a MiniJava program. Each class is mapped to a
 * `ClassEntry` which holds Firm entities for each of the classes members.
 */
public final class TypeMapper {

    private final Map<Integer, ClassEntry> classes = new HashMap<>();

    private final Type booleanType = new PrimitiveType(Mode.getBu());
    private final Type integerType = new PrimitiveType(Mode.getIs());

    private final StringTable stringTable;

    @Getter
    private Entity mainMethod;

    /**
     * Set up the type system for a MiniJava program. Registers a `ClassEntry`
     * for each class in the namespace mapper and populates it with entities
     * for each of the classes members.
     * 
     * @param namespaceMapper the class namespaces of the program
     * @param stringTable the `stringTable` used for the program
     */
    public TypeMapper(NamespaceMapper namespaceMapper, StringTable stringTable) {
        this.stringTable = stringTable;

        // Create an entry for every class and initialize it with entities for
        // fields and methods.
        for (var namespace : namespaceMapper.getNamespaceMap().values()) {
            var classId = namespace.getClassNodeRef().getName();
            var classEntry = classes.computeIfAbsent(classId, ClassEntry::new);
            classEntry.construct(namespace);
        }

        if (mainMethod == null) {
            // Semantic analysis would already have detected this
            throw new IllegalStateException(
                "did not find a main method; program is invalid");
        }
    }

    /**
     * Returns the corresponding Firm type for the given data type. If the given
     * type is an integer or boolean, a `PrimitiveType` is returned. An array
     * will return a `PointerType` to the inner type. A user defined type will
     * return a `PointerType` to the `ClassType`. Void and Any will throw an
     * exception.
     * 
     * @param type the data type whose corresponding Firm type is to be returned
     * @return the corresponding Firm type
     */
    public Type getDataType(DataType type) {
        return switch (type.getType()) {
            case Boolean -> booleanType;
            case Int -> integerType;
            case Array -> {
                var innerType = getDataType(type.getInnerType().get());
                yield new PointerType(innerType);
            }
            case UserDefined -> {
                var classEntry = classes.get(type.getIdentifier().get());
                yield new PointerType(classEntry.getClassType());
            } 
            case Void, Any -> throw new IllegalArgumentException(
                String.format("can't construct type for %s", type.getType())
            );
            default -> throw new IllegalStateException("unsupported data type");
        };
    }

    /**
     * Returns the entry corresponding to the class with the given name.
     * 
     * @param classId the name of the class whose entry is to be returned
     * @return the entry corresponding to the given class
     */
    public ClassEntry getClassEntry(int classId) {
        return classes.get(classId);
    }

    /**
     * Returns the entry corresponding to the given `ClassNode`.
     * 
     * @param classNode the class whose entry is to be returned
     * @return the entry corresponding to the given class
     */
    public ClassEntry getClassEntry(ClassNode classNode) {
        return getClassEntry(classNode.getName());
    }

    /**
     * Returns an immutable view of all classes registered in the type mapper.
     * 
     * @return an unmodifiable collection of all `ClassEntry`s
     */
    public Collection<ClassEntry> getClassEntries() {
        return Collections.unmodifiableCollection(classes.values());
    }

    /**
     * Represents all types and entities related to a single class in a MiniJava
     * program. This includes the type of the class itself, as well as an entity
     * for each field and method of the class. Types of fields and methods can
     * be accessed via the respective entities.
     */
    public final class ClassEntry {
        @Getter
        private final ClassType classType;
        private final Map<Integer, Entity> fields = new HashMap<>();
        private final Map<Integer, Entity> methods = new HashMap<>();

        private ClassEntry(int classId) {
            var className = stringTable.retrieve(classId);
            this.classType = new ClassType(className);
        }

        /**
         * Returns the Firm entity representing the field with the given name.
         * 
         * @param fieldId the name of the field whose entity is to be returned
         * @return the entity corresponding to the given field
         */
        public Entity getField(int fieldId) {
            return fields.get(fieldId);
        }

        /**
         * Returns the Firm entity representing the given field.
         * 
         * @param fieldId the field whose entity is to be returned
         * @return the entity corresponding to the given field
         */
        public Entity getField(ClassNodeField field) {
            return getField(field.getName());
        }

        /**
         * Returns an immutable view of all field entities of this class.
         * 
         * @return an unmodifiable collection of all field `Entity`s
         */
        public Collection<Entity> getFields() {
            return Collections.unmodifiableCollection(fields.values());
        }

        /**
         * Returns the Firm entity representing the method with the given name.
         * 
         * @param methodId the name of the method whose entity is to be returned
         * @return the entity corresponding to the given method
         */
        public Entity getMethod(int methodId) {
            return methods.get(methodId);
        }

        /**
         * Returns the Firm entity representing the given method.
         * 
         * @param methodId the method whose entity is to be returned
         * @return the entity corresponding to the given method
         */
        public Entity getMethod(MethodNode method) {
            return getMethod(method.getName());
        }

        /**
         * Returns an immutable view of all method entities of this class.
         * 
         * @return an unmodifiable collection of all method `Entity`s
         */
        public Collection<Entity> getMethods() {
            return Collections.unmodifiableCollection(methods.values());
        }

        /**
         * Constructs this `ClassEntry` with the fields and methods from the
         * given namespace.
         */
        private void construct(ClassNamespace namespace) {
            // Create and register entity for each field of the class
            constructFields(namespace);

            // Create and register entity for each method of the class
            constructMethods(namespace.getClassNodeRef(), namespace.getDynamicMethods(), false);
            constructMethods(namespace.getClassNodeRef(), namespace.getStaticMethods(), true);

            // ? maybe do later (after possible optimizations)
            classType.layoutFields();
            classType.finishLayout();
        }

        /**
         * Only called during construction. Creates an entity for each field in
         * the given namespace.
         */
        private void constructFields(ClassNamespace namespace) {
            for (var fieldNode : namespace.getClassSymbols().values()) {
                var fieldName = stringTable.retrieve(fieldNode.getName());
                var fieldType = initDataType(fieldNode.getType());

                var fieldEntity = new Entity(classType, fieldName, fieldType);
                // ? is this actually needed for non global entity?
                fieldEntity.setVisibility(ir_visibility.ir_visibility_local);
                fields.put(fieldNode.getName(), fieldEntity);
            }
        }

        /**
         * Only called during construction. Creates an entity for each static
         * and dynamic method in the given namespace.
         */
        private <T extends MethodNode> void constructMethods(
            ClassNode classNode, Map<Integer, T> methodNodes, boolean is_static
        ) {
            for (var methodNode : methodNodes.values()) {
                var methodName = stringTable.retrieve(methodNode.getName());
                var methodType = getMethodType(methodNode, is_static);

                var methodEntity = new Entity(classType, methodName, methodType);
                methodEntity.setVisibility(ir_visibility.ir_visibility_local);
                methods.put(methodNode.getName(), methodEntity);

                if (is_static) {
                    if (mainMethod != null || !methodName.equals("main")) {
                        throw new IllegalStateException(
                            "found illegal static method; program is invalid");
                    } else {
                        mainMethod = methodEntity;
                    }
                }
            }
        }

        /**
         * Only called during construction of TypeMapper. Creates entries for
         * user defined types if necessary.
         */
        private Type initDataType(DataType type) {
            return switch (type.getType()) {
                case Array -> {
                    var innerType = initDataType(type.getInnerType().get());
                    yield new PointerType(innerType);
                }
                case UserDefined -> {
                    var classId = type.getIdentifier().get();
                    var classEntry = classes.computeIfAbsent(classId, ClassEntry::new);
                    yield new PointerType(classEntry.getClassType());
                } 
                default -> getDataType(type);
            };
        }

        /**
         * Only called during construction.
         */
        private MethodType getMethodType(MethodNode method, boolean is_static) {
            var parameterTypes = getParameterTypes(method.getParameters(), is_static);
            var returnType = getReturnType(method.getType());
            return new MethodType(parameterTypes, returnType);
        }

        /**
         * Only called during construction. Returns an empty array if the given
         * type is void.
         */
        private Type[] getReturnType(DataType type) {
            return switch (type.getType()) {
                case Void -> new Type[] {};
                default   -> new Type[] { initDataType(type) };
            };
        }

        /**
         * Only called during construction. Adds an artificial first parameter
         * for `this` if `is_static is false.
         */
        private Type[] getParameterTypes(List<MethodNodeParameter> parameters, boolean is_static) {
            var offset = is_static ? 0 : 1;
            var parameterTypes = new Type[parameters.size() + offset];

            if (!is_static) {
                parameterTypes[0] = new PointerType(classType);
            }

            for (int i = 0; i < parameters.size(); i++) {
                parameterTypes[i + offset] = initDataType(parameters.get(i).getType());
            }

            return parameterTypes;
        }
    }
}
