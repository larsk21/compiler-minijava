package edu.kit.compiler.data;

import java.util.Optional;

import lombok.Getter;

/**
 * Represents the possible data types in MiniJava. This includes predefined,
 * constructed and user-defined types.
 */
public class DataType {

    /**
     * Create a predefined data type.
     */
    public DataType(DataTypeClass type) {
        this.type = type;

        this.innerType = Optional.empty();
        this.identifier = Optional.empty();
    }

    /**
     * Create a constructed data type.
     */
    public DataType(DataTypeClass type, DataType innerType) {
        this.type = type;

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
     * Class of this data type.
     */
    @Getter
    private DataTypeClass type;

    /**
     * Inner type of this (constructed) data type.
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
        UserDefined
    }

}
