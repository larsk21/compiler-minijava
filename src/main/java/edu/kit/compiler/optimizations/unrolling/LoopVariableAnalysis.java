package edu.kit.compiler.optimizations.unrolling;

import java.util.Optional;

import edu.kit.compiler.optimizations.unrolling.LoopAnalysis.Loop;
import firm.Relation;
import firm.TargetValue;
import firm.bindings.binding_irnode.ir_opcode;
import firm.nodes.Cmp;
import firm.nodes.Const;
import firm.nodes.Node;
import firm.nodes.Phi;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Implements an analysis that tries to detect if a loop has a fixed number of
 * iterations, and compute that number if possible. Currently, the loop must be
 * controlled by a Cmp with a Phi and a Const node as predecessor.
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class LoopVariableAnalysis {

    private static final TargetValue UNKNOWN = TargetValue.getUnknown();
    private static final TargetValue BAD = TargetValue.getBad();

    public static Optional<FixedIterationLoop> apply(Loop loop) {
        if (!loop.isValid()) {
            return Optional.empty();
        }

        var cond = loop.getCond();
        if (cond.getSelector().getOpCode() != ir_opcode.iro_Cmp) {
            return Optional.empty();
        }

        var cmp = (Cmp) cond.getSelector();
        if (cmp.getRight().getOpCode() != ir_opcode.iro_Const
                || cmp.getLeft().getOpCode() != ir_opcode.iro_Phi) {
            return Optional.empty();
        }

        var bound = ((Const) cmp.getRight()).getTarval();
        var loopVariable = (Phi) cmp.getLeft();

        var initial = TargetValue.getUnknown();
        var step = TargetValue.getUnknown();
        for (int i = 0; i < loopVariable.getPredCount(); ++i) {
            if (loop.isBackEdge(i)) {
                var value = computeStepValue(loopVariable, i);
                step = supremum(step, value);
            } else {
                var value = getConstValue(loopVariable.getPred(i));
                initial = supremum(initial, value);
            }
        }

        if (initial.isConstant() && step.isConstant()) {
            return Optional.of(new FixedIterationLoop(initial,
                    bound, step, cmp.getRelation()));
        } else {
            return Optional.empty();
        }
    }

    /**
     * Represents a descriptor for loops with a fixed number of iterations.
     */
    @RequiredArgsConstructor
    public static final class FixedIterationLoop {

        @Getter
        private final TargetValue initial;
        @Getter
        private final TargetValue bound;
        @Getter
        private final TargetValue step;
        @Getter
        private final Relation relation;

        /**
         * Returns the number of iterations of the loop. If the loop does not
         * terminate, returns an empty Optional. This may for example be the
         * case if the step is zero.
         * 
         * Note: This method does not consider overflows. Something like
         * `int i = 1; while (x != 0) i = i + 1;` will return an empty
         * Optional.
         */
        public Optional<Long> getIterationCount() {
            var mode = initial.getMode();

            if (!relation.contains(initial.compare(bound))) {
                return Optional.of((long) 0);
            } else if (step.isNull()) {
                return Optional.empty();
            } else {
                Optional<Long> steps = switch (relation) {
                    case Equal -> Optional.of((long) 1);
                    case LessGreater -> {
                        var difference = bound.asLong() - initial.asLong();
                        if (difference % step.asLong() == 0
                                && difference / step.asLong() >= 0) {
                            yield Optional.of(difference / step.asLong());
                        } else {
                            yield Optional.empty();
                        }
                    }

                    // convert loop to a less-than-loop and compute iterations
                    case Less -> getIterations(initial, bound, step);
                    case LessEqual -> getIterations(initial, bound.add(mode.getOne()), step);
                    case Greater -> getIterations(bound, initial, step.neg());
                    case GreaterEqual -> getIterations(bound.sub(mode.getOne()), initial, step.neg());

                    case True, LessEqualGreater -> Optional.empty();
                    case False -> throw new IllegalStateException();
                    default -> Optional.empty();
                };

                steps.ifPresent(this::assertCorrectNumSteps);

                return steps;
            }
        }

        /**
         * Returns the number of iterations of a less-than-loop with the given
         * parameters. It is assumed that the loop has at least one iteration.
         * If the loop does not terminate, an empty Optional is returned.
         */
        private static Optional<Long> getIterations(TargetValue lowerInclusive,
                TargetValue upperExclusive, TargetValue increment) {
            
            var lowerLong = lowerInclusive.asLong();
            var upperLong = upperExclusive.asLong();
            var incLong = increment.asLong();

            if (lowerLong >= upperLong) {
                return Optional.empty();
            } else if (incLong == 0 || incLong < 0) {
                return Optional.empty();
            } else {
                var difference = upperLong - lowerLong + incLong - 1;

                return Optional.of(Long.divideUnsigned(difference, incLong));
            }
        }

        private void assertCorrectNumSteps(long steps) {
            var offset = step.asLong() * steps;
            var result = new TargetValue(initial.asLong() + offset, initial.getMode());

            assert steps >= 0;
            assert relation.contains(initial.compare(bound)) || steps == 0;
            assert !relation.contains(result.compare(bound));
            assert steps == 0 || relation.contains(result.sub(step).compare(bound));
        }
    }

    /**
     * Recursively analyzes every path through a loop starting at the i-th
     * predecessor of the loop variable. Returns a constant Tarval if the
     * node is equal to the previous value of the loop variable plus a constant
     * step (beware that the step may be zero).
     */
    private static TargetValue computeStepValue(Phi loopVariable, int i) {
        loopVariable.getGraph().incVisited();
        var zero = loopVariable.getMode().getNull();
        return analyzeStepValue(loopVariable, zero, loopVariable.getPred(i));
    }

    private static TargetValue analyzeStepValue(Phi loopVariable,
            TargetValue step, Node node) {
        if (step.equals(BAD)) {
            return BAD;
        }

        return switch (node.getOpCode()) {
            case iro_Add -> {
                var newStep = step.add(getConstValue(node.getPred(1)));
                yield analyzeStepValue(loopVariable, newStep, node.getPred(0));
            }
            case iro_Phi -> {
                if (node.equals(loopVariable)) {
                    yield step;
                } else {
                    if (node.visited()) {
                        yield BAD;
                    } else {
                        node.markVisited();
                        var newStep = UNKNOWN;
                        for (int i = 0; i < node.getPredCount() && !newStep.equals(BAD); ++i) {
                            newStep = supremum(newStep, analyzeStepValue(
                                    loopVariable, step, node.getPred(i)));
                        }
                        yield newStep;
                    }
                }
            }
            default -> BAD;
        };
    }

    /**
     * If the node is Const, returns its value. Otherwise returns BAD.
     */
    private static TargetValue getConstValue(Node node) {
        return switch (node.getOpCode()) {
            case iro_Const -> ((Const) node).getTarval();
            default -> BAD;
        };
    }

    /**
     * Returns the supremum of the two Tarvals.
     */
    private static TargetValue supremum(TargetValue lhs, TargetValue rhs) {
        if (lhs.equals(BAD) || rhs.equals(BAD)) {
            return BAD;
        } else if (lhs.equals(UNKNOWN)) {
            return rhs;
        } else if (rhs.equals(UNKNOWN)) {
            return lhs;
        } else if (lhs.equals(rhs)) {
            return lhs;
        } else {
            return BAD;
        }
    }
}
