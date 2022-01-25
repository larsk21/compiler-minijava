package edu.kit.compiler.optimizations.analysis;

import firm.TargetValue;

import lombok.Getter;

/**
 * Represents a flat lattice of TargetValue elements.
 * 
 * The flat lattice contains three layers:
 * - conflicting (top)
 * - constant TargetValue
 * - unknown (bottom)
 */
public class TargetValueLatticeElement {

    private static final TargetValueLatticeElement UNKNOWN = new TargetValueLatticeElement(TargetValue.getUnknown());
    private static final TargetValueLatticeElement CONFLICTING = new TargetValueLatticeElement(TargetValue.getBad());

    /**
     * Get the bottom element `unknown`.
     */
    public static TargetValueLatticeElement unknown() {
        return UNKNOWN;
    }

    /**
     * Get the top element `conflicting`.
     */
    public static TargetValueLatticeElement conflicting() {
        return CONFLICTING;
    }

    /**
     * Get an element with a constant value.
     */
    public static TargetValueLatticeElement constant(TargetValue value) {
        if (!value.isConstant()) {
            throw new IllegalStateException("passed target value is not a constant");
        }

        return new TargetValueLatticeElement(value);
    }

    /**
     * Get an element with a constant boolean value.
     */
    public static TargetValueLatticeElement constant(boolean b) {
        return new TargetValueLatticeElement(b ? TargetValue.getBTrue() : TargetValue.getBFalse());
    }

    /**
     * Create a new TargetValueLatticeElement with the given TargetValue.
     * 
     * `TargetValue.getUnknown()` represents the bottom element `unknown`,
     * `TargetValue.getBad()` represents the top element `conflicting`.
     */
    private TargetValueLatticeElement(TargetValue value) {
        this.value = value;
    }

    /**
     * Get the TargetValue of a constant element.
     * 
     * If this element is not constant, the result is undefined.
     */
    @Getter
    private TargetValue value;

    /**
     * Get whether this element is the bottom element `unknown`.
     */
    public boolean isUnknown() {
        return this == UNKNOWN;
    }

    /**
     * Get whether this element is the top element `conflicting`.
     */
    public boolean isConflicting() {
        return this == CONFLICTING;
    }

    /**
     * Get whether this element is a constant element with a value.
     */
    public boolean isConstant() {
        return !isUnknown() && !isConflicting();
    }

    /**
     * Get whether this element is equal to another.
     */
    public boolean isEqualTo(TargetValueLatticeElement other) {
        if (this.isUnknown() && other.isUnknown()) {
            return true;
        } else if (this.isConflicting() && other.isConflicting()) {
            return true;
        } else if (this.isConstant() && other.isConstant()) {
            return this.value.equals(other.value);
        } else {
            return false;
        }
    }

    /**
     * Join this element with another element.
     * 
     * The join returns the lowest element of the lattice that is for both
     * elements above or equal to that element.
     */
    public TargetValueLatticeElement join(TargetValueLatticeElement other) {
        if (this.isEqualTo(other)) {
            return this;
        } else if (other.isUnknown()) {
            return this;
        } else if (this.isUnknown()) {
            return other;
        } else {
            return conflicting();
        }
    }

    @Override
    public String toString() {
        if (isUnknown()) {
            return "unknown";
        } else if (isConflicting()) {
            return "conflicting";
        } else {
            return "constant: " + value.toString();
        }
    }

}
