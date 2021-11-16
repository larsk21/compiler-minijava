package edu.kit.compiler.data;

import java.util.Optional;

import edu.kit.compiler.lexer.StringTable;
import lombok.EqualsAndHashCode;

/**
 * Represents a Token created by the lexer. A Token can have a value of type
 * Integer or String associated with it, depending on the type of the Token.
 */
@EqualsAndHashCode(callSuper = false)
public class Token implements Positionable {

    /**
     * Create a new Token without any value.
     * 
     * @param type Type of this Token.
     * @param line Line position in the file.
     * @param column Column position in the file.
     */
    public Token(TokenType type, int line, int column) {
        this.type = type;
        this.line = line;
        this.column = column;

        intValue = Optional.empty();
        literalValue = Optional.empty();
    }

    /**
     * Create a new Token with an associated integer value.
     * 
     * @param type Type of this Token.
     * @param line Line position in the file.
     * @param column Column position in the file.
     * @param intValue Integer value associated with this Token.
     */
    public Token(TokenType type, int line, int column, int intValue) {
        this(type, line, column);

        this.intValue = Optional.of(intValue);
    }

    /**
     * Create a new Token with an associated literal value.
     * 
     * @param type Type of this Token.
     * @param line Line position in the file.
     * @param column Column position in the file.
     * @param literal Literal value associated with this Token.
     */
    public Token(TokenType type, int line, int column, Literal literal) {
        this(type, line, column);

        this.literalValue = Optional.of(literal);
    }

    private TokenType type;
    private int line;
    private int column;

    private Optional<Integer> intValue;
    private Optional<Literal> literalValue;

    /**
     * Get the type of this Token.
     */
    public TokenType getType() {
        return type;
    }

    /**
     * Get the line position in the file.
     */
    public int getLine() {
        return line;
    }

    /**
     * Get the column position in the file.
     */
    public int getColumn() {
        return column;
    }

    /**
     * Get the optional integer value associated with this Token.
     */
    public Optional<Integer> getIntValue() {
        return intValue;
    }

    /**
     * Get the optional literal value associated with this Token.
     */
    public Optional<Literal> getLiteralValue() {
        return literalValue;
    }

    @Override
    public String toString() {
        return String.format("Token(%s, %d, %d)", type, line, column);
    }

    /**
     * Get the string representation of this Token. This includes associated
     * values.
     * 
     * @param stringTable String table from the lexer that parsed this Token to
     * resolve identifier names.
     */
    public String getStringRepresentation(StringTable stringTable) {
        switch (type) {
        case EndOfStream:
            return "EOF";
        case Identifier:
            return intValue
                .map(value -> "identifier " + stringTable.retrieve(intValue.get()))
                .orElseThrow(() -> new IllegalStateException("identifier token without associated id"));
        case IntegerLiteral:
            return literalValue
                .map((literal) -> "integer literal " + literal.toString())
                .orElseThrow(() -> new IllegalStateException("integer token without associated literal"));
        case Keyword_Abstract:
            return "abstract";
        case Keyword_Assert:
            return "assert";
        case Keyword_Boolean:
            return "boolean";
        case Keyword_Break:
            return "break";
        case Keyword_Byte:
            return "byte";
        case Keyword_Case:
            return "case";
        case Keyword_Catch:
            return "catch";
        case Keyword_Char:
            return "char";
        case Keyword_Class:
            return "class";
        case Keyword_Const:
            return "const";
        case Keyword_Continue:
            return "continue";
        case Keyword_Default:
            return "default";
        case Keyword_Do:
            return "do";
        case Keyword_Double:
            return "double";
        case Keyword_Else:
            return "else";
        case Keyword_Enum:
            return "enum";
        case Keyword_Extends:
            return "extends";
        case Keyword_False:
            return "false";
        case Keyword_Final:
            return "final";
        case Keyword_Finally:
            return "finally";
        case Keyword_Float:
            return "float";
        case Keyword_For:
            return "for";
        case Keyword_Goto:
            return "goto";
        case Keyword_If:
            return "if";
        case Keyword_Implements:
            return "implements";
        case Keyword_Import:
            return "import";
        case Keyword_Instanceof:
            return "instanceof";
        case Keyword_Int:
            return "int";
        case Keyword_Interface:
            return "interface";
        case Keyword_Long:
            return "long";
        case Keyword_Native:
            return "native";
        case Keyword_New:
            return "new";
        case Keyword_Null:
            return "null";
        case Keyword_Package:
            return "package";
        case Keyword_Private:
            return "private";
        case Keyword_Protected:
            return "protected";
        case Keyword_Public:
            return "public";
        case Keyword_Return:
            return "return";
        case Keyword_Short:
            return "short";
        case Keyword_Static:
            return "static";
        case Keyword_Strictfp:
            return "strictfp";
        case Keyword_String:
            return "string";
        case Keyword_Super:
            return "super";
        case Keyword_Switch:
            return "switch";
        case Keyword_Synchronized:
            return "synchronized";
        case Keyword_This:
            return "this";
        case Keyword_Throw:
            return "throw";
        case Keyword_Throws:
            return "throws";
        case Keyword_Transient:
            return "transient";
        case Keyword_True:
            return "true";
        case Keyword_Try:
            return "try";
        case Keyword_Void:
            return "void";
        case Keyword_Volatile:
            return "volatile";
        case Keyword_While:
            return "while";
        case Operator_And:
            return "&";
        case Operator_AndAnd:
            return "&&";
        case Operator_AndEqual:
            return "&=";
        case Operator_Bar:
            return "|";
        case Operator_BarBar:
            return "||";
        case Operator_BarEqual:
            return "|=";
        case Operator_BraceL:
            return "{";
        case Operator_BraceR:
            return "}";
        case Operator_BracketL:
            return "[";
        case Operator_BracketR:
            return "]";
        case Operator_Circum:
            return "^";
        case Operator_CircumEqual:
            return "^=";
        case Operator_Colon:
            return ":";
        case Operator_Comma:
            return ",";
        case Operator_Dot:
            return ".";
        case Operator_Equal:
            return "=";
        case Operator_EqualEqual:
            return "==";
        case Operator_Greater:
            return ">";
        case Operator_GreaterEqual:
            return ">=";
        case Operator_GreaterGreater:
            return ">>";
        case Operator_GreaterGreaterEqual:
            return ">>=";
        case Operator_GreaterGreaterGreater:
            return ">>>";
        case Operator_GreaterGreaterGreaterEqual:
            return ">>>=";
        case Operator_Minus:
            return "-";
        case Operator_MinusEqual:
            return "-=";
        case Operator_MinusMinus:
            return "--";
        case Operator_Not:
            return "!";
        case Operator_NotEqual:
            return "!=";
        case Operator_ParenL:
            return "(";
        case Operator_ParenR:
            return ")";
        case Operator_Percent:
            return "%";
        case Operator_PercentEqual:
            return "%=";
        case Operator_Plus:
            return "+";
        case Operator_PlusEqual:
            return "+=";
        case Operator_PlusPlus:
            return "++";
        case Operator_Questionmark:
            return "?";
        case Operator_Semicolon:
            return ";";
        case Operator_Slash:
            return "/";
        case Operator_SlashEqual:
            return "/=";
        case Operator_Smaller:
            return "<";
        case Operator_SmallerEqual:
            return "<=";
        case Operator_SmallerSmaller:
            return "<<";
        case Operator_SmallerSmallerEqual:
            return "<<=";
        case Operator_Star:
            return "*";
        case Operator_StarEqual:
            return "*=";
        case Operator_Tilde:
            return "~";
        default:
            return "<unknown>";
        }
    }

}
