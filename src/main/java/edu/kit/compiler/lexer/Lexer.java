package edu.kit.compiler.lexer;

import edu.kit.compiler.data.Token;
import edu.kit.compiler.data.TokenType;
import edu.kit.compiler.io.CharCounterLookaheadIterator;
import static edu.kit.compiler.data.TokenType.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads characters from an input stream and returns found tokens.
 */
public class Lexer {

    private static final Logger LOGGER = LoggerFactory.getLogger(Lexer.class);

    private CharCounterLookaheadIterator charStream;
    private StringTable stringTable;

    public Lexer(CharCounterLookaheadIterator iterator) {
        this.charStream = iterator;
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
     * @throws LexException if no valid token can be found.
     */
    public Token getNextToken() throws LexException {
        while (skipWhiteSpace() || skipComment()) { }

        if (isEndOfStream(charStream.get())) {
            return new Token(EndOfStream, charStream.getLine(), charStream.getColumn());
        } else if (isDigit(charStream.get())) {
            return lexIntegerLiteral();
        } else if (isIdentifierStart(charStream.get())) {
            return lexKeywordOrIdentifier();
        } else {
            return lexOperator();
        }
    }

    private Token lexIntegerLiteral() throws LexException {
        int line = charStream.getLine();
        int column = charStream.getColumn();
        if (charStream.get() == '0') {
            if (isDigit(charStream.get(1))) {
                throw new LexException(charStream.getLine(), charStream.getColumn(),
                    "non-zero integer literal with leading zero");
            } else {
                charStream.next();
                return new Token(IntegerLiteral, line, column, 0);
            }
        } else {
            var builder = new StringBuilder();
            while (isDigit(charStream.get())) {
                builder.append(charStream.get());
                charStream.next();
            }
            
            try {
                int intValue = Integer.parseInt(builder.toString());
                return new Token(IntegerLiteral, line, column, intValue);
            } catch (NumberFormatException e) {
                throw new LexException(line, column, "invalid integer literal");
            }
        }
    }

    private Token lexKeywordOrIdentifier() throws LexException {
        throw new RuntimeException();
    }

    private Token lexOperator() throws LexException {
        int line = charStream.getLine();
        int column = charStream.getColumn();
        TokenType tokenType = switch (charStream.get()) {
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


            case '+' -> switch (charStream.get(1)) {
                case '+' -> { charStream.next(2); yield Operator_PlusPlus;  }
                case '=' -> { charStream.next(2); yield Operator_PlusEqual; }
                default  -> { charStream.next(1); yield Operator_Plus;      }
            };
            case '-' -> switch (charStream.get(1)) {
                case '-' -> { charStream.next(2); yield Operator_MinusMinus; }
                case '=' -> { charStream.next(2); yield Operator_MinusEqual; }
                default  -> { charStream.next(1); yield Operator_Minus;      }
            };
            case '*' -> switch (charStream.get(1)) {
                case '=' -> { charStream.next(2); yield Operator_StarEqual; }
                default  -> { charStream.next(1); yield Operator_Star;      }
            };
            case '/' -> switch (charStream.get(1)) {
                case '=' -> { charStream.next(2); yield Operator_SlashEqual; }
                case '*' -> throw new IllegalStateException(
                    "Comments should have been skipped before a call to this method"
                );
                default  -> { charStream.next(1); yield Operator_Slash;      }
            };
            case '%' -> switch (charStream.get(1)) {
                case '=' -> { charStream.next(2); yield Operator_PercentEqual; }
                default  -> { charStream.next(1); yield Operator_Percent;      }
            };
            case '&' -> switch (charStream.get(1)) {
                case '&' -> { charStream.next(2); yield Operator_AndAnd;   }
                case '=' -> { charStream.next(2); yield Operator_AndEqual; }
                default  -> { charStream.next(1); yield Operator_And;      }
            };
            case '|' -> switch (charStream.get(1)) {
                case '|' -> { charStream.next(2); yield Operator_BarBar;   }
                case '=' -> { charStream.next(2); yield Operator_BarEqual; }
                default  -> { charStream.next(1); yield Operator_Bar;      }
            };
            case '^' -> switch (charStream.get(1)) {
                case '=' -> { charStream.next(2); yield Operator_CircumEqual; }
                default  -> { charStream.next(1); yield Operator_Circum;      }
            };
            case '!' -> switch (charStream.get(1)) {
                case '=' -> { charStream.next(2); yield Operator_NotEqual; }
                default  -> { charStream.next(1); yield Operator_Not;      }
            };
            case '=' -> switch (charStream.get(1)) {
                case '=' -> { charStream.next(2); yield Operator_EqualEqual; }
                default  -> { charStream.next(1); yield Operator_Equal;      }
            };
            case '<' -> switch (charStream.get(1)) {
                case '<' -> switch (charStream.get(2)) {
                    case '=' -> { charStream.next(3); yield Operator_SmallerSmallerEqual; }
                    default  -> { charStream.next(2); yield Operator_SmallerSmaller;      }
                };
                case '=' -> { charStream.next(2); yield Operator_SmallerEqual; }
                default  -> { charStream.next(1); yield Operator_Smaller;      }
            };
            case '>' -> switch (charStream.get(1)) {
                case '>' -> switch (charStream.get(2)) {
                    case '>' -> switch (charStream.get(3)) {
                        case '=' -> { charStream.next(4); yield Operator_GreaterGreaterGreaterEqual; }
                        default  -> { charStream.next(3); yield Operator_GreaterGreaterGreater; }
                    };
                    case '=' -> { charStream.next(3); yield Operator_GreaterGreaterEqual; }
                    default  -> { charStream.next(2); yield Operator_GreaterGreater; }
                };
                case '=' -> { charStream.next(2); yield Operator_GreaterEqual; }
                default  -> { charStream.next(1); yield Operator_Greater;      }
            };
            default -> throw new LexException(
                charStream.getLine(), charStream.getColumn(),
                "unexpected character '" + charStream.get() + "'"
            );
        };
        
        return new Token(tokenType, line, column);
    }

    private boolean skipWhiteSpace() {
        if (Character.isWhitespace(charStream.get())) {
            charStream.next();
            return true;
        } else {
            return false;
        }
    }

    private boolean skipComment() throws LexException {
        if (isCommentStart(charStream.get(0), charStream.get(1))) {
            int startLine = charStream.getLine();
            int startColumn = charStream.getColumn();
            charStream.next(2);

            while (!isCommentEnd(charStream.get(0), charStream.get(1))) {
                if (isCommentStart(charStream.get(0), charStream.get(1))) {
                    // todo proper format for warnings.
                    LOGGER.warn("found opening comment inside of a comment");
                } else if (isEndOfStream(charStream.get())) {
                    throw new LexException(startLine, startColumn,
                        "unclosed comment");
                }
                charStream.next();
            }
            charStream.next(2);
            return true;
       } else {
           return false;
       }
    }

    private static boolean isDigit(char c) {
        // The builtin 'isDigit' allows non ASCII digits,
        // which is not allowed in MiniJava.
        return '0' <= c && c <= '9';
    }

    private static boolean isIdentifierStart(char c) {
        return ('a' <= c && c <= 'z') || ('A' <= c && c <= 'Z') || (c == '_');
    }

    private static boolean isEndOfStream(char c) {
        return c == '\u0000';
    }

    private static boolean isCommentStart(char c1, char c2) {
        return c1 == '/' && c2 == '*';
    }

    private static boolean isCommentEnd(char c1, char c2) {
        return c1 == '*' && c2 == '/';
    }
}
