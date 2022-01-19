package edu.kit.compiler.optimizations.attributes;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a set of restrictions to the behavior of a function.
 */
@AllArgsConstructor
@NoArgsConstructor
@Data
public final class Attributes {

    public static final Attributes MINIMUM = new Attributes();

    private Purity purity = Purity.IMPURE;

    /**
     * A functions that is guaranteed to return, i.e. not end in an endless
     * loop. For our purposes this also precludes any Div or Mod nodes.
     */
    private boolean terminates = false;

    /**
     * Function returns newly allocated memory. This is currently just set
     * to false.
     */
    private boolean malloc = false;

    /**
     * Returns true if the function is pure or const.
     */
    public boolean isPure() {
        return purity.isPure();
    }

    /**
     * Returns true if the function is const.
     */
    public boolean isConst() {
        return purity.isConst();
    }

    /**
     * Represents the purity of a function. Each entry of this enum imposes
     * more restrictions on a function.
     */
    public static enum Purity {

        /**
         * Any function is impure by default.
         */
        IMPURE,

        /**
         * A function is pure if it does not affect the observable state of the
         * program. For our purposes, this means no stores or calls to impure
         * functions.
         * 
         * Calls to pure functions can be removed without changing the semantics of
         * the program.
         */
        PURE,

        /**
         * A function is const if it's pure and its return value is not affected
         * by the state of the program. A const function's return value
         * therefore depends solely on its arguments.
         */
        CONST;

        public boolean isPure() {
            return PURE.compareTo(this) <= 0;
        }

        public boolean isConst() {
            return CONST == this;
        }

        public Purity min(Purity other) {
            return this.compareTo(other) <= 0 ? this : other;
        }
    }
}
