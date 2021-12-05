package edu.kit.compiler.transform;

import java.util.Map;
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
     * Mapping of local variable names to indices.
     */
    private Map<Integer, Integer> variableMapping;

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

    @Getter
    private final Entity methodEntity;

    /**
     * Node for the this-pointer.
     */
    @Getter
    private Node thisNode;

    public TransformContext(TypeMapper typeMapper, ClassNode classNode, MethodNode methodNode,
                            Map<Integer, Integer> variableMapping, boolean isStatic) {
        ClassEntry classEntry = typeMapper.getClassEntry(classNode);
        this.typeMapper = typeMapper;
        this.classNode = classNode;
        this.methodNode = methodNode;
        this.methodEntity = classEntry.getMethod(methodNode).getEntity();
        this.variableMapping = variableMapping;
        this.isStatic = isStatic;
        this.returnType = Optional.empty();
        if (!methodNode.getType().equals(DataType.voidType())) {
            this.returnType = Optional.of(typeMapper.getDataType(methodNode.getType()));
        }

        // add parameters to variable mapping
        final int numLocalVars = variableMapping.size();
        int currentVar = numLocalVars;
        for (var param: methodNode.getParameters()) {
            this.variableMapping.put(param.getName(), currentVar);
            currentVar++;
        }

        // initialize graph
        Graph graph = new Graph(methodEntity, currentVar);
        this.construction = new Construction(graph);
        Node projArgs =  graph.getArgs();
        if (!isStatic) {
            this.thisNode = construction.newProj(projArgs, Mode.getP(), 0);
        }

        // initialize local variables for parameters
        for (int i = 0; i < methodNode.getParameters().size(); i++) {
            MethodNodeParameter param = methodNode.getParameters().get(i);
            int paramIndex = i + (isStatic ? 0 : 1);
            int variableIndex = numLocalVars + i;
            Mode paramMode = typeMapper.getMode(param.getType());
            Node paramProj = construction.newProj(projArgs, paramMode, paramIndex);
            construction.setVariable(variableIndex, paramProj);
        }
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

    public Graph getGraph() {
        return construction.getGraph();
    }
}
