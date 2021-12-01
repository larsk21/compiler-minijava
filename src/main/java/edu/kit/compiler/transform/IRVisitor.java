package edu.kit.compiler.transform;

import edu.kit.compiler.data.AstVisitor;
import edu.kit.compiler.data.DataType;
import edu.kit.compiler.data.ast_nodes.ClassNode;
import edu.kit.compiler.data.ast_nodes.MethodNode;
import edu.kit.compiler.data.ast_nodes.ProgramNode;
import firm.Construction;
import firm.nodes.Node;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

public class IRVisitor implements AstVisitor<Void> {
    private final TypeMapper typeMapper;
    @Getter
    private static final Map<Integer, TransformContext> methodContexts = new HashMap<>();

    /**
     * Transform visitor visits the AST recursively and calls our underlying IR visitors that create firm components for the AST
     *
     * @param typeMapper Mapper that maps from our data types to firm Entities and Types.
     */
    public IRVisitor(TypeMapper typeMapper) {
        this.typeMapper = typeMapper;
    }

    @Override
    public Void visit(ProgramNode program) {
        for (var classNode : program.getClasses()) {
            classNode.accept(this);
        }
        return (Void) null;
    }

    @Override
    public Void visit(ClassNode classNode) {
        // search for methods in our ast
        for (var method : classNode.getDynamicMethods()) {
            // create transform context for this method
            Map<Integer, Integer> variableMapping = LocalVariableCounter.apply(method);
            TransformContext transformContext = new TransformContext(typeMapper, classNode, method, variableMapping, false);

            visitMethod(method, transformContext);
        }

        for (var method : classNode.getStaticMethods()) {
            // create transform context for this method
            Map<Integer, Integer> variableMapping = LocalVariableCounter.apply(method);
            TransformContext transformContext = new TransformContext(typeMapper, classNode, method, variableMapping, true);

            methodContexts.put(method.getName(), transformContext);
            visitMethod(method, transformContext);
        }
        return (Void) null;
    }

    private void visitMethod(MethodNode methodNode, TransformContext transformContext) {
        Construction con = transformContext.getConstruction();

        // visit methods with new statement context
        IRStatementVisitor statementVisitor = new IRStatementVisitor(transformContext);
        boolean returns = methodNode.getStatementBlock().accept(statementVisitor);

        // add implicit return for void methods
        if (!returns && transformContext.getMethodNode().getType().equals(DataType.voidType())) {
            Node returnNode = con.newReturn(con.getCurrentMem(), new Node[]{});
            transformContext.getEndBlock().addPred(returnNode);
        }

        con.finish();
    }
}
