package edu.kit.compiler.data;

import lombok.Getter;

import java.util.Optional;

import edu.kit.compiler.lexer.StringTable;

/**
 * Represents the possible data types in MiniJava. This includes predefined,
 * constructed and user-defined types.
 */
public class DataType {
    private static final DataType voidType = new DataType(DataTypeClass.Void);

    /**
     * Create a predefined data type.
     */
    public DataType(DataTypeClass type) {
        this.type = type;

        this.innerType = Optional.empty();
        this.identifier = Optional.empty();
    }

    /**
     * Create a constructed (here: array) data type.
     */
    public DataType(DataType innerType) {
        this.type = DataTypeClass.Array;

        this.innerType = Optional.of(innerType);
        this.identifier = Optional.empty();
    }

    /**
     * Create a user-defined data type.
     * 
     * @param identifier Identifier of this user-defined data type.
     */
    public DataType(Integer identifier) {
        this.type = DataTypeClass.UserDefined;

        this.innerType = Optional.empty();
        this.identifier = Optional.of(identifier);
    }

    /**
     * Get the void data type
     * 
     * @return void data type
     */
    public static DataType voidType() {
        return voidType;
    }

    /**
     * Class of this data type.
     */
    @Getter
    private DataTypeClass type;

    /**
     * Inner type of this (constructed) data type. Inner type is used when declaring arrays.
     * For multi dimensional array declarations the innerType is another array.
     */
    @Getter
    private Optional<DataType> innerType;

    /**
     * Identifier of this (user-defined) data type.
     */
    @Getter
    private Optional<Integer> identifier;

    /**
     * Represents the classes of data types in MiniJava.
     */
    public static enum DataTypeClass {
        Void,
        Boolean,
        Int,
        Array,
        UserDefined,
        Any
    }

    /**
     * Returns whether this data type is compatible (can be asigned) to the
     * given other data type.
     * 
     * Rules for compatibility:
     * - Void is not compatible to any data type (not even itself)
     * - Any is compatible to itself and any reference type
     * - equal data type are compatible to each other (expect Void)
     */
    public boolean isCompatibleTo(DataType other) {
        if (type == DataTypeClass.Void || other.type == DataTypeClass.Void) {
            return false;
        } else if (type == DataTypeClass.Any && other.type == DataTypeClass.Any) {
            return true;
        } else if (type == DataTypeClass.Any && (other.type == DataTypeClass.UserDefined || other.type == DataTypeClass.Array)) {
            return true;
        } else if ((type == DataTypeClass.UserDefined || type == DataTypeClass.Array) && other.type == DataTypeClass.Any) {
            return true;
        } else {
            return equals(other);
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof DataType)) {
            return false;
        }
        DataType other = (DataType)obj;

        if (other.type != type) {
            return false;
        }
        switch (type) {
            case Array:
                return other.innerType.equals(innerType);
            case UserDefined:
                return other.identifier.equals(identifier);
            default:
                return true;
        }
    }

    /**
     * Get the string representation of this DataType.
     * 
     * @param stringTable StringTable containing the names of all involved
     * data types.
     */
    public String getRepresentation(StringTable stringTable) {
        switch (type) {
        case Array:
            return innerType.map(
                innerType -> innerType.getRepresentation(stringTable) + "[]"
            ).orElseThrow(
                () -> new IllegalStateException("array type without inner type")
            );
        case Boolean:
            return "boolean";
        case Int:
            return "int";
        case UserDefined:
            return identifier.map(
                identifier -> stringTable.retrieve(identifier)
            ).orElseThrow(
                () -> new IllegalStateException("user defined type without identifier")
            );
        case Void:
            return "void";
        case Any:
            return "<any>";
        default:
            throw new IllegalStateException("unsupported data type");
        }
    }

}
