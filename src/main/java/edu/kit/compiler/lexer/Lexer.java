package edu.kit.compiler.lexer;

import edu.kit.compiler.data.Literal;
import edu.kit.compiler.data.Token;
import edu.kit.compiler.data.TokenType;
import edu.kit.compiler.io.BufferedLookaheadIterator;
import edu.kit.compiler.io.CharCounterLookaheadIterator;
import edu.kit.compiler.io.ReaderCharIterator;

import static edu.kit.compiler.data.TokenType.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Reads characters from an input stream and returns found tokens.
 */
public class Lexer {
    private static Map<String, TokenType> KEYWORDS = new HashMap<>(keyWordMap());

    private CharCounterLookaheadIterator charStream;
    private StringTable stringTable;

    public Lexer(ReaderCharIterator iterator) {
        this.charStream = new CharCounterLookaheadIterator(
            new BufferedLookaheadIterator<>(iterator)
        );
        this.stringTable = new StringTable();
    }

    public StringTable getStringTable() {
        return stringTable;
    }

    /**
     * Read characters from the input stream until a token is found. Any
     * comments or white spaces are skipped.
     * 
     * @return the next token found in input stream.
     * @throws LexException if no valid token could be read from the input stream.
     */
    public Token getNextToken() throws LexException {
        while (skipWhiteSpace() || skipComment()) { }

        if (Character.isEndOfStream(charStream.get(0))) {
            return new Token(EndOfStream, charStream.getLine(), charStream.getColumn());
        } else if (Character.isDigit(charStream.get())) {
            return lexIntegerLiteral();
        } else if (Character.isIdentifierStart(charStream.get())) {
            return lexKeywordOrIdentifier();
        } else {
            return lexOperatorOrDelimiter();
        }
    }

    /**
     * Reads an integer literal from the input stream and returns it as a token.
     * The caller must ensure that the next character in the input stream is an
     * ASCII Digit.
     * 
     * @return a Token containing an integer literal.
     * @throws LexException if a non zero literal has a leading zero, or the
     *                      literal would overflow a 32-bit signed integer.
     */
    private Token lexIntegerLiteral() throws LexException {
        assert Character.isDigit(charStream.get());

        int line = charStream.getLine();
        int column = charStream.getColumn();
        if (charStream.get() == '0') {
            charStream.next();
            return new Token(IntegerLiteral, line, column, Literal.ofValue(0));
        } else {
            var builder = new StringBuilder();
            while (Character.isDigit(charStream.get())) {
                builder.append((char)charStream.get().intValue());
                charStream.next();
            }
            
            var literal = new Literal(builder.toString());
            return new Token(IntegerLiteral, line, column, literal);
        }
    }

    /**
     * Reads an identifier or keyword from the input stream and returns it as
     * Token. The caller must ensure that the next character in the input
     * stream is valid as first character in an identifier (i.e. an ASCII
     * letter or underscore);
     * 
     * @return a Token containing a keyword or an identifier.
     */
    private Token lexKeywordOrIdentifier() {
        assert Character.isIdentifierStart(charStream.get());

        int line = charStream.getLine();
        int column = charStream.getColumn();
        var builder = new StringBuilder();
        while (Character.isIdentifierPart(charStream.get())) {
            builder.append((char)charStream.get().intValue());
            charStream.next();
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
     * Reads an operator or delimiter from the input stream and returns it as
     * a Token. 
     * 
     * @return a Token containing an operator or a delimiter.
     * @throws LexException if no valid operator or delimiter was found.
     * @throws IllegalStateException if the token is the start of a comment (i.e. /*).
     */
    private Token lexOperatorOrDelimiter() throws LexException {
        int line = charStream.getLine();
        int column = charStream.getColumn();
        TokenType tokenType = switch (charStream.get().intValue()) {
            case '.' -> { charStream.next(); yield Operator_Dot;          }
            case ',' -> { charStream.next(); yield Operator_Comma;        }
            case ':' -> { charStream.next(); yield Operator_Colon;        }
            case ';' -> { charStream.next(); yield Operator_Semicolon;    }
            case '?' -> { charStream.next(); yield Operator_Questionmark; }
            case '~' -> { charStream.next(); yield Operator_Tilde;        }
            case '(' -> { charStream.next(); yield Operator_ParenL;       }
            case ')' -> { charStream.next(); yield Operator_ParenR;       }
            case '[' -> { charStream.next(); yield Operator_BracketL;     }
            case ']' -> { charStream.next(); yield Operator_BracketR;     }
            case '{' -> { charStream.next(); yield Operator_BraceL;       }
            case '}' -> { charStream.next(); yield Operator_BraceR;       }

            case '+' -> switch (charStream.get(1).intValue()) {
                case '+' -> { charStream.next(2); yield Operator_PlusPlus;  }
                case '=' -> { charStream.next(2); yield Operator_PlusEqual; }
                default  -> { charStream.next(1); yield Operator_Plus;      }
            };
            case '-' -> switch (charStream.get(1).intValue()) {
                case '-' -> { charStream.next(2); yield Operator_MinusMinus; }
                case '=' -> { charStream.next(2); yield Operator_MinusEqual; }
                default  -> { charStream.next(1); yield Operator_Minus;      }
            };
            case '*' -> switch (charStream.get(1).intValue()) {
                case '=' -> { charStream.next(2); yield Operator_StarEqual; }
                default  -> { charStream.next(1); yield Operator_Star;      }
            };
            case '/' -> switch (charStream.get(1).intValue()) {
                case '=' -> { charStream.next(2); yield Operator_SlashEqual; }
                case '*' -> throw new IllegalStateException(
                    "Comments should have been skipped before a call to this method"
                );
                default  -> { charStream.next(1); yield Operator_Slash;      }
            };
            case '%' -> switch (charStream.get(1).intValue()) {
                case '=' -> { charStream.next(2); yield Operator_PercentEqual; }
                default  -> { charStream.next(1); yield Operator_Percent;      }
            };
            case '&' -> switch (charStream.get(1).intValue()) {
                case '&' -> { charStream.next(2); yield Operator_AndAnd;   }
                case '=' -> { charStream.next(2); yield Operator_AndEqual; }
                default  -> { charStream.next(1); yield Operator_And;      }
            };
            case '|' -> switch (charStream.get(1).intValue()) {
                case '|' -> { charStream.next(2); yield Operator_BarBar;   }
                case '=' -> { charStream.next(2); yield Operator_BarEqual; }
                default  -> { charStream.next(1); yield Operator_Bar;      }
            };
            case '^' -> switch (charStream.get(1).intValue()) {
                case '=' -> { charStream.next(2); yield Operator_CircumEqual; }
                default  -> { charStream.next(1); yield Operator_Circum;      }
            };
            case '!' -> switch (charStream.get(1).intValue()) {
                case '=' -> { charStream.next(2); yield Operator_NotEqual; }
                default  -> { charStream.next(1); yield Operator_Not;      }
            };
            case '=' -> switch (charStream.get(1).intValue()) {
                case '=' -> { charStream.next(2); yield Operator_EqualEqual; }
                default  -> { charStream.next(1); yield Operator_Equal;      }
            };
            case '<' -> switch (charStream.get(1).intValue()) {
                case '<' -> switch (charStream.get(2).intValue()) {
                    case '=' -> { charStream.next(3); yield Operator_SmallerSmallerEqual; }
                    default  -> { charStream.next(2); yield Operator_SmallerSmaller;      }
                };
                case '=' -> { charStream.next(2); yield Operator_SmallerEqual; }
                default  -> { charStream.next(1); yield Operator_Smaller;      }
            };
            case '>' -> switch (charStream.get(1).intValue()) {
                case '>' -> switch (charStream.get(2).intValue()) {
                    case '>' -> switch (charStream.get(3).intValue()) {
                        case '=' -> { charStream.next(4); yield Operator_GreaterGreaterGreaterEqual; }
                        default  -> { charStream.next(3); yield Operator_GreaterGreaterGreater; }
                    };
                    case '=' -> { charStream.next(3); yield Operator_GreaterGreaterEqual; }
                    default  -> { charStream.next(2); yield Operator_GreaterGreater; }
                };
                case '=' -> { charStream.next(2); yield Operator_GreaterEqual; }
                default  -> { charStream.next(1); yield Operator_Greater;      }
            };
            case '\u0000' -> throw new LexException(line, column,
                "unexpected character 'NUL'");
            default -> throw new LexException(line, column,
                "unexpected character '" + (char)charStream.get().intValue() + "'"
            );
        };
        
        return new Token(tokenType, line, column);
    }

    /**
     * Skips the next character in the input stream if it is a white space.
     * 
     * @return true if a white space character was skipped.
     */
    private boolean skipWhiteSpace() throws LexException {
        if (Character.isWhiteSpace(charStream.get())) {
            charStream.next();
            return true;
        } else {
            return false;
        }
    }

    /**
     * If the next characters in the input stream are the start of a comment,
     * skips the entirety of that comment, including the closing delimiter.
     * 
     * @return true if a comment was skipped, false otherwise.
     * @throws LexException if no closing delimiter was found for a comment.
     */
    private boolean skipComment() throws LexException {
        if (Character.isCommentStart(charStream.get(0), charStream.get(1))) {
            int line = charStream.getLine();
            int column = charStream.getColumn();
            charStream.next(2);

            while (!Character.isCommentEnd(charStream.get(0), charStream.get(1))) {
                if (Character.isCommentStart(charStream.get(0), charStream.get(1))) {
                    // todo proper format for warnings.
                    System.err.format(
                        "warning: lexer: %d,%d: found opening comment inside of a comment\n",
                        line, column);
                } else if (Character.isEndOfStream(charStream.get(0))) {
                    throw new LexException(line, column, "unclosed comment");
                }
                charStream.next();
            }
            charStream.next(2);
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
}
