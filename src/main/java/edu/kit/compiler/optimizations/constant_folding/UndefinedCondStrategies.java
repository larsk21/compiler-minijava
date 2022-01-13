package edu.kit.compiler.optimizations.constant_folding;

import edu.kit.compiler.optimizations.ConstantOptimization.UndefinedCondStrategy;

import firm.TargetValue;
import firm.nodes.Cond;

/**
 * Collection of strategies for choosing a TargetValue for Cond nodes with an
 * unknown TargetValue.
 */
public class UndefinedCondStrategies {

    private UndefinedCondStrategies() {}

    /**
     * Naive strategy that always chooses BFalse.
     */
    public static class Naive implements UndefinedCondStrategy {

        @Override
        public TargetValue chooseCondValue(Cond cond) {
            return TargetValue.getBFalse();
        }

    }

}
