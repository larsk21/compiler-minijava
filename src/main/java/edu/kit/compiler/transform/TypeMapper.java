package edu.kit.compiler.transform;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.kit.compiler.data.AstVisitor;
import edu.kit.compiler.data.DataType;
import edu.kit.compiler.data.DataType.DataTypeClass;
import edu.kit.compiler.data.ast_nodes.ClassNode;
import edu.kit.compiler.data.ast_nodes.MethodNode;
import edu.kit.compiler.data.ast_nodes.MethodNode.DynamicMethodNode;
import edu.kit.compiler.data.ast_nodes.MethodNode.MethodNodeParameter;
import edu.kit.compiler.data.ast_nodes.MethodNode.StaticMethodNode;
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
 * `ClassEntry`.
 */
public final class TypeMapper {

    private final Map<Integer, ClassEntry> classes = new HashMap<>();

    private final StringTable stringTable;
    private final TypeFactory typeFactory;

    /**
     * Set up the type system for a MiniJava program. Registers a `ClassEntry`
     * for each class in the namespace mapper.
     * 
     * @param namespaceMapper the class namespaces of the program.
     * @param stringTable the `stringTable` used for the program.
     */
    public TypeMapper(NamespaceMapper namespaceMapper, StringTable stringTable) {
        this.stringTable = stringTable;
        this.typeFactory = new TypeFactory();

        for (var namespace : namespaceMapper.getNamespaceMap().values()) {
            var entry = new ClassEntry(namespace);
            this.classes.put(namespace.getClassNodeRef().getName(), entry);
        }
    }

    /**
     * Returns the corresponding Firm type for the given data type. 
     * 
     * @param type
     * @return
     */
    public Type getDataType(DataType type) {
        return typeFactory.getDataType(type);
    }

    public ClassEntry getClassEntry(int classId) {
        return classes.get(classId);
    }

    /**
     * Represents all types and entities related to a single class in a MiniJava
     * program. This includes the type of the class itself, as well as an entity
     * for each field and method of the class. Types of fields and methods can
     * be accessed via the corresponding entities.
     */
    public final class ClassEntry {
        @Getter
        private final ClassType type;
        private final Map<Integer, Entity> fields = new HashMap<>();
        private final Map<Integer, Entity> methods = new HashMap<>();

        private ClassEntry(ClassNamespace namespace) {
            this.type = typeFactory.getClassType(namespace.getClassNodeRef());

            // Create and register entity for each field of the class
            constructFields(namespace);

            // Create and register entity for each dynamic method of the class
            constructMethods(namespace.getClassNodeRef(), namespace.getDynamicMethods());
            constructMethods(namespace.getClassNodeRef(), namespace.getStaticMethods());

            // ? maybe do later (after possible optimizations)
            type.layoutFields();
            type.finishLayout();
        }

        /**
         * Returns the Firm entity representing the given field.
         * @param fieldId
         * @return
         */
        public Entity getField(int fieldId) {
            return fields.get(fieldId);
        }

        /**
         * Returns the Firm entity representing the given method.
         * @param methodId
         * @return
         */
        public Entity getMethod(int methodId) {
            return methods.get(methodId);
        }

        private void constructFields(ClassNamespace namespace) {
            for (var fieldNode : namespace.getClassSymbols().values()) {
                var fieldName = stringTable.retrieve(fieldNode.getName());
                var fieldType = typeFactory.getDataType(fieldNode.getType());

                var fieldEntity = new Entity(type, fieldName, fieldType);
                fieldEntity.setVisibility(ir_visibility.ir_visibility_local);
                fields.put(fieldNode.getName(), fieldEntity);
            }
        }

        private <T extends MethodNode> void constructMethods(
            ClassNode classNode, Map<Integer, T> methodNodes
        ) {
            for (var methodNode : methodNodes.values()) {
                var methodName = stringTable.retrieve(methodNode.getName());
                var methodType = typeFactory.getMethodType(classNode, methodNode);

                var methodEntity = new Entity(type, methodName, methodType);
                methodEntity.setVisibility(ir_visibility.ir_visibility_local);
                methods.put(methodNode.getName(), methodEntity);
            }

        }
    }
    
    private final class TypeFactory {
        private final Map<Integer, ClassType> userTypes = new HashMap<>();

        private final Type booleanType = new PrimitiveType(Mode.getBu());
        private final Type integerType = new PrimitiveType(Mode.getIs());

        private ClassType getClassType(ClassNode class_) {
            return getUserType(new DataType(class_.getName()));
        }

        private MethodType getMethodType(ClassNode classNode, MethodNode method) {
            var parameterTypes = method.accept(new AstVisitor<Type[]>() {
                @Override
                public Type[] visit(DynamicMethodNode method_) {
                    return getParameterTypes(classNode, method.getParameters());
                }

                @Override
                public Type[] visit(StaticMethodNode method_) {
                    return getParameterTypes(method_.getParameters());
                }
            });
            var returnType = getReturnType(method.getType());
            return new MethodType(parameterTypes, returnType);
        }


        private Type getDataType(DataType type) {
            return switch (type.getType()) {
                case Boolean -> booleanType;
                case Int -> integerType;
                case Array -> {
                    var innerType = getDataType(type.getInnerType().get());
                    yield new PointerType(innerType);
                }
                case UserDefined -> new PointerType(getUserType(type)); 
                case Void, Any -> throw new IllegalArgumentException(
                    String.format("can't construct type for %s", type.getType())
                );
                default -> throw new IllegalStateException("unsupported data type");
            };
        }

        private ClassType getUserType(DataType type) {
            assert type.getType() == DataTypeClass.UserDefined;

            var classId = type.getIdentifier().get();
            var classType = userTypes.get(classId);
            if (classType == null) {
                var className = stringTable.retrieve(classId);
                classType = new ClassType(className);
                userTypes.put(classId, classType);
            }

            return classType;
        }

        private Type[] getReturnType(DataType type) {
            return switch (type.getType()) {
                case Void -> new Type[] {};
                default   -> new Type[] { getDataType(type) };
            };
        }

        private Type[] getParameterTypes(ClassNode class_, List<MethodNodeParameter> parameters) {
            var parameterTypes = new Type[parameters.size() + 1];
            parameterTypes[0] = new PointerType(getClassType(class_));
            for (int i = 0; i < parameters.size(); i++) {
                parameterTypes[i+1] = getDataType(parameters.get(i).getType());
            }

            return parameterTypes;
        }

        private Type[] getParameterTypes(List<MethodNodeParameter> parameters) {
            var parameterTypes = new Type[parameters.size()];
            for (int i = 0; i < parameters.size(); i++) {
                parameterTypes[i] = getDataType(parameters.get(i).getType());
            }

            return parameterTypes;
        }
    }
}
