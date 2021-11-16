package edu.kit.compiler.parser;

import java.util.Optional;

import edu.kit.compiler.JavaEasyCompiler.Result;
import edu.kit.compiler.data.CompilerException;
import edu.kit.compiler.data.Positionable;
import edu.kit.compiler.data.Token;

/**
 * Exception thrown by the Parser in case of a syntax error.
 */
public class ParseException extends CompilerException implements Positionable {
    private Token token;

    /**
     * Create a new ParseException.
     * 
     * @param token Token of parser-defined error position.
     */
    public ParseException(Token token) {
        this(token, "");
    }

    /**
     * Create a new ParseException.
     * 
     * @param token Token of parser-defined error position.
     * @param message Message describing the exception further.
     */
    public ParseException(Token token, String message) {
        super(String.format("unexpected token %s; %s%n", token.getType().name(), message));
        this.token = token;
    }

    /**
     * Get the token that caused the exception.
     */
    public Token getToken() {
        return token;
    }

    @Override
    public Optional<Positionable> getPosition() {
        return Optional.of(this);
    }

    @Override
    public Optional<String> getCompilerStage() {
        return Optional.of("parser");
    }

    @Override
    public Result getResult() {
        return Result.ParseError;
    }

    @Override
    public int getLine() {
        return token.getLine();
    }

    @Override
    public int getColumn() {
        return token.getColumn();
    }
}

