package edu.kit.compiler.transform;

import java.util.HashMap;
import java.util.Map;

import edu.kit.compiler.data.AstVisitor;
import edu.kit.compiler.data.ast_nodes.MethodNode;
import edu.kit.compiler.data.ast_nodes.MethodNode.DynamicMethodNode;
import edu.kit.compiler.data.ast_nodes.MethodNode.StaticMethodNode;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ParameterCounter {

    /**
     * Returns a map of parameter names to their index. For static methods, the
     * parameters are numbered starting at 0. For dynamic methods they are
     * numbered starting at 1. The implicit `this` parameter is not included
     * in the mapping.
     * 
     * @param method the method for which to compute the mapping
     * @return a map from parameter names to indices
     */
    public static Map<Integer, Integer> apply(MethodNode method) {
        var visitor = new Visitor();
        method.accept(visitor);
        return visitor.parameters;
    }

    private static final class Visitor implements AstVisitor<Void> {
        private final Map<Integer, Integer> parameters = new HashMap<>();

        @Override
        public Void visit(StaticMethodNode method) {
            setParameterIndices(method, 0);
            return (Void)null;
        }

        @Override
        public Void visit(DynamicMethodNode method) {
            setParameterIndices(method, 1);
            return (Void)null;
        }

        private void setParameterIndices(MethodNode method, int offset) {
            int i = offset;
            for (var parameter : method.getParameters()) {
                parameters.put(parameter.getName(), i++);
            }
        }
    }
}
