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

        var boundValue = ((Const) cmp.getRight()).getTarval();
        var loopVariable = (Phi) cmp.getLeft();

        var initialValue = TargetValue.getUnknown();
        var stepValue = TargetValue.getUnknown();
        for (int i = 0; i < loopVariable.getPredCount(); ++i) {
            var pred = loopVariable.getPred(i);
            if (loop.isBackEdge(i)) {
                stepValue = updateStepValue(loopVariable, stepValue, pred);
            } else {
                initialValue = updateInitialValue(initialValue, pred);
            }
        }

        if (initialValue.isConstant() && stepValue.isConstant()) {
            return Optional.of(new FixedIterationLoop(initialValue,
                    boundValue, stepValue, cmp.getRelation()));
        } else {
            return Optional.empty();
        }
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class FixedIterationLoop {

        @Getter
        private final TargetValue initial;
        @Getter
        private final TargetValue bound;
        @Getter
        private final TargetValue step;
        @Getter
        private final Relation relation;

        public Optional<TargetValue> getIterationCount() {
            var mode = initial.getMode();

            if (!relation.contains(initial.compare(bound))) {
                return Optional.of(mode.getNull());
            } else if (step.isNull()) {
                return Optional.empty();
            } else {
                Optional<TargetValue> steps = switch (relation) {
                    case Equal -> Optional.of(mode.getOne());
                    case LessGreater -> {
                        var difference = bound.sub(initial);
                        if (difference.mod(step).isNull()) {
                            yield Optional.of(difference.div(step));
                        } else {
                            yield Optional.empty();
                        }
                    }
                    case Less, Greater -> getNumSteps(initial, bound, step);
                    case LessEqual ->  getNumSteps(initial,
                            bound.add(mode.getOne()), step);
                    case GreaterEqual -> getNumSteps(initial,
                            bound.sub(mode.getOne()), step);
                    
                    case True, LessEqualGreater -> Optional.empty();
                    case False -> throw new IllegalStateException();
                    default -> Optional.empty();
                };

                steps.ifPresent(this::assertCorrectNumSteps);

                return steps;
            }
        }

        private void assertCorrectNumSteps(TargetValue steps) {
            assert relation.contains(initial.compare(bound)) || steps.isNull();
            assert !relation.contains(initial.add(step.mul(steps)).compare(bound));
            assert steps.isNull() || relation.contains(initial.add(step.mul(
                            steps.sub(steps.getMode().getOne()))).compare(bound));
        }

        private static final Optional<TargetValue> getNumSteps(
                TargetValue begin, TargetValue end, TargetValue step) {
            assert begin.getMode().equals(end.getMode());
            assert begin.getMode().equals(step.getMode());

            var mode = begin.getMode();

            if (step.isNegative()) {
                if (step.equals(mode.getMin())) {
                    // ugly special case because INT_MIN "breaks" neg()
                    return Optional.empty();
                } else {
                    return getNumSteps(begin.neg(), end.neg(), step.neg());
                }
            } else if (step.isNull()) {
                return Optional.empty();
            } else {
                if (begin.equals(mode.getMin()) || end.equals(mode.getMin())) {
                    return Optional.empty();
                } else if (Relation.LessEqual.contains(begin.compare(end))) {
                    var offset = step.sub(mode.getOne());
                    var difference = end.sub(begin).add(offset);
                    var steps = difference.div(step);

                    return Optional.of(steps);
                } else {
                    return Optional.empty();
                }
            }
        }

    }

    private static TargetValue updateInitialValue(
            TargetValue initialValue, Node initializer) {
        if (initialValue.equals(BAD)) {
            return BAD;
        }

        if (initializer.getOpCode() == ir_opcode.iro_Const) {
            var value = ((Const) initializer).getTarval();
            if (initialValue.equals(UNKNOWN)) {
                return value;
            } else if (initialValue.equals(value)) {
                // multiple identical initializers for index are fine
                return initialValue;
            } else {
                // multiple non-identical initializers for index
                return BAD;
            }
        } else {
            // non Const initializer for index
            return BAD;
        }
    }

    private static TargetValue updateStepValue(Phi loopVariable,
            TargetValue stepValue, Node node) {
        if (stepValue.equals(BAD)) {
            return BAD;
        }

        var zero = loopVariable.getMode().getNull();
        var newStepValue = analyzeStepValue(loopVariable, zero, node);

        if (stepValue.equals(UNKNOWN)) {
            return newStepValue;
        } else if (newStepValue.equals(stepValue)) {
            return stepValue;
        } else {
            // every path must have same step value
            return BAD;
        }
    }

    private static TargetValue analyzeStepValue(Phi loopVariable,
            TargetValue stepValue, Node node) {
        return switch (node.getOpCode()) {
            case iro_Add -> {
                if (node.getPred(1).getOpCode() == ir_opcode.iro_Const) {
                    var offset = ((Const) node.getPred(1)).getTarval();
                    stepValue = stepValue.add(offset);

                    yield analyzeStepValue(loopVariable, stepValue, node.getPred(0));
                } else

                {
                    yield BAD;
                }
            }
            case iro_Phi -> {
                if (node.equals(loopVariable)) {
                    yield stepValue;
                } else {
                    // todo there is room for improvement here
                    yield BAD;
                }
            }
            default -> BAD;
        };
    }
}
