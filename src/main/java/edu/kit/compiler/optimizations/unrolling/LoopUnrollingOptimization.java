package edu.kit.compiler.optimizations.unrolling;

import static firm.bindings.binding_irgraph.ir_graph_properties_t.IR_GRAPH_PROPERTIES_NONE;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import edu.kit.compiler.optimizations.Optimization;
import edu.kit.compiler.optimizations.OptimizationState;
import edu.kit.compiler.optimizations.Util;
import edu.kit.compiler.optimizations.unrolling.LoopAnalysis.Loop;
import edu.kit.compiler.optimizations.unrolling.LoopAnalysis.LoopTree;
import edu.kit.compiler.optimizations.unrolling.LoopVariableAnalysis.FixedIterationLoop;
import edu.kit.compiler.transform.JFirmSingleton;
import firm.Graph;
import firm.Mode;
import firm.Relation;
import firm.TargetValue;
import firm.bindings.binding_irgopt;
import firm.nodes.Block;
import firm.nodes.Node;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

public class LoopUnrollingOptimization implements Optimization.Local {

    static {
        JFirmSingleton.initializeFirmLinux();
    }

    private static final TargetValue INT_MAX = new TargetValue(Integer.MAX_VALUE, Mode.getIs());

    /**
     * Loops with more nodes will never be unrolled.
     */
    private static final int LOOP_SIZE_LIMIT = 64;

    /**
     * Maximum number of unrolled iterations per optimization run.
     */
    private static final int MAX_UNROLL = 8;

    /**
     * Maximum number of optimization runs with loops unrolled on the same graph.
     */
    private static final int MAX_PASSES = 4;

    private final Map<Graph, Integer> graphPasses = new HashMap<>();

    @Override
    public boolean optimize(Graph graph, OptimizationState state) {
        var passes = graphPasses.get(graph);
        if (passes != null && passes >= MAX_PASSES) {
            return false;
        }

        var result = LoopAnalysis.apply(graph)
                .getForestOfLoops().stream()
                .map(this::optimize)
                .reduce(Result.UNCHANGED, Result::merge);

        graph.confirmProperties(IR_GRAPH_PROPERTIES_NONE);
        binding_irgopt.remove_bads(graph.ptr);
        binding_irgopt.remove_unreachable_code(graph.ptr);
        binding_irgopt.remove_bads(graph.ptr);

        if (result.changed()) {
            graphPasses.merge(graph, 1, (n, m) -> n + m);
        }

        return result.changed();
    }

    private Result optimize(LoopTree tree) {
        var result = tree.getChildren().stream().map(this::optimize)
                .reduce(Result.FULL, Result::merge);

        if (result == Result.FULL) {
            // only try to unroll a loop if all nested loops are fully unrolled
            var loop = tree.getLoop();
            loop.updateBody();
            return LoopVariableAnalysis.apply(loop)
                    .flatMap(FixedIterationLoop::getIterationCount)
                    .filter(n -> Relation.LessEqual.contains(n.compare(INT_MAX)))
                    .map(n -> tryUnroll(loop, n.asInt()))
                    .orElse(Result.UNCHANGED);
        } else {
            return result;
        }
    }

    private static final Result tryUnroll(Loop loop, int iterations) {
        var result = Result.UNCHANGED;
        Optional<UnrollFactor> factor;

        if (iterations == 0) {
            var nodesPerBlock = Util.getNodesPerBlock(loop.getGraph());
            LoopUnroller.skipLoop(loop, nodesPerBlock);
            return Result.FULL;
        }

        do {
            var nodesPerBlock = Util.getNodesPerBlock(loop.getGraph());
            factor = UnrollFactor.of(loop, iterations, nodesPerBlock);

            if (factor.isPresent()) {
                var factor_ = factor.get();
                if (!LoopUnroller.unroll(loop, factor_.getFactor(),
                        factor_.isFull(), nodesPerBlock)) {
                    return result;
                }
                result = factor_.isFull() ? Result.FULL : Result.PARTIAL;

                assert iterations % factor_.getFactor() == 0;
                iterations /= factor_.getFactor();
            }
        } while (factor.isPresent() && !factor.get().isFull());

        return result;
    }

    private enum Result {

        UNCHANGED, PARTIAL, FULL;

        public Result merge(Result other) {
            return this == other ? this : PARTIAL;
        }

        public boolean changed() {
            return PARTIAL.compareTo(this) <= 0;
        }
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    @ToString
    private static final class UnrollFactor {

        @Getter
        private final int factor;
        @Getter
        private final boolean isFull;

        public static Optional<UnrollFactor> of(Loop loop, int iterations,
                Map<Block, List<Node>> nodesPerBlock) {

            var loopSize = nodesPerBlock.get(loop.getHeader()).size()
                    + loop.getBody().stream().collect(Collectors
                            .summingInt(b -> nodesPerBlock.get(b).size()));

            if (loopSize > LOOP_SIZE_LIMIT) {
                return Optional.empty();
            }

            return UnrollFactor.of(loop, iterations);
        }

        private static Optional<UnrollFactor> of(Loop loop, int iterations) {
            if (iterations <= MAX_UNROLL) {
                return Optional.of(new UnrollFactor(iterations, true));
            } else {
                for (int i = MAX_UNROLL; i >= 2; --i) {
                    if (iterations % i == 0) {
                        return Optional.of(new UnrollFactor(i, false));
                    }
                }
                return Optional.empty();
            }
        }
    }
}
