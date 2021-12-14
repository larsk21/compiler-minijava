package edu.kit.compiler.codegen.pattern;

import edu.kit.compiler.codegen.ExitCondition;

public interface ConditionMatch extends Match {

    public abstract ExitCondition getCondition();
    
    public static ConditionMatch none() {
        return new None();
    }

    public static final class None extends Match.None implements ConditionMatch {
        @Override
        public ExitCondition getCondition() {
            throw new UnsupportedOperationException();
        }
    }

    public static abstract class Some extends Match.Some implements ConditionMatch {

    }
}
