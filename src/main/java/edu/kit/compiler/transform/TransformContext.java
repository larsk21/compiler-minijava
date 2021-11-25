package edu.kit.compiler.transform;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Optional;

import edu.kit.compiler.data.DataType;
import edu.kit.compiler.data.ast_nodes.ClassNode;
import edu.kit.compiler.data.ast_nodes.MethodNode;
import edu.kit.compiler.data.ast_nodes.MethodNode.MethodNodeParameter;
import edu.kit.compiler.transform.TypeMapper.ClassEntry;
import firm.Construction;
import firm.Entity;
import firm.Graph;
import firm.Mode;
import firm.Type;
import firm.nodes.Block;
import firm.nodes.Node;
import lombok.AllArgsConstructor;
import lombok.Getter;

/*
* Contains the necessary Context for the transformation step on a specific method.
*
* Precondition: Local variables of the method are counted and mapped.
*/
@AllArgsConstructor
public class TransformContext {

    /**
     * Mapping of local variable names to indizes.
     */
    private HashMap<Integer, Integer> variableMapping;

    /**
     * Mapping of method parameter names to indizes.
     */
    private HashMap<Integer, Integer> paramMapping;

    /**
     * Firm types of the method parameters.
     */
    private Type[] paramTypes;

    /**
     * Node that projects the method parameters.
     */
    private Node projArgs;

    @Getter
    private TypeMapper typeMapper;
    @Getter
    private ClassNode classNode;
    @Getter
    private MethodNode methodNode;
    @Getter
    private boolean isStatic;

    /**
     * Return type of the method or Optional.empty() for void methods.
     */
    @Getter
    private Optional<Type> returnType;

    /**
     * Current construction object.
     */
    @Getter
    private Construction construction;

    public TransformContext(TypeMapper typeMapper, ClassNode classNode, MethodNode methodNode, Entity methodEntity,
                            HashMap<Integer, Integer> variableMapping, int n_vars, boolean isStatic) {
        this.typeMapper = typeMapper;
        this.classNode = classNode;
        this.methodNode = methodNode;
        this.variableMapping = variableMapping;
        this.isStatic = isStatic;
        this.paramMapping = new HashMap<>();
        ArrayList<Type> params = new ArrayList<>();
        for (int i = 0; i < methodNode.getParameters().size(); i++) {
            MethodNodeParameter param = methodNode.getParameters().get(i);
            params.add(typeMapper.getDataType(param.getType()));
            this.paramMapping.put(param.getName(), i);
        }
        this.paramTypes = params.toArray(new Type[]{});
        this.returnType = Optional.empty();
        if (!methodNode.getType().equals(DataType.voidType())) {
            this.returnType = Optional.of(typeMapper.getDataType(methodNode.getType()));
        }
        Graph graph = new Graph(methodEntity, n_vars);
        this.construction = new Construction(graph);
        this.projArgs = construction.newProj(graph.getStart(), Mode.getT(), 2);
    }

    public Type getParamType(int name) {
        return paramTypes[paramMapping.get(name)];
    }

    /**
     * Firm index of a local variable.
     * 
     * @param name variable name
     * @return the index
     */
    public int getVariableIndex(int name) {
        return variableMapping.get(name);
    }

    public Block getEndBlock() {
        return construction.getGraph().getEndBlock();
    }

    /**
     * Creates a node for accessing a method parameter.
     * 
     * @param name parameter name
     * @return the node
     */
    public Node createParamNode(int name) {
        int param = paramMapping.get(name);
        return construction.newProj(projArgs, paramTypes[param].getMode(), param + (isStatic ? 0 : 1));
    }

    /**
     * Creates a node for accessing 'this'.
     * 
     * @return the node
     */
    public Node createThisNode() {
        if (isStatic) {
            throw new IllegalStateException("'this' is not available in static methods");
        }
        Mode mode = typeMapper.getClassPointerType(classNode.getName()).getMode();
        return construction.newProj(projArgs, mode, 0);
    }
}
