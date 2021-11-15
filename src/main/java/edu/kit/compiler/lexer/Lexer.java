package edu.kit.compiler.lexer;

import edu.kit.compiler.data.Literal;
import edu.kit.compiler.data.Token;
import edu.kit.compiler.data.TokenType;
import edu.kit.compiler.io.SourceLocationReader;
import edu.kit.compiler.logger.Logger;
import static edu.kit.compiler.data.TokenType.*;

import java.io.Reader;
import java.util.Iterator;
import java.util.Map;

/**
 * Reads characters from a character stream and returns found tokens.
 */
public final class Lexer implements Iterator<Token> {
    private static final Map<String, TokenType> KEYWORDS = keyWordMap();

    private final SourceLocationReader reader;
    private final StringTable stringTable;
    private final Logger logger;

    public Lexer(Reader source) {
        this(source, Logger.nullLogger());
    }

    public Lexer(Reader source, Logger logger) {
        this.reader = new SourceLocationReader(source);
        this.stringTable = new StringTable();
        this.logger = logger.withName("lexer");
    }

    public StringTable getStringTable() {
        return stringTable;
    }

    /**
     * Read characters from the character stream until a token is found. Any
     * comments or white spaces are skipped.
     * 
     * @return the next token found in input stream.
     */
    public Token getNextToken() {
        while (skipWhiteSpace() || skipComment());

        if (Character.isEndOfStream(reader.peek())) {
            return new Token(EndOfStream, reader.getLine(), reader.getColumn());
        } else if (Character.isDigit(reader.peek())) {
            return lexIntegerLiteral();
        } else if (Character.isIdentifierStart(reader.peek())) {
            return lexKeywordOrIdentifier();
        } else {
            return lexOperatorOrDelimiter();
        }
    }

    /**
     * Reads an integer literal from the character stream and returns it as a token.
     * The caller must ensure that the next character in the character stream is an
     * ASCII Digit.
     * 
     * @return a Token containing an integer literal.
     */
    private Token lexIntegerLiteral() {
        assert Character.isDigit(reader.peek());

        int line = reader.getLine();
        int column = reader.getColumn();
        if (reader.peek() == '0') {
            reader.next();
            return new Token(IntegerLiteral, line, column, Literal.ofValue(0));
        } else {
            var builder = new StringBuilder();
            while (Character.isDigit(reader.peek())) {
                builder.append((char)reader.peek());
                reader.next();
            }
            
            var literal = new Literal(builder.toString());
            return new Token(IntegerLiteral, line, column, literal);
        }
    }

    /**
     * Reads an identifier or keyword from the character stream and returns
     * it as Token. The caller must ensure that the next character in the
     * input stream is valid as first character in an identifier (i.e. an
     * ASCII letter or underscore);
     * 
     * @return a Token containing a keyword or an identifier.
     */
    private Token lexKeywordOrIdentifier() {
        assert Character.isIdentifierStart(reader.peek());

        int line = reader.getLine();
        int column = reader.getColumn();
        var builder = new StringBuilder();
        while (Character.isIdentifierPart(reader.peek())) {
            builder.append((char)reader.peek());
            reader.next();
        }
        
        String identifier = builder.toString();
        TokenType keyword = KEYWORDS.get(identifier);
        if (keyword == null) {
            int index = stringTable.insert(identifier);
            return new Token(Identifier, line, column, index);
        } else {
            return new Token(keyword, line, column);
        }
    }

    /**
     * Reads an operator or delimiter from the characters stream and returns it
     * as a Token. 
     * 
     * @return a Token containing an operator or a delimiter.
     * @throws LexException if no valid operator or delimiter was found.
     * @throws IllegalStateException if the token is the start of a comment (i.e. /*).
     */
    private Token lexOperatorOrDelimiter() {
        int line = reader.getLine();
        int column = reader.getColumn();
        TokenType tokenType = switch (reader.peek()) {
            case '.' -> { reader.next(); yield Operator_Dot;          }
            case ',' -> { reader.next(); yield Operator_Comma;        }
            case ':' -> { reader.next(); yield Operator_Colon;        }
            case ';' -> { reader.next(); yield Operator_Semicolon;    }
            case '?' -> { reader.next(); yield Operator_Questionmark; }
            case '~' -> { reader.next(); yield Operator_Tilde;        }
            case '(' -> { reader.next(); yield Operator_ParenL;       }
            case ')' -> { reader.next(); yield Operator_ParenR;       }
            case '[' -> { reader.next(); yield Operator_BracketL;     }
            case ']' -> { reader.next(); yield Operator_BracketR;     }
            case '{' -> { reader.next(); yield Operator_BraceL;       }
            case '}' -> { reader.next(); yield Operator_BraceR;       }

            case '+' -> switch (reader.peekNext()) {
                case '+' -> { reader.next(); yield Operator_PlusPlus;  }
                case '=' -> { reader.next(); yield Operator_PlusEqual; }
                default  -> {                yield Operator_Plus;      }
            };
            case '-' -> switch (reader.peekNext()) {
                case '-' -> { reader.next(); yield Operator_MinusMinus; }
                case '=' -> { reader.next(); yield Operator_MinusEqual; }
                default  -> {                yield Operator_Minus;      }
            };
            case '*' -> switch (reader.peekNext()) {
                case '=' -> { reader.next(); yield Operator_StarEqual; }
                default  -> {                yield Operator_Star;      }
            };
            case '/' -> switch (reader.peekNext()) {
                case '=' -> { reader.next(); yield Operator_SlashEqual; }
                case '*' -> throw new IllegalStateException(
                    "Comments should have been skipped before a call to this method"
                );
                default  -> {                yield Operator_Slash;      }
            };
            case '%' -> switch (reader.peekNext()) {
                case '=' -> { reader.next(); yield Operator_PercentEqual; }
                default  -> {                yield Operator_Percent;      }
            };
            case '&' -> switch (reader.peekNext()) {
                case '&' -> { reader.next(); yield Operator_AndAnd;   }
                case '=' -> { reader.next(); yield Operator_AndEqual; }
                default  -> {                yield Operator_And;      }
            };
            case '|' -> switch (reader.peekNext()) {
                case '|' -> { reader.next(); yield Operator_BarBar;   }
                case '=' -> { reader.next(); yield Operator_BarEqual; }
                default  -> {                yield Operator_Bar;      }
            };
            case '^' -> switch (reader.peekNext()) {
                case '=' -> { reader.next(); yield Operator_CircumEqual; }
                default  -> {                yield Operator_Circum;      }
            };
            case '!' -> switch (reader.peekNext()) {
                case '=' -> { reader.next(); yield Operator_NotEqual; }
                default  -> {                yield Operator_Not;      }
            };
            case '=' -> switch (reader.peekNext()) {
                case '=' -> { reader.next(); yield Operator_EqualEqual; }
                default  -> {                yield Operator_Equal;      }
            };
            case '<' -> switch (reader.peekNext()) {
                case '<' -> switch (reader.peekNext()) {
                    case '=' -> { reader.next(); yield Operator_SmallerSmallerEqual; }
                    default  -> {                yield Operator_SmallerSmaller;      }
                };
                case '=' -> { reader.next(); yield Operator_SmallerEqual; }
                default  -> {                yield Operator_Smaller;      }
            };
            case '>' -> switch (reader.peekNext()) {
                case '>' -> switch (reader.peekNext()) {
                    case '>' -> switch (reader.peekNext()) {
                        case '=' -> { reader.next(); yield Operator_GreaterGreaterGreaterEqual; }
                        default  -> {                yield Operator_GreaterGreaterGreater; }
                    };
                    case '=' -> { reader.next(); yield Operator_GreaterGreaterEqual; }
                    default  -> {                yield Operator_GreaterGreater; }
                };
                case '=' -> { reader.next(); yield Operator_GreaterEqual; }
                default  -> {                yield Operator_Greater;      }
            };
            case '\u0000' -> throw new LexException(line, column,
                "unexpected character 'NUL'");
            default -> throw new LexException(line, column,
                "unexpected character '" + (char)reader.peek() + "'"
            );
        };
        
        return new Token(tokenType, line, column);
    }

    /**
     * Skips the next character in the character stream if it is a white space.
     * 
     * @return true if a white space character was skipped.
     */
    private boolean skipWhiteSpace() {
        if (Character.isWhiteSpace(reader.peek())) {
            reader.next();
            return true;
        } else {
            return false;
        }
    }

    /**
     * If the next characters in the character stream are the start of a comment,
     * skips the entirety of that comment, including the closing delimiter.
     * 
     * @return true if a comment was skipped, false otherwise.
     */
    private boolean skipComment() {
        if (reader.peek() == '/' && reader.previewNext() == '*') {
            int line = reader.getLine();
            int column = reader.getColumn();
            reader.next();
            reader.next();

            while (reader.peek() != '*' || reader.peekNext() != '/') {
                if (reader.peek() == '/' && reader.peekNext() == '*') {
                    logger.warn(line, column, "found opening comment inside of comment");
                } else if (Character.isEndOfStream(reader.peek())) {
                    throw new LexException(line, column, "unclosed comment");
                } else {
                    reader.next();
                }
            }
            reader.next();
            return true;
        } else {
            return false;
        }
    }

    /**
     * Returns a map containing ever identifier reserved as a keyword
     * in MiniJava, mapped to their corresponding TokenTypes.
     * 
     * @return a map of keywords and corresponding TokenTypes.
     */
    private static Map<String, TokenType> keyWordMap() {
        return Map.ofEntries(
            Map.entry("abstract", Keyword_Abstract),
            Map.entry("assert", Keyword_Assert),
            Map.entry("boolean", Keyword_Boolean),
            Map.entry("break", Keyword_Break),
            Map.entry("byte", Keyword_Byte),
            Map.entry("case", Keyword_Case),
            Map.entry("catch", Keyword_Catch),
            Map.entry("char", Keyword_Char),
            Map.entry("class", Keyword_Class),
            Map.entry("const", Keyword_Const),
            Map.entry("continue", Keyword_Continue),
            Map.entry("default", Keyword_Default),
            Map.entry("double", Keyword_Double),
            Map.entry("do", Keyword_Do),
            Map.entry("else", Keyword_Else),
            Map.entry("enum", Keyword_Enum),
            Map.entry("extends", Keyword_Extends),
            Map.entry("false", Keyword_False),
            Map.entry("finally", Keyword_Finally),
            Map.entry("final", Keyword_Final),
            Map.entry("float", Keyword_Float),
            Map.entry("for", Keyword_For),
            Map.entry("goto", Keyword_Goto),
            Map.entry("if", Keyword_If),
            Map.entry("implements", Keyword_Implements),
            Map.entry("import", Keyword_Import),
            Map.entry("instanceof", Keyword_Instanceof),
            Map.entry("interface", Keyword_Interface),
            Map.entry("int", Keyword_Int),
            Map.entry("long", Keyword_Long),
            Map.entry("native", Keyword_Native),
            Map.entry("new", Keyword_New),
            Map.entry("null", Keyword_Null),
            Map.entry("package", Keyword_Package),
            Map.entry("private", Keyword_Private),
            Map.entry("protected", Keyword_Protected),
            Map.entry("public", Keyword_Public),
            Map.entry("return", Keyword_Return),
            Map.entry("short", Keyword_Short),
            Map.entry("static", Keyword_Static),
            Map.entry("strictfp", Keyword_Strictfp),
            Map.entry("super", Keyword_Super),
            Map.entry("switch", Keyword_Switch),
            Map.entry("synchronized", Keyword_Synchronized),
            Map.entry("this", Keyword_This),
            Map.entry("throws", Keyword_Throws),
            Map.entry("throw", Keyword_Throw),
            Map.entry("transient", Keyword_Transient),
            Map.entry("true", Keyword_True),
            Map.entry("try", Keyword_Try),
            Map.entry("void", Keyword_Void),
            Map.entry("volatile", Keyword_Volatile),
            Map.entry("while", Keyword_While)
        );
    }

    @Override
    public boolean hasNext() {
        return true;
    }

    @Override
    public Token next() {
        return getNextToken();
    }
}
