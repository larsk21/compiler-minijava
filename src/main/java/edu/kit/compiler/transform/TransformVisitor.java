package edu.kit.compiler.transform;

import edu.kit.compiler.data.AstVisitor;
import edu.kit.compiler.data.ast_nodes.ClassNode;
import edu.kit.compiler.data.ast_nodes.ExpressionNode;
import edu.kit.compiler.data.ast_nodes.ProgramNode;
import edu.kit.compiler.data.ast_nodes.StatementNode.*;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

public class TransformVisitor implements AstVisitor<Void> {

    private final TypeMapper typeMapper;

    @Getter
    private final Map<Integer, TransformContext> methodContexts = new HashMap<>();

    /**
     * Transform visitor visits the AST recursively and calls our underlying IR visitors that create firm components for the AST
     *
     * @param typeMapper Mapper that maps from our data types to firm Entities and Types.
     */
    public TransformVisitor(
            TypeMapper typeMapper
    ) {
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

            methodContexts.put(method.getName(), transformContext);
            IRExpressionVisitor irExpressionVisitor = new IRExpressionVisitor(typeMapper, transformContext);
            for (var statement : method.getStatementBlock().getStatements()) {
                /*
                  continue visiting statements with the IR visitor for this method
                 */
                statement.accept(irExpressionVisitor);
            }
        }
        return (Void) null;
    }

}
